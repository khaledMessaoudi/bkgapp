#!/usr/bin/env python3
"""Upload an encrypted api/<version>/ folder into the Firebase Storage emulator.

    python tools/upload_emulator.py build/content/api/v0

Uploads every `*.data` file plus the plaintext `key1` to `api/<version>/` in the
emulator's bucket — the same set service-export-remoteapi would push to the real
bucket. Start the emulator first:

    cd firebase-backend && firebase emulators:start --only auth,storage

See tools/README.md for the whole local loop (the app needs
RUN_FIREBASE_ON_LOCAL_EMULATORS = true and a media server on 10.0.2.2).
"""

from __future__ import annotations

import argparse
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_BUCKET = "panels-oss.firebasestorage.app"  # google-services.json storage_bucket
DEFAULT_HOST = "http://127.0.0.1:9199"


def upload(host: str, bucket: str, remote_path: str, data: bytes) -> None:
    name = urllib.parse.quote(remote_path, safe="")
    url = f"{host}/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}"
    request = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/octet-stream"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status not in (200, 201):
            raise RuntimeError(f"{remote_path}: HTTP {response.status}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("api_dir", type=Path, help="the encrypted api/<version> folder")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--bucket", default=DEFAULT_BUCKET)
    parser.add_argument(
        "--prefix",
        help="storage path prefix (default api/<folder name>/, which must match "
        "RemoteEndpointsSpecs.Staging.base)",
    )
    args = parser.parse_args()

    api_dir: Path = args.api_dir
    if not api_dir.is_dir():
        print(f"not a directory: {api_dir}")
        return 2
    prefix = (args.prefix or f"api/{api_dir.name}").strip("/")

    files = [p for p in sorted(api_dir.iterdir()) if p.is_file()]
    payload = [p for p in files if p.name.endswith(".data") or p.name == "key1"]
    if not payload:
        print(f"nothing to upload in {api_dir} — run tools/encrypt_api.py first")
        return 2

    for path in payload:
        remote = f"{prefix}/{path.name}"
        try:
            upload(args.host, args.bucket, remote, path.read_bytes())
        except urllib.error.URLError as error:
            print(f"failed uploading {remote}: {error}")
            print(f"is the storage emulator running on {args.host}?")
            return 1
        print(f"  {remote}  ({path.stat().st_size:,} bytes)")

    print(f"uploaded {len(payload)} files to {args.bucket}/{prefix}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
