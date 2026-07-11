#!/usr/bin/env python3
"""Build a unified GTNH translation ZIP and a schema-v3 version catalog."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

DOMAIN_PATTERN = re.compile(r"\[([^\[\]/]+)]$")
SAFE_LABEL = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+\-]{0,79}")
MAX_MANIFEST_BYTES = 1024 * 1024


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


def add_entry(
    entries: dict[str, bytes],
    name: str,
    data: bytes,
    source_label: str = "",
    replace_non_lang: bool = False,
) -> None:
    normalized = str(PurePosixPath(name))
    if normalized.startswith("../") or normalized.startswith("/") or ":" in normalized:
        raise ValueError(f"unsafe output path: {name}")
    previous = entries.get(normalized)
    if previous is not None and previous != data:
        if normalized.lower().endswith(".lang"):
            marker = f"\n\n# NHTranslationUpdate: merged from {source_label}\n".encode("utf-8")
            entries[normalized] = previous.rstrip(b"\r\n") + marker + data
            return
        if not replace_non_lang:
            raise ValueError(f"conflicting translation files map to {normalized}")
    entries[normalized] = data


def collect_resource_pack(source: Path) -> dict[str, bytes]:
    """Collect resources/[domain]/... as assets/{domain}/...."""
    entries: dict[str, bytes] = {}
    resources = source / "resources"
    if resources.is_dir():
        for container in sorted(path for path in resources.iterdir() if path.is_dir()):
            match = DOMAIN_PATTERN.search(container.name)
            if not match:
                print(f"warning: skipped resource directory without [domain]: {container.name}", file=sys.stderr)
                continue
            domain = match.group(1).lower()
            for file in sorted(path for path in container.rglob("*") if path.is_file()):
                relative = file.relative_to(container).as_posix()
                add_entry(entries, f"assets/{domain}/{relative}", file.read_bytes(), container.name)
    return entries


def collect_txloader_layer(source: Path, layer: str) -> dict[str, bytes]:
    """Map one TX Loader layer into the standard resource-pack namespace."""
    entries: dict[str, bytes] = {}
    base = source / "config" / "txloader" / layer
    if not base.is_dir():
        return entries
    for domain_dir in sorted(path for path in base.iterdir() if path.is_dir()):
        domain = domain_dir.name.lower()
        for file in sorted(path for path in domain_dir.rglob("*") if path.is_file()):
            relative = file.relative_to(domain_dir).as_posix()
            add_entry(entries, f"assets/{domain}/{relative}", file.read_bytes(), f"txloader/{layer}/{domain}")
    return entries


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def artifact(path: Path, artifact_id: str, base_url: str, release: str) -> dict[str, object]:
    quoted_release = urllib.parse.quote(release, safe="-._~")
    quoted_name = urllib.parse.quote(path.name, safe="-._~")
    return {
        "id": artifact_id,
        "kind": "translation",
        "url": f"{base_url.rstrip('/')}/releases/{quoted_release}/{quoted_name}",
        "sha256": sha256(path),
        "size": path.stat().st_size,
        "required": True,
    }


def read_url(url: str, maximum: int) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "NHTranslationUpdate-publisher/1"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError(f"remote file is larger than expected: {url}")
    return data


def preserve_existing_site(existing_site_url: str, output: Path, base_url: str) -> dict[str, object] | None:
    manifest_url = existing_site_url.rstrip("/") + "/manifest.json"
    try:
        raw = read_url(manifest_url, MAX_MANIFEST_BYTES)
    except (OSError, urllib.error.URLError) as error:
        print(f"warning: no existing update catalog was preserved: {error}", file=sys.stderr)
        return None

    manifest = json.loads(raw.decode("utf-8"))
    if manifest.get("schemaVersion") != 3 or not isinstance(manifest.get("packs"), dict):
        print("warning: existing site does not use schema v3; starting a new catalog", file=sys.stderr)
        return None

    for pack_version, release_info in manifest["packs"].items():
        if not SAFE_LABEL.fullmatch(pack_version) or not isinstance(release_info, dict):
            raise ValueError(f"invalid pack version in existing catalog: {pack_version}")
        release = release_info.get("release")
        if not isinstance(release, str) or not SAFE_LABEL.fullmatch(release):
            raise ValueError(f"invalid release in existing catalog: {release}")
        artifacts = release_info.get("artifacts")
        if not isinstance(artifacts, list):
            raise ValueError(f"missing artifacts in existing catalog for {pack_version}")
        for item in artifacts:
            if not isinstance(item, dict):
                raise ValueError(f"invalid artifact in existing catalog for {pack_version}")
            url = item.get("url")
            expected_hash = item.get("sha256")
            expected_size = item.get("size")
            if not isinstance(url, str) or not isinstance(expected_hash, str) or not isinstance(expected_size, int):
                raise ValueError(f"invalid artifact metadata in existing catalog for {pack_version}")
            filename = Path(urllib.parse.unquote(urllib.parse.urlparse(url).path)).name
            if not filename or filename in (".", ".."):
                raise ValueError(f"invalid artifact URL in existing catalog: {url}")
            relative = f"releases/{urllib.parse.quote(release, safe='-._~')}/{urllib.parse.quote(filename, safe='-._~')}"
            data = read_url(existing_site_url.rstrip("/") + "/" + relative, expected_size)
            if len(data) != expected_size or sha256_bytes(data).lower() != expected_hash.lower():
                raise ValueError(f"existing artifact failed integrity validation: {pack_version}/{filename}")
            target = output / "releases" / release / filename
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            item["url"] = base_url.rstrip("/") + "/" + relative
    return manifest


def build(args: argparse.Namespace) -> dict[str, object]:
    source = args.source.resolve()
    output = args.output.resolve()
    if not source.is_dir():
        raise ValueError(f"translation source does not exist: {source}")
    if not SAFE_LABEL.fullmatch(args.release):
        raise ValueError(f"invalid release: {args.release}")
    for version in args.pack_version:
        if not SAFE_LABEL.fullmatch(version):
            raise ValueError(f"invalid GTNH pack version: {version}")

    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    manifest = None
    if args.existing_site_url:
        manifest = preserve_existing_site(args.existing_site_url, output, args.base_url)
    if manifest is None:
        manifest = {"schemaVersion": 3, "minecraftVersion": "1.7.10", "packs": {}}

    entries = collect_resource_pack(source)
    for layer in ("load", "forceload"):
        for name, data in collect_txloader_layer(source, layer).items():
            add_entry(entries, name, data, f"txloader/{layer}", replace_non_lang=True)

    gregtech = source / "GregTech.lang"
    if gregtech.is_file():
        add_entry(entries, "install/config/GregTech_zh_CN.lang", gregtech.read_bytes(), "GregTech.lang")
    if not entries:
        raise ValueError("translation source produced no payloads")

    release_dir = output / "releases" / args.release
    translation_archive = release_dir / "gtnh-zh-cn-translation.zip"
    write_zip(translation_archive, entries)
    release_info = {
        "release": args.release,
        "artifacts": [artifact(translation_archive, "gtnh-zh-cn-translation", args.base_url, args.release)],
    }
    for version in args.pack_version:
        manifest["packs"][version] = release_info

    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    rows = []
    for version, info in sorted(manifest["packs"].items()):
        item = info["artifacts"][0]
        rows.append(
            f'<li>GTNH {html.escape(version)}：<a href="{html.escape(str(item["url"]))}">'
            f'{html.escape(str(info["release"]))}</a></li>'
        )
    (output / "index.html").write_text(
        "<!doctype html><meta charset=utf-8><title>NHTranslationUpdate</title>"
        f"<h1>GTNH 简体中文汉化更新</h1><ul>{''.join(rows)}</ul>",
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
    parser.add_argument("--existing-site-url")
    return parser.parse_args(argv)


if __name__ == "__main__":
    try:
        result = build(parse_args())
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
