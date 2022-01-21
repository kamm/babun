set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

mkdir "$homedir/.vim"
tar -C "$homedir/.vim" -xf "$babun_plugins/ack/src/ack-vim/ack.tar" 
