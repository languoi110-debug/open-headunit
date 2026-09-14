#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path.cwd()
settings_path = ROOT / "app/src/main/java/com/andrerinas/openheadunit/utils/Settings.kt"
strings_path = ROOT / "app/src/main/res/values/strings.xml"
gradle_path = ROOT / "app/build.gradle.kts"

for p in (settings_path, strings_path, gradle_path):
    if not p.exists():
        print(f"ERROR: missing {p}")
        sys.exit(2)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        print(f"ERROR: {label}: expected 1 match, found {count}")
        sys.exit(3)
    print(f"OK: {label}")
    return text.replace(old, new, 1)

# Samsung Galaxy J2 Prime SM-G532G/DS preset.
s = settings_path.read_text(encoding="utf-8")
s = replace_once(s,
    'get() = prefs.getInt("resolutionId", 0)',
    'get() = prefs.getInt("resolutionId", 1)',
    '800x480 default resolution')
s = replace_once(s,
    'val value = prefs.getInt("view-mode", 1)',
    'val value = prefs.getInt("view-mode", 2)',
    'GLES renderer default')
s = replace_once(s,
    'get() = prefs.getString("vehicle-display-name", "Open Headunit")!!',
    'get() = prefs.getString("vehicle-display-name", "Moto Headunit")!!',
    'Moto Headunit display name')
s = replace_once(s,
    'get() = prefs.getString("vehicle-model", "Desktop Head Unit")!!',
    'get() = prefs.getString("vehicle-model", "J2 Prime SM-G532G/DS")!!',
    'J2 Prime vehicle model')
s = replace_once(s,
    'get() = prefs.getString("vehicle-id", "headlessunit-001")!!',
    'get() = prefs.getString("vehicle-id", "moto-headunit-j2prime")!!',
    'Moto vehicle id')
s = replace_once(s,
    'get() = VehicleTypePolicy.sanitised(prefs.getInt("vehicle-type", VehicleTypePolicy.CAR))',
    'get() = VehicleTypePolicy.sanitised(prefs.getInt("vehicle-type", VehicleTypePolicy.MOTORCYCLE))',
    'Android Auto motorcycle vehicle type')
s = replace_once(s,
    'get() = prefs.getString("video-codec", "Auto")!!',
    'get() = prefs.getString("video-codec", "H.264")!!',
    'H.264 default codec')
s = replace_once(s,
    'get() = prefs.getInt("fps-limit", 60)',
    'get() = prefs.getInt("fps-limit", 30)',
    '30 FPS limit')
s = replace_once(s,
    'get() = prefs.getBoolean("use-head-unit-microphone", true)',
    'get() = prefs.getBoolean("use-head-unit-microphone", false)',
    'phone/intercom microphone')
settings_path.write_text(s, encoding="utf-8")

# Branding.
t = strings_path.read_text(encoding="utf-8")
t = replace_once(t,
    '<string name="app_name">Open Headunit</string>',
    '<string name="app_name">Moto Headunit</string>',
    'app name')
t = replace_once(t,
    '<string name="title">Open Headunit</string>',
    '<string name="title">Moto Headunit</string>',
    'app title')
strings_path.write_text(t, encoding="utf-8")

# Separate package/version so it can coexist with upstream Open Headunit.
g = gradle_path.read_text(encoding="utf-8")
g = replace_once(g,
    'applicationId = "com.andrerinas.headunitrevived"',
    'applicationId = "com.motoheadunit.j2prime"',
    'application id')
g = replace_once(g,
    'versionCode = 108',
    'versionCode = 1',
    'version code')
g = replace_once(g,
    'versionName = "3.4.0-beta3"',
    'versionName = "0.1.0-j2prime"',
    'version name')
gradle_path.write_text(g, encoding="utf-8")

print("Moto Headunit J2 Prime patch applied successfully.")
