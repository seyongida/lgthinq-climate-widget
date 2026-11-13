#!/usr/bin/env python3
"""Captures Google Play phone screenshots from the Android sample mode.

Needs one running emulator or device with a 9:16 screen (Play rejects screens taller than 2:1) and the
release APK path as the argument. Output: play/images/ko-KR/phone/N-name.png
"""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = str(Path.home() / 'Library/Android/sdk/platform-tools/adb')
PACKAGE = 'dev.thinqclimate.widget.release'
OUT = Path(__file__).resolve().parent.parent / 'play/images/ko-KR/phone'


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, check=check, stdin=subprocess.DEVNULL).stdout


def shell(command):
    return adb('shell', command).decode(errors='replace')


def find(text, timeout=15):
    """Center of the first view whose text or description contains `text`."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        shell('uiautomator dump /sdcard/ui.xml >/dev/null')
        xml = adb('exec-out', 'cat', '/sdcard/ui.xml').decode(errors='replace')
        for node in re.finditer(r'<node [^>]*>', xml):
            tag = node.group(0)
            if f'text="{text}' in tag or f'content-desc="{text}' in tag:
                x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag).groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
        time.sleep(1)
    raise SystemExit(f'Not found on screen: {text}')


def tap(text):
    x, y = find(text)
    shell(f'input tap {x} {y}')
    time.sleep(1.5)


def shot(name):
    time.sleep(1.5)
    (OUT / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    print('captured', name)


def scroll_to(text, limit=8):
    for _ in range(limit):
        shell('uiautomator dump /sdcard/ui.xml >/dev/null')
        xml = adb('exec-out', 'cat', '/sdcard/ui.xml').decode(errors='replace')
        if f'text="{text}' in xml:
            return
        shell('input swipe 540 1500 540 700 400')
        time.sleep(1)
    raise SystemExit(f'Could not scroll to: {text}')


def main():
    apk = sys.argv[1]
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.png'):
        old.unlink()
    adb('uninstall', PACKAGE, check=False)
    adb('install', '-r', '-g', apk)
    # Clean status bar for store images.
    for command in ['settings put global sysui_demo_allowed 1',
                    'am broadcast -a com.android.systemui.demo -e command enter',
                    'am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941',
                    'am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false',
                    'am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4',
                    'am broadcast -a com.android.systemui.demo -e command notifications -e visible false']:
        shell(command)
    shell(f'am start -W -n {PACKAGE}/dev.thinqclimate.widget.MainActivity >/dev/null')
    # The welcome screen holds the token field and blocks screenshots (FLAG_SECURE), so it is not captured.
    tap('샘플 데이터로 체험하기')
    find('곧 끝나요')
    shot('1-home')
    scroll_to('전기 사용량')
    shell('input swipe 540 1200 540 900 400')
    shot('2-energy')
    scroll_to('홈 화면 위젯')
    shell('input swipe 540 1500 540 700 400')
    shot('3-widgets')
    shell('am broadcast -a com.android.systemui.demo -e command exit')


if __name__ == '__main__':
    main()
