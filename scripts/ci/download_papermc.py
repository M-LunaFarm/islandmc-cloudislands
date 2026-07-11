#!/usr/bin/env python3
import argparse
from pathlib import Path

from papermc_smoke import download, stable_download


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", choices=["paper", "velocity"], required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    url, checksum, size = stable_download(args.project, args.version)
    output = Path(args.output)
    download(url, output, checksum, size)
    print(f"downloaded {args.project} {args.version} to {output}")


if __name__ == "__main__":
    main()
