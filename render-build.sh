#!/usr/bin/env bash
set -euo pipefail

JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11+9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz"

mkdir -p .jdk
curl -L "$JDK_URL" -o .jdk/jdk.tar.gz
tar -xzf .jdk/jdk.tar.gz --strip-components=1 -C .jdk

export JAVA_HOME="$PWD/.jdk"
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw -DskipTests=true clean package

