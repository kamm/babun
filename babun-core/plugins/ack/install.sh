#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"


curl https://beyondgrep.com/ack-v3.4.0 > /usr/local/bin/ack
chmod 755 /usr/local/bin/ack
