#!/bin/sh

# Write abcdefz to stdout and ABCDEFZ to stderr, pausing one second
# between each three characters.

# Use /bin/echo instead of echo, because on OS X, calling echo from a /bin/sh
# shell script doesn't handle the -n argument, which is intended to suppress
# the newline.

/bin/echo -n abc
/bin/echo -n ABC>&2
sleep 1

/bin/echo -n def
/bin/echo -n DEF>&2
sleep 1

/bin/echo z
/bin/echo Z>&2
