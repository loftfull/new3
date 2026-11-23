"""Smoke-test local capture on a disposable Android emulator via adb."""
import argparse
import hashlib
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


APP = "com.knowledgehub.app"
COMPONENT = f"{APP}/.MainActivity"


def adb(*args, binary=False):
    result = subprocess.run(
        ["adb", *args], check=True, capture_output=True, timeout=60
    )
    return result.stdout if binary else result.stdout.decode().strip()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def locate(label):
    for _ in range(8):
        adb("shell", "uiautomator", "dump", "/data/local/tmp/kh-window.xml")
        tree = ET.fromstring(adb("shell", "cat", "/data/local/tmp/kh-window.xml"))
        for node in tree.iter("node"):
            if label in (node.get("text"), node.get("content-desc")):
                return node
        time.sleep(1)
    raise RuntimeError(f"Missing UI element: {label}")


def tap(label):
    x0, y0, x1, y1 = map(int, re.findall(r"\d+", locate(label).get("bounds")))
    adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))


def screenshot(directory, name):
    image = adb("exec-out", "screencap", "-p", binary=True)
    require(image.startswith(b"\x89PNG\r\n\x1a\n"), "adb did not return a PNG")
    (directory / f"{name}.png").write_bytes(image)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    emulator = adb("shell", "getprop", "ro.boot.qemu") or adb("shell", "getprop", "ro.kernel.qemu")
    require(emulator == "1", "Use a disposable emulator")
    require(adb("shell", "getprop", "sys.boot_completed") == "1", "Android must finish booting")
    args.evidence.mkdir(parents=True, exist_ok=True)
    print("Android API:", adb("shell", "getprop", "ro.build.version.sdk"), flush=True)
    print("APK SHA-256:", hashlib.sha256(args.apk.read_bytes()).hexdigest(), flush=True)
    print(adb("install", "-r", str(args.apk)), flush=True)
    adb("shell", "cmd", "window", "dismiss-keyguard")
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    require(adb("shell", "settings", "get", "global", "airplane_mode_on") == "1", "Airplane mode did not enable")

    label = f"QA-{uuid.uuid4().hex[:8]}"
    url = f"https://developer.android.com/guide/components/intents-filters?test={label}"
    adb("shell", "am", "force-stop", APP)
    adb("shell", "am", "start", "-W", "-n", COMPONENT)
    locate("Библиотека")
    screenshot(args.evidence, "S01-library")
    adb("shell", "am", "force-stop", APP)
    launched = adb(
        "shell", "am", "start", "-W", "-a", "android.intent.action.SEND",
        "-t", "text/plain", "--es", "android.intent.extra.TEXT", url,
        "--es", "android.intent.extra.SUBJECT", label, APP,
    )
    require("Error" not in launched and APP in launched, launched)
    locate(url)
    screenshot(args.evidence, "S07-share-preview")
    tap("Сохранить")
    locate(label)
    adb("shell", "am", "force-stop", APP)
    adb("shell", "am", "start", "-W", "-n", COMPONENT)
    locate(label)
    screenshot(args.evidence, "S01-offline-restart")
    tap(label)
    locate(url)
    screenshot(args.evidence, "S13-original-link")
    print("PASS: implicit ACTION_SEND, preview, offline save, process restart, exact URL.", flush=True)


if __name__ == "__main__":
    main()
