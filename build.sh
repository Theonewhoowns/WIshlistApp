#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
task_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
task_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
task_jar="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
if ! "$task_java" -version 2>&1 | grep -q '"1.8.'; then
  echo 'Нужен JDK 8 с JavaFX 8. Проверьте JAVA_HOME.' >&2
  exit 1
fi
mkdir -p target/classes target/test-classes target/lib
find src/main/java -name '*.java' > target/sources.list
"$task_javac" -encoding UTF-8 -source 1.8 -target 1.8 -d target/classes @target/sources.list
cp -R src/main/resources/. target/classes/
cp lib/*.jar target/lib/
printf 'Manifest-Version: 1.0\nMain-Class: wishlistapp.Launcher\nClass-Path: lib/postgresql-42.7.14.jar lib/mssql-jdbc-13.6.0.jre8.jar\n\n' > target/MANIFEST.MF
"$task_jar" cfm target/wishlist-app-2.0.0.jar target/MANIFEST.MF -C target/classes .
find src/test/java -name '*.java' > target/test-sources.list
"$task_javac" -encoding UTF-8 -source 1.8 -target 1.8 -cp target/classes -d target/test-classes @target/test-sources.list
echo 'Готово: target/wishlist-app-2.0.0.jar'
