#!/usr/bin/env bash
javac -cp ".:vibium-26.5.31.jar:gson-2.11.0.jar" -d out \
  src/VibiumJavaApiTests.java \
  src/VibiumBugHardening.java \
  src/B3Repro.java
