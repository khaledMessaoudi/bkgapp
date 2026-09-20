#!/usr/bin/env python3
"""Encrypt a built api/<version>/ folder the way the app expects to read it.

    python tools/encrypt_api.py build/content/api/v0

Writes `<name>.data` next to each file and leaves `key1` in plaintext, matching
service/service-export-remoteapi. See docs/05-backend-schema.md §3.2:

  AES-256-GCM, key = UTF-8 bytes of the key1 contents padded/truncated to 32 bytes,
  the 12-byte constant below passed as associated data (NOT as the nonce), and a
  random nonce per file prefixed to the ciphertext.

The nonce-prefix layout is cryptography-kotlin's; it is pinned by
shared/core/security/src/commonTest/.../PythonCiphertextCompatTest.kt, which
decrypts a fixture produced by this file.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.exit("cryptography is required: pip install -r tools/requirements.txt")

# RemoteApiEncryptionConfigDefault.initializationVector — used as associated data.
ASSOCIATED_DATA = bytes(
    b & 0xFF for b in [47, -93 & 0xFF, 98, 49, 107, 77, -74 & 0xFF, 68, -17 & 0xFF, -105 & 0xFF, 89, 86]
)
NONCE_BYTES = 12
KEY_FILENAME = "key1"
ENCRYPTED_SUFFIX = ".data"


def derive_key(key_text: str) -> bytes:
    """`key.toByteArray().copyOf(32)` — zero-padded or truncated to 32 bytes."""
    raw = key_text.encode("utf-8")
    return raw[:32].ljust(32, b"\x00")


def encrypt(data: bytes, key: bytes) -> bytes:
    nonce = os.urandom(NONCE_BYTES)
    return nonce + AESGCM(key).encrypt(nonce, data, ASSOCIATED_DATA)


def decrypt(blob: bytes, key: bytes) -> bytes:
    nonce, body = blob[:NONCE_BYTES], blob[NONCE_BYTES:]
    return AESGCM(key).decrypt(nonce, body, ASSOCIATED_DATA)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("api_dir", type=Path, help="the api/<version> folder to encrypt")
    parser.add_argument(
        "--key-file", default=KEY_FILENAME, help=f"key file name (default {KEY_FILENAME})"
    )
    parser.add_argument(
        "--fixture",
        type=Path,
        help="also write a base64 round-trip fixture here, for the Kotlin compat test",
    )
    args = parser.parse_args()

    api_dir: Path = args.api_dir
    key_path = api_dir / args.key_file
    if not key_path.is_file():
        print(f"no {args.key_file} in {api_dir} — the app fetches the key from there")
        return 2
    key = derive_key(key_path.read_text(encoding="utf-8").strip())

    count = 0
    for path in sorted(api_dir.iterdir()):
        if not path.is_file() or path.name == args.key_file:
            continue
        if path.name.endswith(ENCRYPTED_SUFFIX):
            continue
        plaintext = path.read_bytes()
        blob = encrypt(plaintext, key)
        if decrypt(blob, key) != plaintext:  # same check the Kotlin exporter makes
            print(f"round-trip failed for {path.name}")
            return 1
        path.with_name(path.name + ENCRYPTED_SUFFIX).write_bytes(blob)
        count += 1

    print(f"encrypted {count} files in {api_dir} (key1 left in plaintext)")

    if args.fixture:
        import base64
        import json

        sample = b"bkgapp python ciphertext fixture"
        args.fixture.write_text(
            json.dumps(
                {
                    "key": key_path.read_text(encoding="utf-8").strip(),
                    "plaintext": sample.decode(),
                    "ciphertextBase64": base64.b64encode(encrypt(sample, key)).decode(),
                },
                indent=2,
            ),
            encoding="utf-8",
        )
        print(f"wrote compat fixture to {args.fixture}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
