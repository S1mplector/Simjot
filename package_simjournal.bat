@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Simjot Windows Package Builder
echo ========================================

REM Check if JDK 17+ is available
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java JDK 17+ not found. Please ensure JDK bin directory is on PATH.
    echo Current PATH: %PATH%
    pause
    exit /b 1
)

echo Java is available.

REM Clean previous build
echo.
echo Cleaning previous build...
if exist build\classes rmdir /s /q build\classes 2>nul
if exist dist rmdir /s /q dist 2>nul

REM Create build directories
echo.
echo Creating build directories...
if not exist build\classes mkdir build\classes

REM Compile Java sources
echo.
echo Compiling Java sources...
javac -d build/classes --module-path build/classes -cp "Simjot/src/main/resources" -Xlint:all -Xlint:-serial -Xlint:-processing --module-source-path Simjot/src/main/java -m Simjot

if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

REM Copy resources (images, etc.) into the class tree
echo.
echo Copying resources...
xcopy /E /I /Y "Simjot\src\main\resources\*" "build\classes\" 2>nul

REM Create modular JAR
echo.
echo Creating modular JAR...
jar --create --file Simjot.jar --main-class main.ui.app.JournalApp -C build/classes . 2>nul

if errorlevel 1 (
    echo ERROR: JAR creation failed!
    pause
    exit /b 1
)

REM Copy audio folder next to JAR (needed at runtime)
echo.
echo Copying audio resources...
if exist "Simjot\audio" (
    xcopy /E /I /Y "Simjot\audio" "audio\" 2>nul
) else (
    echo WARNING: audio folder not found in Simjot directory
)

REM Use jpackage to create native Windows executable
echo.
echo Creating native executable with jpackage...
jpackage --type exe --name Simjot --app-version 1.0.0 --vendor "S1mplector" --destination dist --input . --main-jar Simjot.jar --main-class main.ui.app.JournalApp --java-options "-XX:+ShowCodeDetailsInExceptionMessages" --resource-dir Simjot/src/main/packaging --win-menu --win-shortcut --win-menu-group "Simjot"

if errorlevel 1 (
    echo ERROR: jpackage failed!
    echo If jpackage complains about missing icon, you can ignore it or provide an .ico file
    pause
    exit /b 1
)

echo.
echo ========================================
echo Build completed successfully!
echo ========================================
echo.
echo Your executable is located at: dist\Simjot\Simjot.exe
echo.
echo You can now:
echo 1. Double-click Simjot.exe to run the application
echo 2. Distribute the entire 'dist' folder as a standalone application
echo.
pause
