#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .test-output/core
# The runtime may contain jdk.compiler without a javac launcher.
if command -v javac >/dev/null; then
  javac --release 17 -d .test-output/core android/core/src/main/java/dev/thinqclimate/core/Climate.java android/core/src/test/java/dev/thinqclimate/core/CoreTest.java
else
  java com.sun.tools.javac.Main -source 17 -target 17 -d .test-output/core android/core/src/main/java/dev/thinqclimate/core/Climate.java android/core/src/test/java/dev/thinqclimate/core/CoreTest.java
fi
java -cp .test-output/core dev.thinqclimate.core.CoreTest
