#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

mkdir -p /usr/ssl/certs
cd /usr/ssl/certs
curl https://curl.haxx.se/ca/cacert.pem | awk 'split_after==1{n++;split_after=0} /-----END CERTIFICATE-----/ {split_after=1} {print > "cert" n ".pem"}'
#c_rehash

#possible problem with c_rehash in newest cygwin
#for file in *.pem; do ln -s $file `openssl x509 -hash -noout -in $file`.0; done