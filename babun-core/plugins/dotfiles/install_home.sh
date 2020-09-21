set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

#echo "ForwardX11Trusted yes" >> "$homedir/.ssh/config"


src="$babun_source/babun-core/plugins/dotfiles/src"
/bin/cp -rf "$src/minttyrc" "$home/.minttyrc"
mkdir -p "$home/.mintty/emojis"
tar -xf "$src/apple.tar" -C "$home/.mintty/emojis"