#!/bin/bash
source "/usr/local/etc/babun.instance"

if ! [[ "$DISABLE_CHECK_ON_STARTUP" == "true" ]]; then
	source "$babun_tools/check.sh"
	guarded_babun_check
	trap - DEBUG ERR
	trap	
fi