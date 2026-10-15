#!/usr/bin/env python3
"""Build isolated policy variants from the current solver and run paired host simulations."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise RuntimeError(f"Solver changed; review candidate transformation: {old!r}")
    return source.replace(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--games", type=int, default=8)
    parser.add_argument("--moves", type=int, default=500)
    parser.add_argument("--workers", type=int, default=1)
    parser.add_argument("--seed-start", type=int, default=1)
    parser.add_argument("--spawn-probability", type=float, default=.90)
    parser.add_argument("--policies", default="greedy,expectimax2,current,adaptive4,expectimax4,minimax")
    parser.add_argument("--output", type=Path, required=True, help="Output path prefix, outside the repository")
    args = parser.parse_args()
    if args.games <= 0 or args.moves <= 0 or args.workers <= 0 or not 0 < args.spawn_probability < 1:
        parser.error("games, moves and workers must be positive; probability must be between 0 and 1")
    root = Path(__file__).resolve().parents[1]
    java_sources = root / "app/src/main/java/ua/borlock/autotap"
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    if not java or not javac:
        parser.error("Set JAVA_HOME to a JDK 17 or newer")
    original = (java_sources / "MergeMadnessSolver.java").read_text()
    # Candidates use the production Direction type, board rules, cache implementation,
    # evaluation weights and 120 ms budget. APK sources are never rewritten here.
    first = original.index("    enum Direction {")
    last = original.index("    static final class Move {", first)
    template = original[:first] + original[last:]
    template = replace_once(template, "import java.util.HashMap;",
                            "import java.util.HashMap;\nimport ua.borlock.autotap.MergeMadnessSolver.Direction;")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="merge-algorithms-") as temporary:
        directory = Path(temporary)
        generated = []
        for name, depth in [("BenchmarkGreedy", 1), ("BenchmarkDepth2", 2), ("BenchmarkAdaptive4", 4),
                            ("BenchmarkDepth4", 4), ("BenchmarkMinimax", 3)]:
            source = replace_once(template, "final class MergeMadnessSolver {", f"final class {name} {{")
            source = replace_once(source, "static final int MAX_SEARCH_DEPTH = 3;",
                                  f"static final int MAX_SEARCH_DEPTH = {depth};")
            if name == "BenchmarkAdaptive4":
                source = replace_once(source,
                    "for (int depth = 1; depth <= MAX_SEARCH_DEPTH; depth++) {",
                    "int depthLimit = board.emptyCount() <= 4 ? MAX_SEARCH_DEPTH : 3;\n"
                    "        for (int depth = 1; depth <= depthLimit; depth++) {")
            if name == "BenchmarkMinimax":
                if source.count("double total = 0.0;") != 2:
                    raise RuntimeError("Review minimax accumulation transformation")
                source = source.replace("double total = 0.0;", "double total = Double.POSITIVE_INFINITY;")
                source = replace_once(source,
                    "total += oneValue * context.rankOneProbability + twoValue * rankTwoProbability;",
                    "total = Math.min(total, Math.min(oneValue, twoValue));")
                source = replace_once(source, "double expected = total / emptyCount;", "double expected = total;")
                source = replace_once(source,
                    "total += evaluateLeaf(board.withTile(index, 1)) * rankOneProbability\n"
                    "                    + evaluateLeaf(board.withTile(index, 2)) * (1.0 - rankOneProbability);",
                    "total = Math.min(total, Math.min(evaluateLeaf(board.withTile(index, 1)),\n"
                    "                    evaluateLeaf(board.withTile(index, 2))));")
                source = replace_once(source, "return total / emptyCount;", "return total;")
            path = directory / f"{name}.java"
            path.write_text(source)
            generated.append(path)
        classes = directory / "classes"
        sources = [java_sources / (name + ".java") for name in ["MergeMadnessBoard",
                   "MergeMadnessSolver", "MergeMadnessSpawnModel", "MergeMadnessTransition"]]
        sources += generated + [root / "tools/MergeMadnessAlgorithmBenchmark.java"]
        subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-d", str(classes)]
                       + [str(path) for path in sources], check=True)
        subprocess.run([java, "-Xms256m", "-Xmx768m", "-XX:ActiveProcessorCount=2",
                        "-cp", str(classes), "ua.borlock.autotap.MergeMadnessAlgorithmBenchmark",
                        str(args.games), str(args.moves), str(args.workers), str(args.seed_start),
                        str(args.output), str(args.spawn_probability), args.policies], check=True)


if __name__ == "__main__":
    main()
