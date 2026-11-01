#!/usr/bin/env python3
"""Locate the legacy Treo service and pauseIfBackground references in source and packaged libraries."""
from pathlib import Path
from zipfile import ZipFile, BadZipFile
import io

root = Path(__file__).resolve().parents[1] / "extracted" / "J2ME_NST_FPS_Rebuild"
needles = (
    b"ru/playsoftware/j2meloader/keepalive/TreoOnlineService",
    b"ru.playsoftware.j2meloader.keepalive.TreoOnlineService",
    b"TreoOnlineService",
    b"pauseIfBackground",
    b"getActivity",
)

def inspect_zip(data, label, depth=0):
    if depth > 4:
        return
    try:
        with ZipFile(io.BytesIO(data)) as z:
            names = z.namelist()
            matched = []
            for name in names:
                low = name.lower()
                if low.endswith((".class", ".java", ".kt", ".xml", ".properties")):
                    try:
                        raw = z.read(name)
                    except Exception:
                        continue
                    found = [n.decode("ascii") for n in needles if n in raw]
                    if found:
                        matched.append((name, found))
                if low.endswith((".jar", ".aar", ".zip")):
                    try:
                        inspect_zip(z.read(name), label + "!" + name, depth + 1)
                    except Exception:
                        pass
            if matched:
                print(f"\nARCHIVE MATCH: {label}")
                for name, found in matched:
                    print(f"  {name}: {', '.join(found)}")
    except (BadZipFile, OSError):
        return

if not root.is_dir():
    raise SystemExit(f"Extracted project not found: {root}")

print("=== Source matches ===")
for p in root.rglob("*"):
    if p.is_file() and p.suffix.lower() in (".java", ".kt", ".xml", ".gradle", ".kts", ".properties"):
        try:
            raw = p.read_bytes()
        except OSError:
            continue
        found = [n.decode("ascii") for n in needles if n in raw]
        if found:
            print(f"{p.relative_to(root)}: {', '.join(found)}")

print("\n=== JAR/AAR/ZIP matches ===")
archives = list(root.rglob("*.jar")) + list(root.rglob("*.aar")) + list(root.rglob("*.zip"))
print("Archives found:", len(archives))
for p in archives:
    try:
        inspect_zip(p.read_bytes(), str(p.relative_to(root)))
    except OSError as e:
        print("Could not read", p, e)

print("\n=== Dependency declarations ===")
for p in list(root.rglob("build.gradle")) + list(root.rglob("build.gradle.kts")) + list(root.rglob("libs.versions.toml")):
    try:
        lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        continue
    selected = [f"{i+1}: {line.strip()}" for i, line in enumerate(lines) if "implementation" in line or "api(" in line or "files(" in line or "fileTree" in line]
    if selected:
        print(p.relative_to(root))
        for line in selected:
            print("  " + line)
