ok="V"
fail="X"
return_code1="$ok %{$fg[blue]%}%?"
return_code2="$fail %{$fg[red]%}%?"
return_code="%(?.$return_code1.$return_code2)"
gitbranch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

PROMPT="%{$fg[green]%}$HOST%{$fg[blue]%}{%c} {$reset_color%}"

PATH=$PATH:/sbin:/usr/sbin
EDITOR=vim