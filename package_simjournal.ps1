# Simjot Windows Package Builder (PowerShell version)
Write-Host "========================================" -ForegroundColor Green
Write-Host "Simjot Windows Package Builder" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# Check if JDK 17+ is available
try {
    $javaVersion = & java -version 2>&1 | Select-String "version" | ForEach-Object { $_.Line -replace '.*version "([^"]+)".*', '$1' }
    Write-Host "Found Java version: $javaVersion" -ForegroundColor Cyan
} catch {
    Write-Host "ERROR: Java JDK 17+ not found. Please ensure JDK bin directory is on PATH." -ForegroundColor Red
    Write-Host "Current PATH: $env:PATH" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Clean previous build
Write-Host "`nCleaning previous build..." -ForegroundColor Yellow
if (Test-Path "build/classes") {
    Remove-Item -Recurse -Force "build/classes"
}
if (Test-Path "dist") {
    Remove-Item -Recurse -Force "dist"
}

# Create build directories
Write-Host "Creating build directories..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "build/classes" | Out-Null

# Compile Java sources
Write-Host "Compiling Java sources..." -ForegroundColor Yellow
try {
    & javac -d build/classes --module-path build/classes -cp "Simjot/src/main/resources" -Xlint:all -Xlint:-serial -Xlint:-processing --module-source-path Simjot/src/main/java -m Simjot
    if ($LASTEXITCODE -ne 0) {
        throw "Compilation failed"
    }
    Write-Host "Compilation successful!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Compilation failed!" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Copy resources (images, etc.) into the class tree
Write-Host "Copying resources..." -ForegroundColor Yellow
Copy-Item -Recurse -Force "Simjot/src/main/resources/*" "build/classes/" 2>$null

# Create modular JAR
Write-Host "Creating modular JAR..." -ForegroundColor Yellow
try {
    & jar --create --file Simjot.jar --main-class main.ui.app.JournalApp -C build/classes .
    if ($LASTEXITCODE -ne 0) {
        throw "JAR creation failed"
    }
    Write-Host "JAR creation successful!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: JAR creation failed!" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Copy audio folder next to JAR (needed at runtime)
Write-Host "Copying audio resources..." -ForegroundColor Yellow
if (Test-Path "Simjot/audio") {
    Copy-Item -Recurse -Force "Simjot/audio" "audio"
    Write-Host "Audio resources copied!" -ForegroundColor Green
} else {
    Write-Host "WARNING: audio folder not found in Simjot directory" -ForegroundColor Yellow
}

# Use jpackage to create native Windows executable
Write-Host "Creating native executable with jpackage..." -ForegroundColor Yellow
try {
    & jpackage --type exe --name Simjot --app-version 1.0.0 --vendor "S1mplector" --destination dist --input . --main-jar Simjot.jar --main-class main.ui.app.JournalApp --java-options "-XX:+ShowCodeDetailsInExceptionMessages" --resource-dir Simjot/src/main/packaging --win-menu --win-shortcut --win-menu-group "Simjot"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed"
    }
    Write-Host "Native executable creation successful!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: jpackage failed!" -ForegroundColor Red
    Write-Host "If jpackage complains about missing icon, you can ignore it or provide an .ico file" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "Build completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nYour executable is located at: dist\Simjot\Simjot.exe" -ForegroundColor Cyan
Write-Host "`nYou can now:" -ForegroundColor White
Write-Host "1. Double-click Simjot.exe to run the application" -ForegroundColor White
Write-Host "2. Distribute the entire 'dist' folder as a standalone application" -ForegroundColor White

Read-Host "`nPress Enter to exit"
