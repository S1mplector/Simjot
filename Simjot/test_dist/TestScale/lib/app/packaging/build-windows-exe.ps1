#Requires -Version 5.1
<#
.SYNOPSIS
    Simjot Windows Portable Executable Builder

.DESCRIPTION
    Creates a portable Windows executable with bundled JRE.
    No installation required - just extract and run.

.PARAMETER Clean
    Clean build directories before starting

.PARAMETER Msi
    Also create an MSI installer

.PARAMETER Zip
    Also create a portable ZIP archive

.EXAMPLE
    .\build-windows-exe.ps1
    Basic build - creates portable executable

.EXAMPLE
    .\build-windows-exe.ps1 -Clean -Zip -Msi
    Full build with all artifacts

.NOTES
    Requirements:
    - Java 17+ JDK (with jpackage)
    - Maven (mvn)
    - WiX Toolset 3.x (optional, for MSI)
#>

param(
    [switch]$Clean,
    [switch]$Msi,
    [switch]$Zip,
    [switch]$Help
)

# ============================================================================
# Configuration
# ============================================================================
$AppName = "Simjot"
$Vendor = "S1mplector"
$ErrorActionPreference = "Stop"

# Resolve paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = (Get-Item "$ScriptDir\..").FullName
$BuildDir = Join-Path $RootDir "build\windows-installer"
$DistDir = Join-Path $RootDir "dist"
$IconPath = Join-Path $RootDir "src\main\resources\img\icons\original\simjot.ico"

# ============================================================================
# Helper Functions
# ============================================================================
function Write-Info { param($Message) Write-Host "[INFO] $Message" -ForegroundColor Cyan }
function Write-Ok { param($Message) Write-Host "[OK] $Message" -ForegroundColor Green }
function Write-Warn { param($Message) Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Write-Err { param($Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

function Test-Command {
    param($Command)
    $null -ne (Get-Command $Command -ErrorAction SilentlyContinue)
}

# ============================================================================
# Show Help
# ============================================================================
if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Detailed
    exit 0
}

# ============================================================================
# Pre-flight Checks
# ============================================================================
Write-Host ""
Write-Info "Checking requirements..."

if (-not (Test-Command "java")) {
    Write-Err "Java not found in PATH"
    Write-Err "Please install Java 17+ JDK and add it to PATH"
    exit 1
}

if (-not (Test-Command "mvn")) {
    Write-Err "Maven not found in PATH"
    Write-Err "Please install Maven and add it to PATH"
    exit 1
}

if (-not (Test-Command "jpackage")) {
    Write-Err "jpackage not found in PATH"
    Write-Err "Please ensure you have Java 17+ JDK (not JRE) installed"
    exit 1
}

# Check Java version
$javaVersion = (java -version 2>&1 | Select-String "version").ToString()
if ($javaVersion -match '"(\d+)') {
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 17) {
        Write-Err "Java 17 or higher is required (found: $javaMajor)"
        exit 1
    }
    Write-Ok "Java $javaMajor detected"
}

# ============================================================================
# Setup
# ============================================================================
Set-Location $RootDir

if ($Clean) {
    Write-Info "Cleaning build directories..."
    if (Test-Path $BuildDir) { Remove-Item -Recurse -Force $BuildDir }
    Get-ChildItem "$DistDir\$AppName-*.exe" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem "$DistDir\$AppName-*.msi" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem "$DistDir\$AppName-*.zip" -ErrorAction SilentlyContinue | Remove-Item -Force
}

New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

# Get version from pom.xml
try {
    $Version = (mvn -q -DforceStdout help:evaluate -Dexpression=project.version 2>$null)
    if ([string]::IsNullOrEmpty($Version)) { $Version = "1.0.0" }
} catch {
    $Version = "1.0.0"
}
Write-Info "Building $AppName version $Version"

# ============================================================================
# Step 1: Build the Application JAR
# ============================================================================
Write-Host ""
Write-Info "Building application JAR..."
& mvn -DskipTests clean package -q
if ($LASTEXITCODE -ne 0) {
    Write-Err "Maven build failed"
    exit 1
}

$ShadedJar = "$AppName-$Version.jar"
$JarPath = Join-Path $RootDir "target\$ShadedJar"

if (-not (Test-Path $JarPath)) {
    Write-Err "JAR not found: $JarPath"
    exit 1
}
Write-Ok "JAR built: $ShadedJar"

# ============================================================================
# Step 2: Create Custom Runtime (jlink)
# ============================================================================
Write-Host ""
Write-Info "Creating optimized Java runtime..."
$RuntimeDir = Join-Path $BuildDir "runtime"

if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }

Write-Info "  Analyzing module dependencies..."
$Modules = (jdeps --multi-release 17 --ignore-missing-deps --print-module-deps $JarPath 2>$null | Select-Object -Last 1)
if ([string]::IsNullOrEmpty($Modules)) {
    $Modules = "java.base,java.desktop,java.logging,java.prefs,java.sql"
}
$Modules = "$Modules,jdk.unsupported,java.naming,java.management"

Write-Info "  Required modules: $Modules"
Write-Info "  Building runtime with jlink..."

& jlink `
    --add-modules $Modules `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress=2 `
    --output $RuntimeDir

if ($LASTEXITCODE -ne 0) {
    Write-Err "jlink failed"
    exit 1
}
Write-Ok "Custom runtime created"

# ============================================================================
# Step 3: Create Windows App Image with jpackage
# ============================================================================
Write-Host ""
Write-Info "Creating Windows application..."

$AppImageDir = Join-Path $BuildDir $AppName
if (Test-Path $AppImageDir) { Remove-Item -Recurse -Force $AppImageDir }

$JpkgArgs = @(
    "--type", "app-image"
    "--name", $AppName
    "--app-version", $Version
    "--vendor", $Vendor
    "--dest", $BuildDir
    "--input", (Join-Path $RootDir "target")
    "--main-jar", $ShadedJar
    "--runtime-image", $RuntimeDir
    "--java-options", "-Xmx1G"
    "--java-options", "--enable-preview"
)

# Add icon if exists
if (Test-Path $IconPath) {
    $JpkgArgs += "--icon", $IconPath
} elseif (Test-Path "$ScriptDir\app.ico") {
    $JpkgArgs += "--icon", "$ScriptDir\app.ico"
}

& jpackage @JpkgArgs

if ($LASTEXITCODE -ne 0) {
    Write-Err "jpackage app-image creation failed"
    exit 1
}
Write-Ok "Windows application created: $AppName"

# ============================================================================
# Step 4: Bundle Native Library (if available)
# ============================================================================
$NativeDir = Join-Path $RootDir "src\main\native"
$NativeLib = Join-Path $NativeDir "build\Release\simjot_native.dll"
$NativeLibAlt = Join-Path $NativeDir "build\simjot_native.dll"
$AppDir = Join-Path $BuildDir "$AppName\app"

if (Test-Path $NativeDir) {
    $foundNative = $null
    if (Test-Path $NativeLib) { $foundNative = $NativeLib }
    elseif (Test-Path $NativeLibAlt) { $foundNative = $NativeLibAlt }
    
    if ($foundNative) {
        Write-Info "Bundling native library..."
        New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
        Copy-Item $foundNative -Destination $AppDir -Force
        Write-Ok "Native library bundled: simjot_native.dll"
    } else {
        Write-Warn "Native library not found - skipping"
        Write-Warn "Build with: cd src\main\native && cmake -B build && cmake --build build --config Release"
    }
}

# ============================================================================
# Step 5: Create Portable ZIP (optional)
# ============================================================================
if ($Zip) {
    Write-Host ""
    Write-Info "Creating portable ZIP archive..."
    $ZipPath = Join-Path $DistDir "$AppName-$Version-windows-portable.zip"
    
    if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
    
    Compress-Archive -Path $AppImageDir -DestinationPath $ZipPath -Force
    Write-Ok "Portable ZIP created: $ZipPath"
}

# ============================================================================
# Step 6: Create MSI Installer (optional)
# ============================================================================
if ($Msi) {
    Write-Host ""
    Write-Info "Creating MSI installer..."
    
    & jpackage `
        --type msi `
        --name $AppName `
        --app-version $Version `
        --vendor $Vendor `
        --dest $DistDir `
        --app-image $AppImageDir `
        --win-menu `
        --win-shortcut `
        --win-dir-chooser
    
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "MSI creation failed - requires WiX Toolset 3.x"
        Write-Warn "Install from: https://wixtoolset.org/releases/"
    } else {
        Write-Ok "MSI installer created"
    }
}

# ============================================================================
# Step 7: Copy Portable Distribution to dist
# ============================================================================
Write-Host ""
Write-Info "Copying portable distribution to dist..."

$PortableDist = Join-Path $DistDir "$AppName-$Version-portable"
if (Test-Path $PortableDist) { Remove-Item -Recurse -Force $PortableDist }

Copy-Item -Path $AppImageDir -Destination $PortableDist -Recurse
Write-Ok "Portable distribution: $PortableDist"

# ============================================================================
# Summary
# ============================================================================
Write-Host ""
Write-Host "============================================================================" -ForegroundColor White
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "============================================================================" -ForegroundColor White
Write-Host ""
Write-Host "Portable App: $PortableDist" -ForegroundColor Cyan
Write-Host "  Run: $PortableDist\$AppName.exe" -ForegroundColor Gray

if ($Zip -and (Test-Path "$DistDir\$AppName-$Version-windows-portable.zip")) {
    Write-Host ""
    Write-Host "Portable ZIP: $DistDir\$AppName-$Version-windows-portable.zip" -ForegroundColor Cyan
}

if ($Msi -and (Test-Path "$DistDir\$AppName-$Version.msi")) {
    Write-Host ""
    Write-Host "MSI Installer: $DistDir\$AppName-$Version.msi" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "The portable distribution includes a bundled JRE and can be run" -ForegroundColor Gray
Write-Host "on any Windows machine without requiring Java to be installed." -ForegroundColor Gray
Write-Host ""
