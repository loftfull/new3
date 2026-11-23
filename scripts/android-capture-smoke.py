"""Smoke-test local capture on a disposable Android emulator via adb."""
import argparse
import hashlib
import re
import shlex
import struct
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


def visible_nodes():
    dump = adb("shell", "uiautomator", "dump", "/data/local/tmp/kh-window.xml")
    if "dumped to" not in dump:
        return []
    return list(ET.fromstring(adb("shell", "cat", "/data/local/tmp/kh-window.xml")).iter("node"))


def locate(label):
    for _ in range(8):
        for node in visible_nodes():
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


def capture_reference_viewport(directory):
    fixtures = [
        ("Связи между заметками", "https://example.org/connections"),
        ("Доступность мобильных интерфейсов", "https://example.org/accessibility"),
        ("Идеи для личной библиотеки", "Список пользовательских идей"),
        ("Справочник по типографике", "https://example.org/typography.pdf"),
        ("Сторителлинг для коротких видео", "https://youtu.be/example"),
        ("Как устроена хорошая заметка", "https://example.org/good-note"),
    ]
    previous_font = adb("shell", "settings", "get", "system", "font_scale")
    try:
        adb("shell", "settings", "put", "system", "font_scale", "1.0")
        adb("shell", "wm", "size", "780x1690")
        adb("shell", "wm", "density", "320")
        for title, content in fixtures:
            adb("shell", "am", "force-stop", APP)
            launched = adb(
                "shell", "am", "start", "-W", "-a", "android.intent.action.SEND",
                "-t", "text/plain", "--es", "android.intent.extra.TEXT", shlex.quote(content),
                "--es", "android.intent.extra.SUBJECT", shlex.quote(title), APP,
            )
            require("Error" not in launched and APP in launched, launched)
            locate(content)
            tap("Сохранить")
            locate(title)
        titles = [title for title, _ in reversed(fixtures)]
        for _ in range(8):
            nodes = visible_nodes()
            displayed = [node.get("text") for node in nodes]
            matching = [title for title in displayed if title in titles]
            if matching == titles and "Материал сохранён" not in displayed:
                break
            time.sleep(1)
        else:
            raise RuntimeError(f"Expected six visible titles in reference order, found: {matching}")
        font_scale = adb("shell", "settings", "get", "system", "font_scale")
        require(float(font_scale) == 1.0, f"Unexpected font scale: {font_scale}")
        print("S01 populated evidence: 780x1690, density 320, font scale 1.0, six ordered rows.", flush=True)
        screenshot(directory, "S01-populated-390dp")
        path = directory / "S01-populated-390dp.png"
        require(struct.unpack(">II", path.read_bytes()[16:24]) == (780, 1690), "Unexpected reference viewport")
        for tab in ("Библиотека", "Коллекции", "Добавить", "Поиск", "AI"):
            locate(tab)
    finally:
        adb("shell", "wm", "size", "reset")
        adb("shell", "wm", "density", "reset")
        if previous_font == "null":
            adb("shell", "settings", "delete", "system", "font_scale")
        else:
            adb("shell", "settings", "put", "system", "font_scale", previous_font)


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
    capture_reference_viewport(args.evidence)


if __name__ == "__main__":
    main()
