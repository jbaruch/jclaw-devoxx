#!/usr/bin/env python3
"""Exercise the real launcher with two overlapping runs and a replaced installDist."""
import json
import os
from pathlib import Path
import select
import shutil
import signal
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def executable(path, content):
    path.write_text(content)
    path.chmod(0o755)


def response(process):
    if not select.select([process.stdout], [], [], 20)[0]:
        raise AssertionError("Launcher did not produce a response")
    line = process.stdout.readline()
    if not line:
        raise AssertionError("Launcher exited early: " + process.stderr.read())
    return json.loads(line)


def main():
    with tempfile.TemporaryDirectory(prefix="jclaw launcher test ") as directory:
        repo = Path(directory)
        shutil.copy2(ROOT / "jclaw", repo / "jclaw")
        (repo / "tools").mkdir()
        shutil.copy2(ROOT / "tools/run-isolated.py", repo / "tools/run-isolated.py")
        installed = repo / "app/build/install/app"
        (installed / "bin").mkdir(parents=True)
        (installed / "lib").mkdir()
        (repo / "mocks/build/libs").mkdir(parents=True)
        executable(repo / "gradlew", """#!/usr/bin/env python3
from pathlib import Path
version = Path('version').read_text()
Path('app/build/install/app/lib/app.jar').write_text(version)
for name in ('calendar-mcp', 'organizer-mcp'):
    Path('mocks/build/libs/' + name + '.jar').write_text(version)
""")
        executable(installed / "bin/app-preview", """#!/usr/bin/env python3
import json, os, signal, sys
from pathlib import Path
app = Path(__file__).resolve().parents[1]
mocks = Path(os.environ['JCLAW_MOCKS_ROOT'])
def stopped(signum, _frame):
    print(json.dumps({'signal': signum, 'filesPresent':
                      (app / 'lib/app.jar').exists() and (mocks / 'calendar-mcp.jar').exists()}), flush=True)
    sys.exit(128 + signum)
for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
    signal.signal(signum, stopped)
print(json.dumps({'app': str(app), 'mocks': str(mocks), 'args': sys.argv[1:]}), flush=True)
exit_code = int(sys.stdin.readline())
print(json.dumps({'app': (app / 'lib/app.jar').read_text(),
                  'calendar': (mocks / 'calendar-mcp.jar').read_text(),
                  'organizer': (mocks / 'organizer-mcp.jar').read_text()}), flush=True)
sys.exit(exit_code)
""")
        environment = os.environ.copy()
        environment.pop("JCLAW_MOCKS_ROOT", None)
        processes = []
        try:
            (repo / "version").write_text("first build")
            first = subprocess.Popen([str(repo / "jclaw"), "preview", "pasted words"],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, env=environment)
            processes.append(first)
            first_start = response(first)

            # Starting again replaces the shared app and mocks while run one waits.
            (repo / "version").write_text("second build")
            second = subprocess.Popen([str(repo / "jclaw"), "preview"],
                                      stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE, text=True, env=environment)
            processes.append(second)
            second_start = response(second)
            assert first_start["app"] != second_start["app"]
            assert first_start["args"] == ["pasted words"]
            assert (installed / "lib/app.jar").read_text() == "second build"

            for process, start, version, code in (
                (first, first_start, "first build", 0),
                (second, second_start, "second build", 17),
            ):
                process.stdin.write(str(code) + "\n")
                process.stdin.flush()
                assert response(process) == dict(app=version, calendar=version, organizer=version)
                assert process.wait(timeout=20) == code
                assert not Path(start["app"]).exists(), "Run files were not cleaned up"
                assert not Path(start["mocks"]).exists(), "Mock files were not cleaned up"
            for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
                process = subprocess.Popen([str(repo / "jclaw"), "preview"],
                                           stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                           stderr=subprocess.PIPE, text=True, env=environment)
                processes.append(process)
                start = response(process)
                process.send_signal(signum)
                assert response(process) == dict(signal=signum, filesPresent=True)
                assert process.wait(timeout=20) == 128 + signum
                assert not Path(start["app"]).exists()
                assert not Path(start["mocks"]).exists()
            print("PASS: overlapping runs preserve files/arguments/exit codes; INT/TERM/HUP reach the child before cleanup")
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=20)


if __name__ == "__main__":
    main()
