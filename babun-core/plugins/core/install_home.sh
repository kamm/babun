#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"
source "$babun_tools/git.sh"

src="$babun/home/core"
dest="$homedir"

if [ ! -f "$dest/.babunrc" ]; then
	/bin/cp -rf "$src/.babunrc" "$dest/.babunrc"
	branch='release'
	
	echo "" >> "$dest/.babunrc"
	echo "export BABUN_BRANCH=$branch" >> "$dest/.babunrc"
fi

