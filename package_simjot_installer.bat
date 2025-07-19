@echo off
REM ================================================================
REM  build & package Simjot into a Windows MSI installer
REM  Requirements:
REM    • JDK 17 (or newer) installed and on PATH – jpackage must be present.
REM    • WiX Toolset 3.11+ installed and on PATH (for MSI creation)
REM ================================================================

setlocal EnableDelayedExpansion

REM -------- configurable metadata --------------------------------
set APP_NAME=Simjot
set APP_VERSION=1.0.0
set MAIN_MODULE=Simjot
set MAIN_CLASS=main.ui.JournalApp
set VENDOR=Simjot Team
set DESCRIPTION=A modern journaling application with drawing and mood tracking features
set COPYRIGHT=Copyright (C) 2025 Simjot Team

REM -------- derived paths ----------------------------------------
set SRC_DIR=Simjot\src
set BUILD_DIR=build
set CLASS_DIR=%BUILD_DIR%\classes
set INPUT_DIR=%BUILD_DIR%\input
set JAR_NAME=%APP_NAME%.jar
set DIST_DIR=dist

REM -------- clean previous build ---------------------------------
if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
if exist %DIST_DIR% rmdir /s /q %DIST_DIR%
del /q %JAR_NAME% 2>NUL
del /q *.msi 2>NUL

REM -------- compile sources --------------------------------------
echo.
echo === Compiling Java sources ===
mkdir %CLASS_DIR% 2>NUL

REM Collect list of .java files (excluding module-info) into a temp file
dir /s /b "%SRC_DIR%\*.java" | findstr /v /i "module-info.java" > "%BUILD_DIR%\sources.txt"

javac -d "%CLASS_DIR%" @"%BUILD_DIR%\sources.txt"

if errorlevel 1 (
    echo *** Compilation failed – see messages above ***
    pause
    exit /b 1
)

REM -------- copy resources into class tree so they end up inside JAR
mkdir %CLASS_DIR%\img 2>NUL
mkdir %CLASS_DIR%\audio 2>NUL
mkdir %CLASS_DIR%\mindmaps 2>NUL
xcopy /E /I /Y "Simjot\img" "%CLASS_DIR%\img" >NUL
xcopy /E /I /Y "Simjot\audio" "%CLASS_DIR%\audio" >NUL
xcopy /E /I /Y "Simjot\mindmaps" "%CLASS_DIR%\mindmaps" >NUL

REM -------- create modular JAR -----------------------------------
echo.
echo === Creating modular JAR ===
jar --create --file %JAR_NAME% --main-class %MAIN_CLASS% -C %CLASS_DIR% .
if errorlevel 1 (
    echo JAR creation failed.
    pause
    exit /b 1
)

REM -------- prepare jpackage input dir ---------------------------
mkdir %INPUT_DIR% 2>NUL
copy %JAR_NAME% %INPUT_DIR% >NUL
xcopy /E /I /Y "Simjot\audio" "%INPUT_DIR%\audio" >NUL

REM -------- check for WiX toolset --------------------------------
echo.
echo === Checking for WiX Toolset ===
where candle >NUL 2>NUL
if errorlevel 1 (
    echo WARNING: WiX Toolset not found in PATH. 
    echo Please install WiX Toolset 3.11+ from https://wixtoolset.org/
    echo Or use the regular package_simjournal.bat for app-image instead.
    pause
    exit /b 1
)

REM -------- run jpackage for MSI installer -----------------------
echo.
echo === Creating MSI installer (this may take several minutes) ===
jpackage --type msi ^
         --input %INPUT_DIR% ^
         --dest %DIST_DIR% ^
         --name %APP_NAME% ^
         --main-jar %JAR_NAME% ^
         --main-class %MAIN_CLASS% ^
         --app-version %APP_VERSION% ^
         --icon Simjot\img\logo.ico ^
         --description "%DESCRIPTION%" ^
         --vendor "%VENDOR%" ^
         --copyright "%COPYRIGHT%" ^
         --win-dir-chooser ^
         --win-menu ^
         --win-shortcut

if errorlevel 1 (
    echo MSI creation failed.
    pause
    exit /b 1
)

ENDLOCAL

echo.
echo ==================================================================
echo  MSI installer created successfully!
echo  Look for %APP_NAME%-%APP_VERSION%.msi in the %DIST_DIR% folder.
echo  You can now distribute this installer to users.
echo ==================================================================
pause