#!/bin/bash

script=$(cygpath -m -a build.groovy | sed "s/\//\\\\/g")
cygstart --action=runas -w cmd /c groovy $script partclean
cygstart --action=runas -w cmd /c groovy $script package

