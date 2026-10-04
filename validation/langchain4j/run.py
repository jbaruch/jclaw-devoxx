#!/usr/bin/env python3
"""Reproduce the native Jev holdout check; preserve admitted evidence and never print credentials."""
import argparse
import os
from pathlib import Path
import shlex
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--report", type=Path, default=root / "build/jev-validation/langchain4j-native-current.json")
args = parser.parse_args()
environment = dict(os.environ)
dotenv = root / ".env"
if dotenv.exists():
    for line in dotenv.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:]
        key, separator, value = line.partition("=")
        if separator:
            parts = shlex.split(value, comments=True)
            if len(parts) == 1:
                environment.setdefault(key, parts[0])
if not environment.get("TYPESAFE_API_KEY"):
    parser.error("Set TYPESAFE_API_KEY in the environment or the ignored .env")
if sys.platform == "darwin":
    environment["JAVA_HOME"] = subprocess.check_output(
        ["/usr/libexec/java_home", "-F", "-v", "21"], text=True).strip()
report = args.report.resolve()
report.parent.mkdir(parents=True, exist_ok=True)
fixture = root / "validation/jev/results/holdout-01.json"
if report == fixture or report.parent == fixture.parent:
    parser.error("Write new results outside the admitted results directory")
command = [str(root / "gradlew"), "-p", str(root / "validation/langchain4j"),
           "run", "--console=plain", "--args=" + shlex.join([str(fixture), str(report)])]
raise SystemExit(subprocess.call(command, cwd=root, env=environment))
