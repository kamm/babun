set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

#start with installing sdkman
curl -s "https://get.sdkman.io" | bash
