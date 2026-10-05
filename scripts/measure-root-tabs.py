"""Read-only ADB tab benchmark. Start Kipu on its root dock before running.

python scripts/measure-root-tabs.py --serial DEVICE_SERIAL --label after --switches 20
Captures are separate from gfxinfo measurement; sleep 50ms is an ADB request,
not a guarantee that the captured display frame is exactly 50ms after touch.
"""
import argparse
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--label', default='after')
parser.add_argument('--switches', type=int, default=20)
parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
args = parser.parse_args()
if not args.serial:
    parser.error('Select the target device with --serial or ANDROID_SERIAL.')
adb = str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
output = Path('docs/ux-ui/implementation-evidence/tab-performance')
output.mkdir(parents=True, exist_ok=True)

def run(*command):
    return subprocess.check_output([adb, '-s', args.serial, *command])

xml_path = f'/sdcard/kipu-benchmark-{uuid.uuid4().hex}.xml'
for attempt in range(3):
    result = run('shell', 'uiautomator', 'dump', xml_path)
    if b'dumped to' in result:
        break
    time.sleep(.5)
else:
    raise SystemExit('Cannot inspect current screen; benchmark cancelled.')
tree = ET.fromstring(run('shell', 'cat', xml_path))
run('shell', 'rm', xml_path)
if not any(node.get('content-desc') == 'Registrar movimiento' for node in tree.iter('node')):
    raise SystemExit('Invalid benchmark: Kipu root dock is not visible. No measurement taken.')

for x in (540, 225, 540, 225):
    run('shell', 'input', 'tap', str(x), '2114')
    time.sleep(.35)
run('shell', 'dumpsys', 'gfxinfo', 'com.kipu.app', 'reset')
for index in range(args.switches):
    run('shell', 'input', 'tap', str(540 if index % 2 == 0 else 225), '2114')
    time.sleep(.35)
stats = run('shell', 'dumpsys', 'gfxinfo', 'com.kipu.app', 'framestats').decode().replace('\r', '')
(output / f'{args.label}-gfxinfo.txt').write_text(stats, encoding='utf-8')
print('\n'.join(stats.splitlines()[:25]))
if 'Total frames rendered: 0\n' in stats:
    raise SystemExit('Invalid benchmark: zero rendered frames; do not report this as zero jank.')
for name, x in [('money', 225), ('movements', 540)]:
    run('shell', f'input tap {x} 2114; sleep 0.05; screencap -p /sdcard/kipu-tab.png')
    run('pull', '/sdcard/kipu-tab.png', str(output / f'{args.label}-{name}-50ms.png'))
