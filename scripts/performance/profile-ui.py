"""Read-only navigation/scroll smoke profiling on Samsung SM-A165M, font scale 0.9."""
import argparse, json, os, re, subprocess, time
from pathlib import Path

adb = str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('label')
parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
args = parser.parse_args()
if not args.serial:
    parser.error('Select the target device with --serial or ANDROID_SERIAL.')
def run(*args):
    return subprocess.run([adb, '-s', device_serial, *args], check=True, capture_output=True, text=True, encoding='utf-8').stdout
device_serial = args.serial
def tap(x, y):
    run('shell', 'input', 'tap', str(x), str(y))
    time.sleep(0.8)
def stats():
    raw = run('shell', 'dumpsys', 'gfxinfo', 'com.kipu.app')
    result = {}
    for key, pattern in {
        'frames': r'Total frames rendered: (\d+)', 'janky': r'Janky frames: (\d+)',
        'p50_ms': r'50th percentile: (\d+)ms', 'p90_ms': r'90th percentile: (\d+)ms',
        'p95_ms': r'95th percentile: (\d+)ms', 'slow_ui': r'Number Slow UI thread: (\d+)',
    }.items():
        match = re.search(pattern, raw)
        result[key] = int(match[1]) if match else None
    return result

results = []
for repeat in range(3):
    run('shell', 'am', 'force-stop', 'com.kipu.app')
    run('shell', 'am', 'start', '-n', 'com.kipu.app/.MainActivity')
    time.sleep(4)
    tap(650, 2110); tap(100, 2110)
    run('shell', 'dumpsys', 'gfxinfo', 'com.kipu.app', 'reset')
    for _ in range(6):
        tap(650, 2110); tap(100, 2110)
    results.append({'scenario': 'navigation', 'repeat': repeat, **stats()})
    run('shell', 'dumpsys', 'gfxinfo', 'com.kipu.app', 'reset')
    for _ in range(4):
        tap(950, 2110)
        run('shell', 'input', 'keyevent', '4')
        time.sleep(0.8)
    results.append({'scenario': 'quick_sheet', 'repeat': repeat, **stats()})
    run('shell', 'dumpsys', 'gfxinfo', 'com.kipu.app', 'reset')
    for _ in range(4):
        run('shell', 'input', 'swipe', '500', '1750', '500', '600', '450')
        run('shell', 'input', 'swipe', '500', '600', '500', '1750', '450')
    time.sleep(1)
    results.append({'scenario': 'dashboard_scroll', 'repeat': repeat, **stats()})
    print(json.dumps(results[-3:]), flush=True)
output = Path('docs/ux-ui/performance') / f'performance-{args.label}.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(results, indent=2), encoding='utf-8')
