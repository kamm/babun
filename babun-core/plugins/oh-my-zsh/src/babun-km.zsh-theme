function ret {
        ret="$1"
        if [ $ret -eq 0 ]; then
                echo "V"
        else
                echo "X"
        fi
}

function get_git_id {
	(git id --simple 2>/dev/null) || ""
}

local return_code="%(?.%{$fg[green]%}V%{$reset_color%}.%{$fg[red]%}X%{$reset_color%})"
local git_id="$( (git id --simple 2>/dev/null) || echo "" )"
function parse_git_dirty {
        gitst=$(git status --porcelain 2>/dev/null | wc -l)
        gitad=$(git diff --cached 2>/dev/null| wc -l)

        if [ ${gitst} -eq 0 ]; then
                echo ""
        else
                if [ ${gitad} -ne 0 ]; then
                        echo "%{$fg[blue]%}♦%{$reset_color%} " #λ"
                else
                        echo "%{$fg[red]%}♦%{$reset_color%} " #λ"
                fi
        fi
}
PROMPT='%{$fg[green]%}$HOST%{$fg[blue]%}{%c}\
%{$fg[green]%}$(git rev-parse --abbrev-ref HEAD 2> /dev/null || echo "")%{$reset_color%} \
%{$fg[yellow]%}${git_id}$(parse_git_dirty)%{$fg[red]%}%(!.#.»)%{$reset_color%} '

PROMPT2='%{$fg[red]%}\ %{$reset_color%}'

RPS1='%{$fg[blue]%}%~%{$reset_color%} ${return_code}'

ZSH_THEME_GIT_PROMPT_PREFIX="%{$reset_color%}:: %{$fg[yellow]%}("
ZSH_THEME_GIT_PROMPT_SUFFIX=")%{$reset_color%} "
ZSH_THEME_GIT_PROMPT_CLEAN=""
ZSH_THEME_GIT_PROMPT_DIRTY="%{$fg[red]%}*%{$fg[yellow]%}"
