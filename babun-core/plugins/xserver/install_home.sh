#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

src="$babun/home/xserver"

# if XWinrc not installed
if [ ! -d "$homedir/.XWinrc" ]; then        
    # installing XWinrc
    /bin/cp -rf "$src/.XWinrc" "$homedir/.XWinrc" 
    dos2unix "$homedir/.XWinrc"     
fi

# if Xresources not installed
if [ ! -d "$homedir/.Xresources" ]; then        
    # installing Xresources
    /bin/cp -rf "$src/.Xresources" "$homedir/.Xresources" 
    dos2unix "$homedir/.Xresources" 
    
fi
mkdir "$homedir/.ssh"
echo "ForwardX11Trusted yes" >> "$homedir/.ssh/config"

