#!/bin/bash

script=$(cygpath -m -a build.groovy | sed "s/\//\\\\/g")
time cygstart --action=runas -w cmd /c groovy $script clean
time cygstart --action=runas -w cmd /c groovy $script package

