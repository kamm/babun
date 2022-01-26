#!/bin/bash
set -e -f -o pipefail

# init babun.instance before anything other initializes
/bin/cp -rf /usr/local/etc/babun/source/babun-core/plugins/core/src/babun.instance /usr/local/etc

if [ ! "x${1}" = "x" ]; then
    echo "${1}" > /usr/local/etc/cygwinBitVersion
fi
