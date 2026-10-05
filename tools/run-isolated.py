#!/usr/bin/env python3
"""Run private app/mock copies; forward signals and clean up after the JVM exits."""
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile


def main():
    repo = Path(__file__).resolve().parents[1]
    entry_point, *arguments = sys.argv[1:]
    child = None
    pending_signal = None

    def forward(signum, _frame):
        nonlocal pending_signal
        pending_signal = signum
        if child is not None:
            try:
                child.send_signal(signum)
            except ProcessLookupError:
                pass

    # Caught signals reset to normal handling when the child execs. Unlike an
    # asynchronous shell job, this keeps its SIGINT behavior and inherited stdin.
    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(signum, forward)

    run_root = repo / "build/runtime"
    run_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="run.", dir=run_root) as directory:
        run = Path(directory)
        shutil.copytree(repo / "app/build/install/app", run / "app")
        (run / "mocks").mkdir()
        for name in ("calendar-mcp.jar", "organizer-mcp.jar"):
            shutil.copy2(repo / "mocks/build/libs" / name, run / "mocks" / name)
        if pending_signal is not None:
            return 128 + pending_signal
        environment = os.environ.copy()
        environment["JCLAW_MOCKS_ROOT"] = str(run / "mocks")
        child = subprocess.Popen([str(run / "app/bin" / entry_point), *arguments],
                                 cwd=repo, env=environment)
        if pending_signal is not None:
            forward(pending_signal, None)
        code = child.wait()
        return code if code >= 0 else 128 - code


if __name__ == "__main__":
    sys.exit(main())
