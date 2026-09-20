#!/usr/bin/env python3
"""Build the bkgapp content API from master images + content.yaml.

Emits the flat `api/<version>/` folder the app expects (see docs/05-backend-schema.md
§3 and §10) plus the resized media tree that the media maps point at.

    python tools/content_build.py content/content.yaml -o build/content

The API folder is then handed to service/service-export-remoteapi, which encrypts
every file to `<name>.data` and uploads it to Firebase Storage. Media binaries are
uploaded separately (they are not encrypted).

Requires: Pillow, PyYAML. Blurhash is implemented here, no extra dependency.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
import sys
from dataclasses import dataclass, field
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is required: pip install -r tools/requirements.txt")

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: pip install -r tools/requirements.txt")


# --------------------------------------------------------------------------- #
# Device buckets — mirror of shared/core/common/.../image/bucket/ImageBucketSpecs.kt
# Keep in sync with the app; the keys are what `spec.json` advertises in "b".
# --------------------------------------------------------------------------- #

BUCKETS: dict[str, tuple[int, int]] = {
    "p~s": (960, 1800),
    "p~five0": (1080, 2160),
    "p~a~n": (1179, 2556),
    "p~a~xl": (1290, 2796),
    "p~uhd": (1440, 3120),
    "f~fo": (2208, 1840),
    "t~s": (744, 1133),
    "t~m": (1848, 2960),
    "t~l": (2048, 2732),
}

# Slot keys — shared/data/base/.../image/sized/SizedImage.kt
DOWNLOAD_SLOTS = ("dhd", "dsd")
PREVIEW_SLOTS = ("fs", "s", "wfs", "wft", "wcs0", "wcs1", "wcs2", "wcl0", "wcl1", "wcl2")
PROFILE_SLOTS = ("as", "am", "e")

PROFILE_SIZES = {"as": 168, "am": 288}  # 56.dp / 96.dp at 3x

# NetworkSocialLinks — anything else is dropped by ignoreUnknownKeys, so reject it here.
SOCIAL_KEYS = {"facebook", "instagram", "shop", "tiktok", "twitter", "website", "youtube"}


def slot_sizes(bucket: str) -> dict[str, tuple[int, int]]:
    """Target pixel size per slot for one device bucket.

    Nothing in the app pins these — it computes the size it wants at runtime and
    only appends imgix parameters when the URL is an imgix host. We have no imgix,
    so these are our choice. Sizes collapse to four distinct variants per bucket,
    and identical sizes share one file on disk.
    """
    w, h = BUCKETS[bucket]
    half = (w // 2, h // 2)
    return {
        "dhd": (w, h),
        "dsd": half,
        "fs": (w, h),
        "s": (w, h),
        "wfs": half,
        "wft": (w // 2, w // 2),
        "wcs0": half,
        "wcs1": half,
        "wcs2": half,
        "wcl0": (w, h),
        "wcl1": (w, h),
        "wcl2": (w, h),
        "e": (w, w),
        "as": (PROFILE_SIZES["as"], PROFILE_SIZES["as"]),
        "am": (PROFILE_SIZES["am"], PROFILE_SIZES["am"]),
    }


# --------------------------------------------------------------------------- #
# Blurhash (https://blurhash.dev) — encode only; the app decodes.
# 4x4 components, matching the upstream export.
# --------------------------------------------------------------------------- #

B83 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"


def _b83(value: int, length: int) -> str:
    out = ""
    for i in range(1, length + 1):
        digit = (value // (83 ** (length - i))) % 83
        out += B83[digit]
    return out


def _srgb_to_linear(v: int) -> float:
    x = v / 255.0
    return x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(v: float) -> int:
    x = max(0.0, min(1.0, v))
    if x <= 0.0031308:
        return int(x * 12.92 * 255 + 0.5)
    return int((1.055 * (x ** (1 / 2.4)) - 0.055) * 255 + 0.5)


def _quantise(value: float) -> int:
    # floor(sign(v) * sqrt(|v|) * 9 + 9.5), clamped — the reference encoder's rounding.
    return max(0, min(18, math.floor(math.copysign(abs(value) ** 0.5 * 9, value) + 9.5)))


def blurhash_encode(image: Image.Image, nx: int = 4, ny: int = 4) -> str:
    """Standard blurhash of a small downscale of the image."""
    img = image.convert("RGB")
    img.thumbnail((64, 64), Image.Resampling.LANCZOS)
    width, height = img.size
    raw = img.tobytes()
    linear = [
        (
            _srgb_to_linear(raw[i]),
            _srgb_to_linear(raw[i + 1]),
            _srgb_to_linear(raw[i + 2]),
        )
        for i in range(0, len(raw), 3)
    ]

    components: list[tuple[float, float, float]] = []
    for j in range(ny):
        for i in range(nx):
            normalisation = 1.0 if (i == 0 and j == 0) else 2.0
            r = g = b = 0.0
            for y in range(height):
                cos_y = math.cos(math.pi * j * y / height)
                for x in range(width):
                    basis = normalisation * math.cos(math.pi * i * x / width) * cos_y
                    pr, pg, pb = linear[y * width + x]
                    r += basis * pr
                    g += basis * pg
                    b += basis * pb
            scale = 1.0 / (width * height)
            components.append((r * scale, g * scale, b * scale))

    dc, ac = components[0], components[1:]
    out = _b83((ny - 1) * 9 + (nx - 1), 1)

    if ac:
        actual_max = max(max(abs(c) for c in comp) for comp in ac)
        quantised_max = max(0, min(82, int(actual_max * 166 - 0.5)))
        maximum = (quantised_max + 1) / 166
        out += _b83(quantised_max, 1)
    else:
        maximum = 1.0
        out += _b83(0, 1)

    out += _b83(
        (_linear_to_srgb(dc[0]) << 16) + (_linear_to_srgb(dc[1]) << 8) + _linear_to_srgb(dc[2]),
        4,
    )
    for r, g, b in ac:
        value = (
            _quantise(r / maximum) * 19 * 19
            + _quantise(g / maximum) * 19
            + _quantise(b / maximum)
        )
        out += _b83(value, 2)
    return out


# --------------------------------------------------------------------------- #
# Ids
# --------------------------------------------------------------------------- #


def _digest(*parts: str) -> str:
    return hashlib.sha256("\u0000".join(parts).encode("utf-8")).hexdigest()


def media_id(key: str) -> int:
    """Stable positive int that fits a Kotlin Long. Must not change between builds."""
    return int(_digest("media", key)[:9], 16)


def short_slug(key: str) -> str:
    """Short, URL-safe wallpaper slug, e.g. 2QR."""
    alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    value = int(_digest("slug", key)[:8], 16)
    out = ""
    for _ in range(3):
        out += alphabet[value % len(alphabet)]
        value //= len(alphabet)
    return out


# --------------------------------------------------------------------------- #
# Image rendering
# --------------------------------------------------------------------------- #


def render(master: Image.Image, target: tuple[int, int]) -> Image.Image:
    """Centre cover-crop to the target aspect, resize to target, never upscale."""
    tw, th = target
    sw, sh = master.size

    crop_w, crop_h = sw, int(round(sw * th / tw))
    if crop_h > sh:
        crop_h = sh
        crop_w = int(round(sh * tw / th))
    left = (sw - crop_w) // 2
    top = (sh - crop_h) // 2
    cropped = master.crop((left, top, left + crop_w, top + crop_h))

    if crop_w < tw:  # master is smaller than asked for — cap instead of upscaling
        tw, th = crop_w, max(1, int(round(th * crop_w / tw)))
    return cropped.resize((tw, th), Image.Resampling.LANCZOS)


@dataclass
class MediaAsset:
    """One source image and every variant generated from it."""

    key: str
    path: Path
    slots: tuple[str, ...]
    mid: int = 0
    blur_hash: str = ""
    width: int = 0
    height: int = 0
    # bucket -> slot -> relative url path
    urls: dict[str, dict[str, str]] = field(default_factory=dict)


class Builder:
    def __init__(self, config: dict, root: Path, out: Path, quality: dict[str, int]):
        self.config = config
        self.root = root
        self.out = out
        self.quality = quality
        self.buckets: list[str] = config.get("buckets") or list(BUCKETS)
        self.platforms: list[str] = config.get("platforms") or ["c"]
        self.media_base_url: str = config["media_base_url"].rstrip("/")
        self.assets: dict[str, MediaAsset] = {}
        self.rendered = 0
        self.reused = 0
        self.seen_variants: set[str] = set()

    # -- media ------------------------------------------------------------- #

    def add_asset(self, key: str, path: Path, slots: tuple[str, ...]) -> MediaAsset:
        asset = self.assets.get(key)
        if asset is None:
            if not path.is_file():
                raise SystemExit(f"missing image: {path}")
            asset = MediaAsset(key=key, path=path, slots=slots)
            asset.mid = media_id(key)
            self.assets[key] = asset
        else:
            asset.slots = tuple(dict.fromkeys(asset.slots + slots))
        return asset

    def build_media(self, dry_run: bool) -> None:
        ids: dict[int, str] = {}
        for key, asset in sorted(self.assets.items()):
            if asset.mid in ids:
                raise SystemExit(f"media id collision: {key} vs {ids[asset.mid]}")
            ids[asset.mid] = key

            with Image.open(asset.path) as master:
                master.load()
                master = master.convert("RGB")
                asset.width, asset.height = master.size
                asset.blur_hash = blurhash_encode(master)

                for bucket in self.buckets:
                    sizes = slot_sizes(bucket)
                    for slot in asset.slots:
                        target = sizes[slot]
                        variant = render(master, target)
                        name = f"{variant.width}x{variant.height}.webp"
                        rel = f"{asset.mid}/{name}"
                        asset.urls.setdefault(bucket, {})[slot] = f"{self.media_base_url}/{rel}"

                        dest = self.out / "media" / rel
                        if rel in self.seen_variants or (not dry_run and dest.exists()):
                            self.reused += 1
                            continue
                        self.seen_variants.add(rel)
                        if dry_run:
                            self.rendered += 1
                            continue
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        quality = (
                            self.quality["download"]
                            if slot in DOWNLOAD_SLOTS
                            else self.quality["preview"]
                        )
                        variant.save(dest, "WEBP", quality=quality, method=6)
                        self.rendered += 1

    def media_maps(self) -> dict[str, dict]:
        maps: dict[str, dict] = {}
        for bucket in self.buckets:
            data: dict[str, dict[str, str]] = {}
            for asset in self.assets.values():
                data[str(asset.mid)] = asset.urls[bucket]
            maps[bucket] = {"version": 1, "data": data}
        return maps

    # -- content ----------------------------------------------------------- #

    def build_content(self) -> tuple[dict, dict]:
        artists, categories, folders, wallpapers = [], [], [], []
        metadata = []
        wallpaper_ids: dict[str, str] = {}  # "<collection>/<stem>" -> wallpaper id

        for spec in self.config.get("artists", []):
            artist_id = f"a~{spec['id']}"
            profile = self.add_asset(
                f"artist/{spec['id']}/profile", self.root / spec["profile"], PROFILE_SLOTS
            )
            social = spec.get("social") or {}
            unknown_social = set(social) - SOCIAL_KEYS
            if unknown_social:
                raise SystemExit(
                    f"artist {spec['id']}: unsupported social links {sorted(unknown_social)}; "
                    f"allowed: {sorted(SOCIAL_KEYS)}"
                )
            entry = {
                "id": artist_id,
                "label": spec["label"],
                "slugs": [spec["id"]],
                "categoryIds": [],
                "profileImage": {"id": profile.mid, "blurHash": profile.blur_hash},
                "socialLinks": social,
            }
            if spec.get("banner"):
                banner = self.add_asset(
                    f"artist/{spec['id']}/banner", self.root / spec["banner"], PROFILE_SLOTS
                )
                entry["featureBannerImage"] = {
                    "id": banner.mid,
                    "blurHash": banner.blur_hash,
                    "w": 0,
                    "h": 0,
                }
            artists.append(entry)

        artists_by_id = {a["id"]: a for a in artists}

        for spec in self.config.get("collections", []):
            artist_id = f"a~{spec['artist']}"
            if artist_id not in artists_by_id:
                raise SystemExit(f"collection {spec['id']}: unknown artist {spec['artist']}")
            category_id = f"{spec['artist']}~{spec['id']}"
            remix_ids = []

            for item in spec["wallpapers"]:
                path = self.root / item["file"]
                stem = Path(item["file"]).stem
                ref = f"{spec['id']}/{stem}"
                wallpaper_id = f"{artist_id}_{_digest('wallpaper', ref)[:8]}"
                wallpaper_ids[ref] = wallpaper_id

                download = self.add_asset(f"w/{ref}/download", path, DOWNLOAD_SLOTS)
                preview = self.add_asset(f"w/{ref}/preview", path, PREVIEW_SLOTS)

                wallpapers.append(
                    {
                        "id": wallpaper_id,
                        "label": item["label"],
                        "collectionLabel": spec["label"],
                        "type": item.get("type", "standard"),
                        "artistId": artist_id,
                        "categoryId": category_id,
                        "isSingle": bool(spec.get("singles", False)),
                        "isDark": bool(item.get("dark", False)),
                        "aie": False,
                        "free": bool(item.get("free", False)),
                        "slugs": [f"w/{short_slug(ref)}"],
                        "dlm": {
                            "hd": download.mid,
                            "sd": download.mid,
                            "w": download.width,
                            "h": download.height,
                        },
                        "previews": {
                            "standard": [
                                {"id": preview.mid, "blurHash": preview.blur_hash}
                            ]
                        },
                    }
                )
                metadata.append(
                    {
                        "remixId": wallpaper_id,
                        "artistNames": [spec["artist"]],
                        "title": item["label"].lower(),
                        "collectionTitle": spec["label"].lower(),
                        "styles": _terms(item.get("styles", [])),
                        "tags": _terms(item.get("tags", [])),
                        "colors": _terms(item.get("colors", [])),
                        "searchTerms": _terms(item.get("search_terms", [])),
                    }
                )
                remix_ids.append(wallpaper_id)

            preview_ref = spec.get("preview")
            preview_remix_id = (
                wallpaper_ids[f"{spec['id']}/{preview_ref}"] if preview_ref else remix_ids[0]
            )
            category = {
                "id": category_id,
                "label": spec["label"],
                "artistId": artist_id,
                "categoryType": spec.get("type", "Collection"),
                "previewRemixId": preview_remix_id,
                "remixIds": remix_ids,
                "slugs": [f"{spec['artist']}/{spec['id']}"],
            }
            if spec.get("product_ids"):
                category["purchasableProductIds"] = spec["product_ids"]
            categories.append(category)
            artists_by_id[artist_id]["categoryIds"].append(category_id)

        categories_by_id = {c["id"]: c for c in categories}

        for spec in self.config.get("folders", []):
            folder_id = f"f~{spec['id']}"
            profile = self.add_asset(
                f"folder/{spec['id']}/profile", self.root / spec["profile"], PROFILE_SLOTS
            )
            banner = self.add_asset(
                f"folder/{spec['id']}/banner", self.root / spec["banner"], PROFILE_SLOTS
            )
            remix_ids = []
            for ref in spec.get("wallpapers", []):
                if ref not in wallpaper_ids:
                    raise SystemExit(f"folder {spec['id']}: unknown wallpaper {ref}")
                remix_ids.append(wallpaper_ids[ref])
            collection_ids = []
            for ref in spec.get("collections", []):
                found = next((c for c in categories if c["id"].endswith(f"~{ref}")), None)
                if found is None:
                    raise SystemExit(f"folder {spec['id']}: unknown collection {ref}")
                collection_ids.append(found["id"])

            folders.append(
                {
                    "id": folder_id,
                    "title": spec["title"],
                    "titleTwoLines": spec.get("title_two_lines", spec["title"]),
                    "remixIds": remix_ids,
                    "collectionIds": collection_ids,
                    "profileImage": {"id": profile.mid, "blurHash": profile.blur_hash},
                    "featureBannerImage": {
                        "id": banner.mid,
                        "blurHash": banner.blur_hash,
                        "w": 0,
                        "h": 0,
                    },
                }
            )

        content = {
            "wallpapers": wallpapers,
            "categories": categories,
            "artists": artists,
            "folders": folders,
        }
        if not categories_by_id:
            raise SystemExit("no collections defined")
        return content, {"remixMetadata": metadata}

    def fill_media_fields(self, content: dict) -> None:
        """Blurhashes and dimensions are only known once the masters are opened,
        which happens after the content tree is assembled — patch them in here."""
        by_id = {str(a.mid): a for a in self.assets.values()}

        def walk(node) -> None:
            if isinstance(node, list):
                for item in node:
                    walk(item)
            elif isinstance(node, dict):
                asset = by_id.get(str(node.get("id"))) if "blurHash" in node else None
                if asset is not None:
                    node["blurHash"] = asset.blur_hash
                    if "w" in node:
                        node["w"], node["h"] = asset.width, asset.height
                for value in node.values():
                    walk(value)

        walk(content)
        for wallpaper in content["wallpapers"]:
            asset = by_id.get(str(wallpaper["dlm"]["hd"]))
            if asset:
                wallpaper["dlm"]["w"], wallpaper["dlm"]["h"] = asset.width, asset.height


def _terms(items) -> list[dict]:
    """Accepts ["dark", {"amoled": 0.9}] and emits [{"t": …, "r": …}]."""
    out = []
    for item in items:
        if isinstance(item, dict):
            for term, relevance in item.items():
                out.append({"t": str(term).lower(), "r": float(relevance)})
        else:
            out.append({"t": str(item).lower(), "r": 1.0})
    return out


# --------------------------------------------------------------------------- #
# Entry point
# --------------------------------------------------------------------------- #


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("config", type=Path, help="path to content.yaml")
    parser.add_argument("-o", "--out", type=Path, default=Path("build/content"))
    parser.add_argument("--clean", action="store_true", help="wipe the output folder first")
    parser.add_argument(
        "--dry-run", action="store_true", help="validate and report, write nothing"
    )
    args = parser.parse_args()

    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    root = args.config.parent
    version = str(config["version"])
    suffix = str(config.get("content_suffix", "1a"))
    quality = {
        "preview": int(config.get("preview_quality", 85)),
        "download": int(config.get("download_quality", 90)),
    }

    if args.clean and args.out.exists() and not args.dry_run:
        shutil.rmtree(args.out)

    builder = Builder(config, root, args.out, quality)
    content, metadata = builder.build_content()
    builder.build_media(args.dry_run)
    builder.fill_media_fields(content)

    api_root = f"api/{version}"
    spec = {
        "content": f"{api_root}/content-{suffix}",
        "search": f"{api_root}/content-metadata-{suffix}",
        "media": {
            "root": f"{api_root}/media-{suffix}",
            "p": builder.platforms,
            "b": builder.buckets,
        },
    }

    files: dict[str, object] = {
        "spec.json": spec,
        f"content-{suffix}": content,
        f"content-metadata-{suffix}": metadata,
    }
    for bucket, media_map in builder.media_maps().items():
        for platform in builder.platforms:
            files[f"media-{suffix}-{platform}-{bucket}"] = media_map

    if not args.dry_run:
        api_dir = args.out / "api" / version
        api_dir.mkdir(parents=True, exist_ok=True)
        for name, payload in files.items():
            (api_dir / name).write_text(
                json.dumps(payload, separators=(",", ":")), encoding="utf-8"
            )
        (api_dir / "key1").write_text(str(config["encryption_key"]), encoding="utf-8")

    print(f"wallpapers   {len(content['wallpapers'])}")
    print(f"collections  {len(content['categories'])}")
    print(f"artists      {len(content['artists'])}")
    print(f"folders      {len(content['folders'])}")
    print(f"media ids    {len(builder.assets)}")
    print(f"variants     {builder.rendered} written, {builder.reused} deduped")
    print(f"api files    {len(files) + 1} in {args.out / 'api' / version}")
    if args.dry_run:
        print("(dry run — nothing written)")


if __name__ == "__main__":
    main()
