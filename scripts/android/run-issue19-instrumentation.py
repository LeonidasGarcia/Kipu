import argparse
import os
import pathlib
import re
import subprocess
import sys
import threading

parser = argparse.ArgumentParser(description="Run issue #19 lab tests with the MIUI activity-launch workaround.")
parser.add_argument("classes", help="Comma-separated instrumentation classes or class#method names")
parser.add_argument("prefix", nargs="?", default="after", choices=("before", "after"))
parser.add_argument("dark", nargs="?", default="false", choices=("false", "true"))
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="ADB device serial; optional with one device")
args = parser.parse_args()
classes, prefix, dark = args.classes, args.prefix, args.dark
adb = ["adb"] + (["-s", args.serial] if args.serial else [])
command = adb + ["shell", "am", "instrument", "-w", "-r",
           "-e", "class", classes, "-e", "evidencePrefix", prefix,
           "-e", "evidenceDark", dark,
           "com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner"]
log_dir = pathlib.Path(__file__).resolve().parents[2] / "scratch" / "issue-19"
log_dir.mkdir(parents=True, exist_ok=True)
log = log_dir / f"instrumentation-{prefix}-{dark}.log"
failed = False
finished = False
pending_launch = threading.Event()

def launch_waiting_activity(done):
    if done.wait(3):
        return
    focus = subprocess.run(adb + ["shell", "dumpsys", "window"],
        capture_output=True, text=True).stdout
    if not done.is_set() and not re.search(r"mCurrentFocus=.*com\.kipu\.app\.lab", focus):
        subprocess.run(adb + ["shell", "am", "start", "-W",
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
            "-f", "0x10008000", "-n", "com.kipu.app.lab/androidx.activity.ComponentActivity"],
            capture_output=True, text=True)
        print("Opened waiting test activity", flush=True)

with log.open("w", encoding="utf-8") as output:
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace")
    for line in process.stdout:
        output.write(line)
        output.flush()
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            print(line.strip(), flush=True)
        if line.strip() == "INSTRUMENTATION_STATUS_CODE: 1":
            # This MIUI device blocks ActivityScenario's background launch. Start the same
            # exported test activity with the exact intent expected by ActivityScenario.
            pending_launch.set()
            pending_launch = threading.Event()
            threading.Thread(target=launch_waiting_activity, args=(pending_launch,), daemon=True).start()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE:"):
            pending_launch.set()
        if line.strip() in ("INSTRUMENTATION_STATUS_CODE: -2", "INSTRUMENTATION_STATUS_CODE: -1"):
            failed = True
        if line.strip() == "INSTRUMENTATION_CODE: -1":
            finished = True
        if line.startswith("INSTRUMENTATION_STATUS_CODE:") or line.startswith("OK (") or line.startswith("FAILURES"):
            print(line.strip(), flush=True)
    process.wait()
    pending_launch.set()
print(f"Log: {log}", flush=True)
sys.exit(0 if process.returncode == 0 and finished and not failed else 1)
