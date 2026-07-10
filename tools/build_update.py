#!/usr/bin/env python3
"""Build deterministic GTNH translation payloads and a schema-v1 manifest."""

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

DEFAULT_OVERLAY_ROOTS = (
    "config/txloader",
    "config/Betterloadingscreen",
    "config/amazingtrophies",
    "config/InGameInfoXML",
)
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
    metadata = {"pack": {"pack_format": 1, "description": "GTNH 简体中文社区自动更新汉化"}}
    entries["pack.mcmeta"] = (json.dumps(metadata, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    return entries


def collect_overlay(source: Path, roots: tuple[str, ...]) -> dict[str, bytes]:
    entries: dict[str, bytes] = {}
    for root in roots:
        directory = source / Path(root)
        if not directory.is_dir():
            continue
        for file in sorted(path for path in directory.rglob("*") if path.is_file()):
            add_entry(entries, file.relative_to(source).as_posix(), file.read_bytes())
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

    artifacts: list[dict[str, object]] = []
    resource_entries = collect_resource_pack(source)
    if len(resource_entries) > 1:
        resource_archive = release_dir / "gtnh-zh-cn-resource-pack.zip"
        write_zip(resource_archive, resource_entries)
        artifacts.append(artifact(resource_archive, "gtnh-zh-cn-resource-pack", "resource_pack", args.base_url, args.release))

    overlay_entries = collect_overlay(source, tuple(args.overlay_root))
    if overlay_entries:
        overlay_archive = release_dir / "gtnh-zh-cn-overlay.zip"
        write_zip(overlay_archive, overlay_entries)
        artifacts.append(artifact(overlay_archive, "gtnh-zh-cn-overlay", "overlay", args.base_url, args.release))

    if not artifacts:
        raise ValueError("translation source produced no payloads")
    manifest: dict[str, object] = {
        "schemaVersion": 1,
        "release": args.release,
        "minecraftVersion": "1.7.10",
        "packVersions": args.pack_version,
        "artifacts": artifacts,
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    links = "".join(
        f'<li><a href="{html.escape(item["url"])}">{html.escape(str(item["id"]))}</a></li>' for item in artifacts
    )
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
    parser.add_argument("--overlay-root", action="append", default=list(DEFAULT_OVERLAY_ROOTS))
    return parser.parse_args(argv)


if __name__ == "__main__":
    try:
        result = build(parse_args())
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
