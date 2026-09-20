#!/usr/bin/env python3
"""Validate a built api/<version>/ folder against what the app expects.

    python tools/validate_content.py build/content/api/20260920

Checks the structure the Kotlin models and MediaMap require (docs/05-backend-schema.md
§3-4): spec.json shape, media map coverage, slot coverage, blurhash format and
referential integrity between wallpapers, collections, folders and artists.
Run it before handing the folder to service-export-remoteapi.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

B83 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

DOWNLOAD_SLOTS = {"dhd", "dsd"}
PREVIEW_SLOTS = {"fs", "s", "wfs", "wft", "wcs0", "wcs1", "wcs2", "wcl0", "wcl1", "wcl2"}
PROFILE_SLOTS = {"as", "am", "e"}
ALL_SLOTS = DOWNLOAD_SLOTS | PREVIEW_SLOTS | PROFILE_SLOTS


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.checks = 0

    def check(self, condition: bool, message: str) -> bool:
        self.checks += 1
        if not condition:
            self.errors.append(message)
        return condition


def check_blurhash(report: Report, value: str, where: str) -> None:
    if not report.check(isinstance(value, str) and len(value) > 6, f"{where}: empty blurHash"):
        return
    report.check(
        all(c in B83 for c in value), f"{where}: blurHash has non-base83 characters"
    )
    size = B83.index(value[0])
    nx, ny = size % 9 + 1, size // 9 + 1
    expected = 4 + 2 * (nx * ny - 1) + 2
    report.check(
        len(value) == expected,
        f"{where}: blurHash length {len(value)} != {expected} for {nx}x{ny}",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("api_dir", type=Path, help="the api/<version> folder")
    parser.add_argument(
        "--media-dir",
        type=Path,
        help="local media tree, to confirm every URL has a file (default: <api_dir>/../../media)",
    )
    args = parser.parse_args()

    api_dir: Path = args.api_dir
    report = Report()

    spec_path = api_dir / "spec.json"
    if not spec_path.is_file():
        print(f"no spec.json in {api_dir}")
        return 2
    spec = json.loads(spec_path.read_text(encoding="utf-8"))

    for key in ("content", "search", "media"):
        report.check(key in spec, f"spec.json: missing {key!r}")
    media_spec = spec["media"]
    report.check("-" in media_spec["root"], "spec.json: media.root must contain '-'")
    platforms = media_spec["p"]
    buckets = media_spec["b"]
    report.check(bool(platforms) and bool(buckets), "spec.json: empty p or b")

    content = json.loads((api_dir / Path(spec["content"]).name).read_text(encoding="utf-8"))
    search = json.loads((api_dir / Path(spec["search"]).name).read_text(encoding="utf-8"))

    media_root_name = Path(media_spec["root"]).name
    media_maps: dict[str, dict] = {}
    for platform in platforms:
        for bucket in buckets:
            name = f"{media_root_name}-{platform}-{bucket}"
            path = api_dir / name
            if not report.check(path.is_file(), f"missing media map: {name}"):
                continue
            media_maps[name] = json.loads(path.read_text(encoding="utf-8"))

    if not media_maps:
        print("no media maps found")
        return 2

    # Every media map must cover the same ids.
    id_sets = {name: set(m["data"]) for name, m in media_maps.items()}
    reference = next(iter(id_sets.values()))
    for name, ids in id_sets.items():
        report.check(ids == reference, f"{name}: media id set differs from the other maps")
        report.check(media_maps[name].get("version") == 1, f"{name}: version should be 1")

    known_ids = reference

    # Slots must be known and present for every id in every map.
    for name, media_map in media_maps.items():
        for mid, slots in media_map["data"].items():
            unknown = set(slots) - ALL_SLOTS
            report.check(not unknown, f"{name}/{mid}: unknown slot keys {sorted(unknown)}")
            report.check(
                all(isinstance(url, str) and url.startswith("http") for url in slots.values()),
                f"{name}/{mid}: non-http url",
            )

    artists = {a["id"]: a for a in content["artists"]}
    categories = {c["id"]: c for c in content["categories"]}
    wallpapers = {w["id"]: w for w in content["wallpapers"]}

    def require_slots(mid: str, needed: set[str], where: str) -> None:
        if not report.check(mid in known_ids, f"{where}: media id {mid} not in the media maps"):
            return
        for name, media_map in media_maps.items():
            missing = needed - set(media_map["data"][mid])
            report.check(not missing, f"{where}: {name} missing slots {sorted(missing)}")

    for wallpaper_id, wallpaper in wallpapers.items():
        where = f"wallpaper {wallpaper_id}"
        report.check(
            wallpaper["artistId"] in artists, f"{where}: unknown artistId {wallpaper['artistId']}"
        )
        report.check(
            wallpaper["categoryId"] in categories,
            f"{where}: unknown categoryId {wallpaper['categoryId']}",
        )
        dlm = wallpaper["dlm"]
        report.check(
            all(isinstance(dlm[k], int) for k in ("hd", "sd", "w", "h")),
            f"{where}: dlm values must be numbers",
        )
        report.check(dlm["w"] > 0 and dlm["h"] > 0, f"{where}: dlm has zero dimensions")
        require_slots(str(dlm["hd"]), DOWNLOAD_SLOTS, where + " dlm.hd")
        require_slots(str(dlm["sd"]), {"dsd"}, where + " dlm.sd")

        previews = wallpaper["previews"]["standard"]
        report.check(bool(previews), f"{where}: no previews")
        for preview in previews:
            check_blurhash(report, preview.get("blurHash", ""), where + " preview")
            require_slots(str(preview["id"]), {"s", "wfs"}, where + " preview")
        report.check(bool(wallpaper.get("slugs")), f"{where}: no slugs")

    for category_id, category in categories.items():
        where = f"collection {category_id}"
        report.check(
            category["previewRemixId"] in wallpapers,
            f"{where}: previewRemixId {category['previewRemixId']} is not a wallpaper",
        )
        for remix_id in category["remixIds"]:
            report.check(remix_id in wallpapers, f"{where}: unknown remixId {remix_id}")
        report.check(category["artistId"] in artists, f"{where}: unknown artistId")

    for artist_id, artist in artists.items():
        where = f"artist {artist_id}"
        for category_id in artist["categoryIds"]:
            report.check(category_id in categories, f"{where}: unknown categoryId {category_id}")
        for key in ("profileImage", "featureBannerImage"):
            image = artist.get(key)
            if image:
                check_blurhash(report, image.get("blurHash", ""), f"{where}.{key}")
                require_slots(str(image["id"]), {"as", "am"}, f"{where}.{key}")

    for folder in content["folders"]:
        where = f"folder {folder['id']}"
        for remix_id in folder.get("remixIds", []):
            report.check(remix_id in wallpapers, f"{where}: unknown remixId {remix_id}")
        for category_id in folder.get("collectionIds", []):
            report.check(category_id in categories, f"{where}: unknown collectionId {category_id}")
        for key in ("profileImage", "featureBannerImage"):
            image = folder.get(key)
            if image:
                check_blurhash(report, image.get("blurHash", ""), f"{where}.{key}")

    indexed = {entry["remixId"] for entry in search["remixMetadata"]}
    missing_index = set(wallpapers) - indexed
    report.check(not missing_index, f"search index missing {len(missing_index)} wallpapers")
    report.check(
        not (indexed - set(wallpapers)), "search index references unknown wallpapers"
    )

    media_dir = args.media_dir or api_dir.parent.parent / "media"
    if media_dir.is_dir():
        missing_files = set()
        for media_map in media_maps.values():
            for mid, slots in media_map["data"].items():
                for url in slots.values():
                    rel = "/".join(url.split("/")[-2:])
                    if not (media_dir / rel).is_file():
                        missing_files.add(rel)
        report.check(not missing_files, f"{len(missing_files)} media files missing in {media_dir}")
    else:
        print(f"note: no media tree at {media_dir}, skipping file check")

    print(f"{report.checks} checks, {len(report.errors)} failed")
    print(
        f"  {len(wallpapers)} wallpapers · {len(categories)} collections · "
        f"{len(artists)} artists · {len(content['folders'])} folders · "
        f"{len(known_ids)} media ids · {len(media_maps)} media maps"
    )
    for error in report.errors[:40]:
        print(f"  ✗ {error}")
    if len(report.errors) > 40:
        print(f"  … and {len(report.errors) - 40} more")
    return 1 if report.errors else 0


if __name__ == "__main__":
    sys.exit(main())
