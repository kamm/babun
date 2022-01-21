#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

src="$babun_source/babun-core/plugins/xserver/src/."
dest="$babun/home/xserver"

echo "export DISPLAY=:0" >> "$babun/home/.zshrc"

/bin/cp -rf "$src/" "$dest"
