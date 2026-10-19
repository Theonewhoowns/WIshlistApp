@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
set "TASK_JAVA=java"
set "TASK_JAVAC=javac"
set "TASK_JAR=jar"
if defined JAVA_HOME set "TASK_JAVA=%JAVA_HOME%\bin\java.exe"
if defined JAVA_HOME set "TASK_JAVAC=%JAVA_HOME%\bin\javac.exe"
if defined JAVA_HOME set "TASK_JAR=%JAVA_HOME%\bin\jar.exe"
"%TASK_JAVA%" -version 2>&1 | findstr /c:"1.8." >nul
if errorlevel 1 goto badjava
if not exist target\classes mkdir target\classes
if not exist target\test-classes mkdir target\test-classes
if not exist target\lib mkdir target\lib
(for /r "src\main\java" %%f in (*.java) do @echo "%%f") > target\sources.list
"%TASK_JAVAC%" -J-Dfile.encoding=UTF-8 -encoding UTF-8 -source 1.8 -target 1.8 -d target\classes @target\sources.list
if errorlevel 1 goto failed
xcopy /e /i /y src\main\resources target\classes >nul
copy /y lib\*.jar target\lib\ >nul
>target\MANIFEST.MF echo Manifest-Version: 1.0
>>target\MANIFEST.MF echo Main-Class: wishlistapp.Launcher
>>target\MANIFEST.MF echo Class-Path: lib/postgresql-42.7.14.jar lib/mssql-jdbc-13.6.0.jre8.jar
>>target\MANIFEST.MF echo.
"%TASK_JAR%" cfm target\wishlist-app-2.0.0.jar target\MANIFEST.MF -C target\classes .
if errorlevel 1 goto failed
(for /r "src\test\java" %%f in (*.java) do @echo "%%f") > target\test-sources.list
"%TASK_JAVAC%" -J-Dfile.encoding=UTF-8 -encoding UTF-8 -source 1.8 -target 1.8 -cp target\classes -d target\test-classes @target\test-sources.list
if errorlevel 1 goto failed
echo Build complete: target\wishlist-app-2.0.0.jar
exit /b 0
:badjava
echo JDK 8 with JavaFX is required. Check JAVA_HOME.
:failed
pause
exit /b 1
