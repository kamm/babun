set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

src="$babun_plugins/dotfiles/src"

mkdir -p "$homedir/.local/bin"
mkdir -p "$homedir/.cache/vim/undodir"
mkdir -p "$homedir/.mintty/emojis"
mkdir -p "$homedir/.config/tmux"

tar -xf "$src/apple.tar" -C "$homedir/.mintty/emojis"

/bin/cp -rf "$src/minttyrc" "$homedir/.minttyrc"
/bin/cp -rf "$src/vimrc" "$homedir/.vimrc"
/bin/cp "$src/battery" "$homedir/.local/bin"
/bin/cp "$src/temperature" "$homedir/.local/bin"
/bin/cp "$src/tm" "$homedir/.local/bin"
/bin/cp "$src/tp" "$homedir/.local/bin"
/bin/cp "$src/tmux/tmux.conf.symlink" "$homedir/.config/tmux"
/bin/cp "$src/tmux/base16.sh" "$homedir/.config/tmux"

ln -s "$homedir/.config/tmux/tmux.conf.symlink" "$homedir/.tmux.conf"

chmod ugo+x "$homedir/.local/bin/battery"
chmod ugo+x "$homedir/.local/bin/temperature"
chmod ugo+x "$homedir/.local/bin/tp"
chmod ugo+x "$homedir/.local/bin/tm"
