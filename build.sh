#!/usr/bin/env bash
# Build PhantomHeads.jar
# Extracts compile-time deps from the local paper.jar and compiles with javac directly
# (avoids Maven compiler plugin crash on Java 25).
set -e

PAPER_JAR="/home/jan/minecraft/paper.jar"
LIBS="/tmp/ph_build_libs"
OUT="/tmp/ph_build_out"
JAR_OUT="$(dirname "$0")/PhantomHeads.jar"

mkdir -p "$LIBS" "$OUT"

echo "[PhantomHeads] Extracting deps from paper.jar..."
cd /tmp
jar xf "$PAPER_JAR" \
  "META-INF/libraries/io/papermc/paper/paper-api/26.1.2.build.53-stable/paper-api-26.1.2.build.53-stable.jar" \
  "META-INF/libraries/net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar" \
  "META-INF/libraries/net/kyori/adventure-text-minimessage/4.26.1/adventure-text-minimessage-4.26.1.jar" \
  "META-INF/libraries/net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar" \
  "META-INF/libraries/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar" \
  "META-INF/libraries/net/md_5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar" \
  "META-INF/libraries/net/kyori/adventure-text-serializer-gson/4.26.1/adventure-text-serializer-gson-4.26.1.jar" 2>/dev/null || true

find /tmp/META-INF -name "*.jar" -exec cp {} "$LIBS/" \; 2>/dev/null || true

# JetBrains annotations and Guava from Maven local cache
cp ~/.m2/repository/org/jetbrains/annotations/26.0.2-1/annotations-26.0.2-1.jar "$LIBS/" 2>/dev/null || \
cp ~/.m2/repository/org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar       "$LIBS/" 2>/dev/null || true
cp ~/.m2/repository/com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar        "$LIBS/" 2>/dev/null || true

CP=$(find "$LIBS" -name "*.jar" | tr '\n' ':')
SRC=$(find "$(dirname "$0")/src/main/java" -name "*.java" | tr '\n' ' ')

echo "[PhantomHeads] Compiling..."
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 21 -cp "$CP" -d "$OUT" $SRC

cp -r "$(dirname "$0")/src/main/resources/." "$OUT/"
jar cf "$JAR_OUT" -C "$OUT" .
echo "[PhantomHeads] Built: $JAR_OUT ($(du -h "$JAR_OUT" | cut -f1))"
