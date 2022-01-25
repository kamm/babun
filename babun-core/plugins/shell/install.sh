#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

src="$babun_source/babun-core/plugins/shell/src/"
dest="$babun/home/shell/"

/bin/cp -rf /etc/minttyrc /etc/minttyrc.old  2>/dev/null || true
/bin/cp -rf $src/minttyrc /etc/minttyrc

/bin/cp -rf /etc/zprofile /etc/zprofile.old 2>/dev/null || true
/bin/cp -rf $src/zprofile /etc/zprofile

mkdir -p "$dest"
 
