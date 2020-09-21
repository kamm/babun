set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

src="$babun_plugins/dotfiles/src"

/bin/cp -rf "$src/minttyrc" "$homedir/.minttyrc"
mkdir -p "$homedir/.mintty/emojis"
tar -xf "$src/apple.tar" -C "$homedir/.mintty/emojis"