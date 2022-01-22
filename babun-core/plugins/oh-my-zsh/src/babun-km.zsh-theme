function get_git_id {
	(git id --simple 2>/dev/null) || ""
}

local return_code="%(?.%{$fg[green]%}😎%{$reset_color%}.%{$fg[red]%}💩%{$reset_color%})"
local git_id="$( (git id --simple 2>/dev/null) || echo "" )"

PROMPT='%{$fg[green]%}$HOST%{$fg[blue]%}{%c}\
%{$fg[green]%}$(git rev-parse --abbrev-ref HEAD 2> /dev/null || echo "")%{$reset_color%}\
%{$fg[yellow]%}${git_id}%{$fg[red]%}» %{$reset_color%}'

PROMPT2='%{$fg[red]%}\ %{$reset_color%}'

export WORKSPACE=/c/workspace

alias rcmda='cygstart --action=runas cmd'
alias rcmdaw='cygstart --action=runas -w cmd'
alias rcmdw='cygstart -w cmd'
alias rcmd='cygstart cmd'

