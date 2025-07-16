@echo off
REM ================================================================
REM  build & package Simjournal into a Windows executable launcher
REM  Requirements:
REM    • JDK 17 (or newer) installed and on PATH – jpackage must be present.
REM ================================================================

setlocal EnableDelayedExpansion

REM -------- configurable metadata --------------------------------
set APP_NAME=Simjournal
set APP_VERSION=1.0.0
set MAIN_MODULE=Simjournal
set MAIN_CLASS=main.ui.JournalApp

REM -------- derived paths ----------------------------------------
set SRC_DIR=Simjournal\src
set BUILD_DIR=build
set CLASS_DIR=%BUILD_DIR%\classes
set INPUT_DIR=%BUILD_DIR%\input
set JAR_NAME=%APP_NAME%.jar
set DIST_DIR=dist

REM -------- clean previous build ---------------------------------
if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
if exist %DIST_DIR% rmdir /s /q %DIST_DIR%
del /q %JAR_NAME% 2>NUL

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
xcopy /E /I /Y "Simjournal\img" "%CLASS_DIR%\img" >NUL
xcopy /E /I /Y "Simjournal\audio" "%CLASS_DIR%\audio" >NUL
xcopy /E /I /Y "Simjournal\mindmaps" "%CLASS_DIR%\mindmaps" >NUL

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
xcopy /E /I /Y "Simjournal\audio" "%INPUT_DIR%\audio" >NUL

REM -------- run jpackage ----------------------------------------
echo.
echo === Running jpackage (this may take a minute) ===
REM Use app-image to avoid WiX dependency (produces folder with Simjournal.exe)
jpackage --type app-image ^
         --input %INPUT_DIR% ^
         --dest %DIST_DIR% ^
         --name %APP_NAME% ^
         --main-jar %JAR_NAME% ^
         --main-class %MAIN_CLASS% ^
         --app-version %APP_VERSION%

if errorlevel 1 (
    echo jpackage failed.
    pause
    exit /b 1
)

ENDLOCAL

echo.
echo ==================================================================
echo  Build finished!  Check the %DIST_DIR% folder for %APP_NAME%.exe.
echo ==================================================================
pause 