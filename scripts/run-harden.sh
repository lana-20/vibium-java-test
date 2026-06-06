#!/usr/bin/env bash
# Usage: run-harden.sh [B1|B2|...|B10]
PATH="/usr/local/bin:$PATH" java -cp "out:vibium-26.5.31.jar:gson-2.11.0.jar" VibiumBugHardening "$@"
