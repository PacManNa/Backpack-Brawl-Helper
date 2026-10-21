#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
check_dir="$(mktemp -d)"
trap 'rm -rf -- "$check_dir"' EXIT
git show "${BLITZ_BASELINE_REF:-e6e393c}:app/src/main/java/ua/borlock/autotap/GameSolver.java" \
    | sed 's/\bGameSolver\b/GameSolver1940/g' > "$check_dir/GameSolver1940.java"
"$javac_bin" -encoding UTF-8 -d "$check_dir" \
    app/src/main/java/ua/borlock/autotap/GameBoard.java \
    app/src/main/java/ua/borlock/autotap/GameSolver.java \
    "$check_dir/GameSolver1940.java" tools/BlitzSolverBenchmark.java
"$java_bin" -Xms128m -Xmx512m -cp "$check_dir" \
    ua.borlock.autotap.BlitzSolverBenchmark "$@"
