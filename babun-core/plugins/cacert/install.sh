#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

curl -k https://curl.haxx.se/ca/cacert.pem > /etc/pki/tls/certs/ca-bundle.crt
