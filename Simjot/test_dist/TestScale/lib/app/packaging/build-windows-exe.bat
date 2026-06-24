@echo off
REM ============================================================================
REM Simjot Windows Portable Executable Builder
REM Creates a portable .exe with bundled JRE (no installation required)
REM
REM Usage: build-windows-exe.bat [options]
REM   --clean     Clean build directories before starting
REM   --msi       Also create an MSI installer
REM   --zip       Also create a portable ZIP archive
REM   --help      Show this help
REM
REM Requirements:
REM   - Java 17+ JDK (with jpackage)
REM   - Maven (mvn)
REM   - Visual Studio Build Tools (for native library, optional)
REM ============================================================================
setlocal enabledelayedexpansion

REM ============================================================================
REM Configuration
REM ============================================================================
set APP_NAME=Simjot
set VENDOR=S1mplector
set VERSION=

REM Resolve paths
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%..
pushd %ROOT_DIR%
set ROOT_DIR=%CD%
popd
set BUILD_DIR=%ROOT_DIR%\build\windows-installer
set DIST_DIR=%ROOT_DIR%\dist
set ICON_PATH=%ROOT_DIR%\src\main\resources\img\icons\original\simjot.ico

REM Options
set CLEAN_BUILD=0
set CREATE_MSI=0
set CREATE_ZIP=0

REM ============================================================================
REM Parse Arguments
REM ============================================================================
:parse_args
if "%~1"=="" goto :done_args
if /i "%~1"=="--clean" (
    set CLEAN_BUILD=1
    shift
    goto :parse_args
)
if /i "%~1"=="--msi" (
    set CREATE_MSI=1
    shift
    goto :parse_args
)
if /i "%~1"=="--zip" (
    set CREATE_ZIP=1
    shift
    goto :parse_args
)
if /i "%~1"=="--help" goto :show_help
if /i "%~1"=="-h" goto :show_help
echo [ERROR] Unknown option: %~1
exit /b 1

:show_help
echo.
echo Simjot Windows Portable Executable Builder
echo.
echo Usage: %~nx0 [options]
echo.
echo Options:
echo   --clean     Clean build directories before starting
echo   --msi       Also create an MSI installer
echo   --zip       Also create a portable ZIP archive
echo   --help      Show this help
echo.
echo Requirements:
echo   - Java 17+ JDK (with jpackage and jlink)
echo   - Maven (mvn) in PATH
echo   - Visual Studio Build Tools (optional, for native library)
echo.
exit /b 0

:done_args

REM ============================================================================
REM Pre-flight Checks
REM ============================================================================
echo.
echo [INFO] Checking requirements...

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found in PATH
    echo [ERROR] Please install Java 17+ JDK and add it to PATH
    exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found in PATH
    echo [ERROR] Please install Maven and add it to PATH
    exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
    echo [ERROR] jpackage not found in PATH
    echo [ERROR] Please ensure you have Java 17+ JDK (not JRE) installed
    exit /b 1
)

REM Check Java version
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER_RAW=%%i
set JAVA_VER=%JAVA_VER_RAW:"=%
for /f "tokens=1 delims=." %%a in ("%JAVA_VER%") do set JAVA_MAJOR=%%a
if %JAVA_MAJOR% LSS 17 (
    echo [ERROR] Java 17 or higher is required (found: %JAVA_MAJOR%)
    exit /b 1
)
echo [OK] Java %JAVA_MAJOR% detected

REM ============================================================================
REM Setup
REM ============================================================================
cd /d "%ROOT_DIR%"

if %CLEAN_BUILD%==1 (
    echo [INFO] Cleaning build directories...
    if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
    if exist "%DIST_DIR%\%APP_NAME%-*.exe" del /q "%DIST_DIR%\%APP_NAME%-*.exe"
    if exist "%DIST_DIR%\%APP_NAME%-*.msi" del /q "%DIST_DIR%\%APP_NAME%-*.msi"
    if exist "%DIST_DIR%\%APP_NAME%-*.zip" del /q "%DIST_DIR%\%APP_NAME%-*.zip"
)

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

REM Get version from pom.xml
for /f "tokens=*" %%i in ('mvn -q -DforceStdout help:evaluate -Dexpression^=project.version 2^>nul') do set VERSION=%%i
if "%VERSION%"=="" set VERSION=1.0.0
echo [INFO] Building %APP_NAME% version %VERSION%

REM ============================================================================
REM Step 1: Build the Application JAR
REM ============================================================================
echo.
echo [INFO] Building application JAR...
call mvn -DskipTests clean package -q
if errorlevel 1 (
    echo [ERROR] Maven build failed
    exit /b 1
)

set SHADED_JAR=%APP_NAME%-%VERSION%.jar
set JAR_PATH=%ROOT_DIR%\target\%SHADED_JAR%

if not exist "%JAR_PATH%" (
    echo [ERROR] JAR not found: %JAR_PATH%
    exit /b 1
)
echo [OK] JAR built: %SHADED_JAR%

REM ============================================================================
REM Step 2: Create Custom Runtime (jlink)
REM ============================================================================
echo.
echo [INFO] Creating optimized Java runtime...
set RUNTIME_DIR=%BUILD_DIR%\runtime

if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"

REM Analyze module dependencies
echo [INFO]   Analyzing module dependencies...
for /f "tokens=*" %%i in ('jdeps --multi-release 17 --ignore-missing-deps --print-module-deps "%JAR_PATH%" 2^>nul') do set MODULES=%%i

REM Add commonly needed modules for Swing apps
set MODULES=%MODULES%,jdk.unsupported,java.naming,java.management

echo [INFO]   Required modules: %MODULES%
echo [INFO]   Building runtime with jlink...

jlink ^
    --add-modules %MODULES% ^
    --strip-debug ^
    --no-header-files ^
    --no-man-pages ^
    --compress=2 ^
    --output "%RUNTIME_DIR%"

if errorlevel 1 (
    echo [ERROR] jlink failed
    exit /b 1
)
echo [OK] Custom runtime created

REM ============================================================================
REM Step 3: Create Windows App Image with jpackage
REM ============================================================================
echo.
echo [INFO] Creating Windows application...

REM Remove existing app image
if exist "%BUILD_DIR%\%APP_NAME%" rmdir /s /q "%BUILD_DIR%\%APP_NAME%"

REM Prepare jpackage command
set JPKG_CMD=jpackage ^
    --type app-image ^
    --name "%APP_NAME%" ^
    --app-version "%VERSION%" ^
    --vendor "%VENDOR%" ^
    --dest "%BUILD_DIR%" ^
    --input "%ROOT_DIR%\target" ^
    --main-jar "%SHADED_JAR%" ^
    --runtime-image "%RUNTIME_DIR%" ^
    --java-options "-Xmx1G" ^
    --java-options "--enable-preview" ^
    --win-console

REM Add icon if exists
if exist "%ICON_PATH%" (
    set JPKG_CMD=%JPKG_CMD% --icon "%ICON_PATH%"
) else (
    REM Try alternate icon locations
    if exist "%ROOT_DIR%\packaging\app.ico" (
        set JPKG_CMD=%JPKG_CMD% --icon "%ROOT_DIR%\packaging\app.ico"
    )
)

%JPKG_CMD%

if errorlevel 1 (
    echo [ERROR] jpackage app-image creation failed
    exit /b 1
)
echo [OK] Windows application created: %APP_NAME%

REM ============================================================================
REM Step 4: Bundle Native Library (if available)
REM ============================================================================
set NATIVE_DIR=%ROOT_DIR%\src\main\native
set NATIVE_LIB=%NATIVE_DIR%\build\Release\simjot_native.dll
set NATIVE_LIB_ALT=%NATIVE_DIR%\build\simjot_native.dll
set APP_DIR=%BUILD_DIR%\%APP_NAME%\app

if exist "%NATIVE_DIR%" (
    REM Check for pre-built native library
    if exist "%NATIVE_LIB%" (
        echo [INFO] Bundling native library...
        if not exist "%APP_DIR%" mkdir "%APP_DIR%"
        copy /y "%NATIVE_LIB%" "%APP_DIR%\" >nul
        echo [OK] Native library bundled: simjot_native.dll
    ) else if exist "%NATIVE_LIB_ALT%" (
        echo [INFO] Bundling native library...
        if not exist "%APP_DIR%" mkdir "%APP_DIR%"
        copy /y "%NATIVE_LIB_ALT%" "%APP_DIR%\" >nul
        echo [OK] Native library bundled: simjot_native.dll
    ) else (
        echo [WARN] Native library not found - skipping
        echo [WARN] Build native library with: cd src\main\native ^&^& cmake -B build ^&^& cmake --build build --config Release
    )
)

REM ============================================================================
REM Step 5: Create Portable ZIP (optional)
REM ============================================================================
if %CREATE_ZIP%==1 (
    echo.
    echo [INFO] Creating portable ZIP archive...
    set ZIP_PATH=%DIST_DIR%\%APP_NAME%-%VERSION%-windows-portable.zip
    
    where tar >nul 2>&1
    if errorlevel 1 (
        echo [WARN] tar not found - skipping ZIP creation
        echo [WARN] You can manually zip: %BUILD_DIR%\%APP_NAME%
    ) else (
        pushd "%BUILD_DIR%"
        tar -a -cf "%ZIP_PATH%" "%APP_NAME%"
        popd
        if exist "%ZIP_PATH%" (
            echo [OK] Portable ZIP created: %ZIP_PATH%
        )
    )
)

REM ============================================================================
REM Step 6: Create MSI Installer (optional)
REM ============================================================================
if %CREATE_MSI%==1 (
    echo.
    echo [INFO] Creating MSI installer...
    
    REM Check for WiX Toolset
    where candle >nul 2>&1
    if errorlevel 1 (
        echo [WARN] WiX Toolset not found - using jpackage for MSI
        
        set MSI_PATH=%DIST_DIR%\%APP_NAME%-%VERSION%.msi
        
        jpackage ^
            --type msi ^
            --name "%APP_NAME%" ^
            --app-version "%VERSION%" ^
            --vendor "%VENDOR%" ^
            --dest "%DIST_DIR%" ^
            --app-image "%BUILD_DIR%\%APP_NAME%" ^
            --win-menu ^
            --win-shortcut ^
            --win-dir-chooser
        
        if errorlevel 1 (
            echo [WARN] MSI creation failed - jpackage MSI requires WiX Toolset 3.x
            echo [WARN] Install from: https://wixtoolset.org/releases/
        ) else (
            echo [OK] MSI installer created
        )
    ) else (
        jpackage ^
            --type msi ^
            --name "%APP_NAME%" ^
            --app-version "%VERSION%" ^
            --vendor "%VENDOR%" ^
            --dest "%DIST_DIR%" ^
            --app-image "%BUILD_DIR%\%APP_NAME%" ^
            --win-menu ^
            --win-shortcut ^
            --win-dir-chooser
        
        if not errorlevel 1 (
            echo [OK] MSI installer created
        )
    )
)

REM ============================================================================
REM Step 7: Copy Portable EXE to dist
REM ============================================================================
echo.
echo [INFO] Copying portable executable to dist...

set EXE_SRC=%BUILD_DIR%\%APP_NAME%\%APP_NAME%.exe
set EXE_DST=%DIST_DIR%\%APP_NAME%-%VERSION%-portable

if exist "%EXE_SRC%" (
    REM Copy entire app directory as portable distribution
    if exist "%EXE_DST%" rmdir /s /q "%EXE_DST%"
    xcopy /e /i /q "%BUILD_DIR%\%APP_NAME%" "%EXE_DST%\" >nul
    echo [OK] Portable distribution: %EXE_DST%
)

REM ============================================================================
REM Summary
REM ============================================================================
echo.
echo ============================================================================
echo Build Complete!
echo ============================================================================
echo.
echo Portable App: %EXE_DST%
echo   Run: %EXE_DST%\%APP_NAME%.exe

if %CREATE_ZIP%==1 (
    if exist "%DIST_DIR%\%APP_NAME%-%VERSION%-windows-portable.zip" (
        echo.
        echo Portable ZIP: %DIST_DIR%\%APP_NAME%-%VERSION%-windows-portable.zip
    )
)

if %CREATE_MSI%==1 (
    if exist "%DIST_DIR%\%APP_NAME%-%VERSION%.msi" (
        echo.
        echo MSI Installer: %DIST_DIR%\%APP_NAME%-%VERSION%.msi
    )
)

echo.
echo The portable distribution includes a bundled JRE and can be run
echo on any Windows machine without requiring Java to be installed.
echo.

exit /b 0
