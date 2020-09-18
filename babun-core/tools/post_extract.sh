#!/bin/bash
set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

# fix symlinks on local instance
/bin/dos2unix.exe /etc/postinstall/symlinks_repair.sh >/dev/null 2>&1

/bin/chmod 755 /etc/postinstall/symlinks_repair.sh >/dev/null 2>&1
/etc/postinstall/symlinks_repair.sh >/dev/null 2>&1
/bin/mv.exe /etc/postinstall/symlinks_repair.sh /etc/postinstall/symlinks_repair.sh.done >/dev/null 2>&1

# regenerate user/group information
/bin/rm -rf /home

echo "[babun] HOME set to $HOME"

if [[ ! "$HOME" == /cygdrive* ]]; then
	echo "[babun] Running mkpasswd for CYGWIN home"
	# regenerate users' info
	/bin/mkpasswd.exe -l -c > /etc/passwd	2>/dev/null

	# remove spaces in username and user home folder (sic!)
	# xuser=${USERNAME//[[:space:]]}
	# xhome="\/home\/"
	# /bin/sed -e "s/$USERNAME/$xuser/" -e "s/$xhome$USERNAME/$xhome$xuser/" -i /etc/passwd
else
	echo "[babun] Running mkpasswd for WINDOWS home"
	# regenerate users' info using windows paths
	/bin/mkpasswd -l -c -p"$(/bin/cygpath -H)" > /etc/passwd 2>/dev/null
fi
/bin/mkgroup -l -c > /etc/group 2>/dev/null

# fix file permissions in /usr/local
/bin/chmod 755 -R /usr/local
/bin/chmod u+rwx -R /etc
