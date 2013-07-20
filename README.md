# Pty4J - Pseudo terminal(PTY) implementation in Java.

This is a pure Java implementation of PTY. No native code is used - only JNA.

It is based on two projects that provide the same functionality: JPty (https://github.com/jawi/JPty)
and elt (https://code.google.com/p/elt/). 

While JPty is pretty good and written using only JNA it suffers from a 
hang on Mac OS X when Java is under debug (https://github.com/jawi/JPty/issues/2).

PTY from "elt" works ok, but it has a ton of native code(using JNI) and that is not cool.

So this one is a mix of the other two: a port of elt to JNA in the style it is made in JPty.

## Dependencies

This library depends on JTermios, part of the PureJavacomm library found at
<https://github.com/nyholku/purejavacomm>. A binary release of this library,
along with its dependency JNA, is made part of this repository, and can be 
found in the lib-directory.

## Usage

Using this library is relatively easy:

    // The command to run in a PTY...
    String[] cmd = { "/bin/sh", "-l" };
    // The initial environment to pass to the PTY child process...
    String[] env = { "TERM=xterm" };

    PtyProcess pty = Pty.exec(cmd, env);

    OutputStream os = pty.getOutputStream();
    InputStream is = pty.getInputStream();
    
    // ... work with the streams ...
    
    // wait until the PTY child process terminates...
    int result = pty.waitFor();
    
    // free up resources.
    pty.close();

The operating systems currently supported by JPty are: FreeBSD, Linux, OSX and
Solaris.  

**Note that this library is not yet fully tested on all platforms.**

## Changes

    0.1 | 20-07-2013 | Initial version.

## License

The code in this library is licensed under Eclipse Public License, version 
1.0 and can be found online at: <http://www.eclipse.org/legal/epl-v10.html>.

