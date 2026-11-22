#!/usr/bin/env bash
# Compiles and tests the Minecraft-independent core (config, filters, dedupe/cooldowns).
# Needs only a JDK 21 and the Gson jar. This is NOT a substitute for ./gradlew build.
set -euo pipefail
cd "$(dirname "$0")/.."
GSON_JAR="${GSON_JAR:-}"
if [ -z "$GSON_JAR" ]; then
  for c in /usr/share/java/gson.jar; do [ -f "$c" ] && GSON_JAR="$c"; done
fi
if [ -z "$GSON_JAR" ]; then
  mkdir -p build/selftest-libs
  GSON_JAR=build/selftest-libs/gson-2.10.1.jar
  [ -f "$GSON_JAR" ] || curl -sSL -o "$GSON_JAR" https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
fi
OUT=build/selftest-classes
rm -rf "$OUT"; mkdir -p "$OUT"
SRC=src/main/java/io/github/spawnnotifyclient
javac --release 21 -Xlint:all -cp "$GSON_JAR" -d "$OUT" $SRC/core/*.java $SRC/config/*.java selftest/CoreSelfTest.java
java -cp "$OUT:$GSON_JAR" CoreSelfTest
