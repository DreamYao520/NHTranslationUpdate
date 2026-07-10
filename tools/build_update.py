#!/usr/bin/env python3
"""Build a unified GTNH translation ZIP and a schema-v2 manifest.

The single translation ZIP contains:
  - assets/{domain}/{path}  — standard Minecraft resources (lang files etc.)
  - txloader/{domain}/{path} — TX Loader resources
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import shutil
import sys
import urllib.parse
import zipfile
from pathlib import Path, PurePosixPath

DOMAIN_PATTERN = re.compile(r"\[([^\[\]/]+)]$")


def zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    return info


def write_zip(target: Path, entries: dict[str, bytes]) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(entries):
            archive.writestr(zip_info(name), entries[name])


def add_entry(entries: dict[str, bytes], name: str, data: bytes, source_label: str = "") -> None:
    normalized = str(PurePosixPath(name))
    if normalized.startswith("../") or normalized.startswith("/") or ":" in normalized:
        raise ValueError(f"unsafe output path: {name}")
    previous = entries.get(normalized)
    if previous is not None and previous != data:
        if normalized.lower().endswith(".lang"):
            marker = f"\n\n# Merged from {source_label}\n".encode("utf-8")
            entries[normalized] = previous.rstrip(b"\r\n") + marker + data
            return
        raise ValueError(f"conflicting translation files map to {normalized}")
    entries[normalized] = data


def collect_resource_pack(source: Path) -> dict[str, bytes]:
    """Collect standard Minecraft resources: resources/[domain]/... -> assets/{domain}/..."""
    entries: dict[str, bytes] = {}
    resources = source / "resources"
    if resources.is_dir():
        for container in sorted(path for path in resources.iterdir() if path.is_dir()):
            match = DOMAIN_PATTERN.search(container.name)
            if not match:
                print(f"warning: skipped resource directory without [domain]: {container.name}", file=sys.stderr)
                continue
            domain = match.group(1)
            for file in sorted(path for path in container.rglob("*") if path.is_file()):
                relative = file.relative_to(container).as_posix()
                add_entry(entries, f"assets/{domain}/{relative}", file.read_bytes(), container.name)
    return entries


def collect_txloader(source: Path) -> dict[str, bytes]:
    """Collect TX Loader resources from config/txloader/ -> txloader/{domain}/..."""
    entries: dict[str, bytes] = {}
    for sub in ("load", "forceload"):
        base = source / "config" / "txloader" / sub
        if not base.is_dir():
            continue
        for domain_dir in sorted(path for path in base.iterdir() if path.is_dir()):
            domain = domain_dir.name
            for file in sorted(path for path in domain_dir.rglob("*") if path.is_file()):
                relative = file.relative_to(domain_dir).as_posix()
                add_entry(entries, f"txloader/{domain}/{relative}", file.read_bytes(), f"txloader/{sub}/{domain}")
    return entries


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def artifact(path: Path, artifact_id: str, kind: str, base_url: str, release: str) -> dict[str, object]:
    quoted_release = urllib.parse.quote(release, safe="-._~")
    quoted_name = urllib.parse.quote(path.name, safe="-._~")
    return {
        "id": artifact_id,
        "kind": kind,
        "url": f"{base_url.rstrip('/')}/releases/{quoted_release}/{quoted_name}",
        "sha256": sha256(path),
        "size": path.stat().st_size,
        "required": True,
    }


def build(args: argparse.Namespace) -> dict[str, object]:
    source = args.source.resolve()
    output = args.output.resolve()
    if not source.is_dir():
        raise ValueError(f"translation source does not exist: {source}")
    if output.exists():
        shutil.rmtree(output)
    release_dir = output / "releases" / args.release
    release_dir.mkdir(parents=True)

    entries: dict[str, bytes] = {}

    resource_entries = collect_resource_pack(source)
    for k, v in resource_entries.items():
        add_entry(entries, k, v)

    txloader_entries = collect_txloader(source)
    for k, v in txloader_entries.items():
        add_entry(entries, k, v)

    if not entries:
        raise ValueError("translation source produced no payloads")

    translation_archive = release_dir / "gtnh-zh-cn-translation.zip"
    write_zip(translation_archive, entries)

    manifest: dict[str, object] = {
        "schemaVersion": 2,
        "release": args.release,
        "minecraftVersion": "1.7.10",
        "packVersions": args.pack_version,
        "artifacts": [
            artifact(translation_archive, "gtnh-zh-cn-translation", "translation", args.base_url, args.release)
        ],
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    artifact_info = manifest["artifacts"][0]  # type: ignore[index]
    links = f'<li><a href="{html.escape(str(artifact_info["url"]))}">{html.escape(str(artifact_info["id"]))}</a></li>'
    (output / "index.html").write_text(
        "<!doctype html><meta charset=utf-8><title>NHTranslationUpdate</title>"
        f"<h1>GTNH 中文汉化更新</h1><p>当前版本：{html.escape(args.release)}</p><ul>{links}</ul>",
        encoding="utf-8",
    )
    return manifest


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--release", required=True)
    parser.add_argument("--pack-version", action="append", required=True)
    parser.add_argument("--base-url", required=True)
    return parser.parse_args(argv)


if __name__ == "__main__":
    try:
        result = build(parse_args())
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
