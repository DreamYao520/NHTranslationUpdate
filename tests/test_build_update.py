import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "tools" / "build_update.py"


def add_language(source: Path, language: str, translated: str) -> None:
    root = source / language
    gregtech = root / f"GregTech_{language}.lang"
    gregtech.parent.mkdir(parents=True, exist_ok=True)
    gregtech.write_text(f"languagefile {{\n S:key={translated}\n}}\n", encoding="utf-8")
    base = root / "config" / "txloader" / "load" / "Example Mod[example]" / "lang" / f"{language}.lang"
    forced = (
        root / "config" / "txloader" / "forceload" / "Override[example]" / "lang" / f"{language}.lang"
    )
    base.parent.mkdir(parents=True, exist_ok=True)
    forced.parent.mkdir(parents=True, exist_ok=True)
    base.write_text(f"same.key={translated}-base\nbase.key={translated}\n", encoding="utf-8")
    forced.write_text(f"same.key={translated}-forced\n", encoding="utf-8")
    tips = root / "config" / "Betterloadingscreen" / "tips" / f"{language}.txt"
    tips.parent.mkdir(parents=True, exist_ok=True)
    tips.write_text(translated + "\n", encoding="utf-8")


def run_build(source: Path, output: Path, release: str, version: str, *extra: str) -> None:
    subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--source",
            str(source),
            "--output",
            str(output),
            "--release",
            release,
            "--pack-version",
            version,
            "--base-url",
            output.as_uri(),
            *extra,
        ],
        check=True,
        capture_output=True,
        text=True,
    )


class BuildUpdateTest(unittest.TestCase):
    def test_builds_all_official_languages_and_flattens_txloader_priority(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "GTNH-Translations"
            output = root / "site"
            add_language(source, "zh_CN", "中文")
            add_language(source, "ja_JP", "日本語")

            run_build(source, output, "2.9.0-beta-2-multilang.1", "2.9.0-beta-2")
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            release = manifest["packs"]["2.9.0-beta-2"]
            self.assertEqual(3, manifest["schemaVersion"])
            self.assertEqual(["ja_JP", "zh_CN"], release["languages"])
            self.assertEqual("gtnh-multilingual-translation", release["artifacts"][0]["id"])

            archive_path = (
                output / "releases" / "2.9.0-beta-2-multilang.1" / "gtnh-multilingual-translation.zip"
            )
            with zipfile.ZipFile(archive_path) as archive:
                names = archive.namelist()
                for language in ("ja_JP", "zh_CN"):
                    resource = f"assets/example/lang/{language}.lang"
                    self.assertIn(resource, names)
                    merged = archive.read(resource).decode("utf-8")
                    self.assertLess(merged.index("-base"), merged.index("-forced"))
                    self.assertIn(f"install/config/GregTech_{language}.lang", names)
                    self.assertIn(f"install/config/Betterloadingscreen/tips/{language}.txt", names)
                self.assertFalse(any(name.startswith("txloader/") for name in names))

    def test_preserves_previous_schema_v3_release(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "GTNH-Translations"
            add_language(source, "zh_CN", "第一版")
            old_site = root / "old-site"
            run_build(source, old_site, "2.8.4-multilang.1", "2.8.4")

            add_language(source, "ja_JP", "第二版")
            new_site = root / "new-site"
            run_build(
                source,
                new_site,
                "2.9.0-beta-2-multilang.1",
                "2.9.0-beta-2",
                "--existing-site-url",
                old_site.as_uri(),
            )
            manifest = json.loads((new_site / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual({"2.8.4", "2.9.0-beta-2"}, set(manifest["packs"]))
            self.assertTrue(
                (new_site / "releases" / "2.8.4-multilang.1" / "gtnh-multilingual-translation.zip").is_file()
            )


if __name__ == "__main__":
    unittest.main()
