# Simjot – One-click Windows build

Below is a quick visual of the packaged app. For more screenshots, see the main `README.md`.

![Main Interface](Simjot/Simjot/docs/main_interface.png)

## Prerequisites
1. **JDK 17** or newer on the machine (`java -version` should print ≥ 17).  
   – Make sure the JDK's *bin* directory (where `javac` & `jpackage` live) is on your `PATH`.
2. Windows 10/11 – the batch file uses `xcopy` / standard cmd commands.

## Build & package
Open *PowerShell* or *Command Prompt* inside the project folder (the one that contains `package_simjournal.bat`) and run:

```cmd
package_simjournal.bat
```

The script will:
1. Compile all Java sources into *build/classes*.
2. Copy images into the class tree so they are embedded in the JAR.
3. Create a **modular** `Simjot.jar` with `main.JournalApp` as the entry point.
4. Copy the `audio/` folder (needed at runtime) next to the JAR.
5. Invoke `jpackage` to create `Simjot.exe` plus a trimmed runtime in *dist*.

On success you'll find:
```
dist/Simjot/Simjot.exe   <- runnable app image
```
Double-click it to launch.

> ⚠️  If `jpackage` complains it can't find a suitable icon you can ignore the flag or provide a `.ico` file via the script. 

## Run on macOS/Linux

- The one-click packaging script targets Windows. On macOS/Linux, run the app via the JAR as long as Java 17+ is installed:

  ```bash
  java -jar Simjot.jar
  ```

  You can produce `Simjot.jar` by running the Windows script on a Windows machine or by compiling sources in your IDE and creating an artifact with `main.JournalApp` as the entry point.

## Troubleshooting

- **`javac`/`jpackage` not found**: Ensure JDK 17+ `bin` is on your `PATH`.
- **Permission issues on Windows**: Run PowerShell/Command Prompt as Administrator.
- **Missing resources at runtime**: Verify `audio/` and embedded images were copied next to the JAR as the script describes.