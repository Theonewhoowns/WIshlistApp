#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
task_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$task_java" -jar target/wishlist-app-2.0.0.jar "$@"
