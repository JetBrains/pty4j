# Pty4J - Pseudo terminal(PTY) implementation in Java.

This is a pure Java implementation of PTY. Written in JNA with native code to make fork of a process.

It is based on two projects that provide the same functionality: JPty (https://github.com/jawi/JPty)
and elt (https://code.google.com/p/elt/). 

While JPty is pretty good and written using only JNA it suffers from a 
hang on Mac OS X when Java is under debug (https://github.com/jawi/JPty/issues/2), because
fork doesn't work well in java.

elt works ok, but it has mostly native code(using JNI).

So this one is a mix of the other two: a port of elt to JNA in the style it is made in JPty with only
fork and process exec written in native.

Also pty4j implements java interface for pty for windows, using win-pty library.

## Dependencies

This library depends on JTermios, part of the PureJavacomm library found at
<https://github.com/nyholku/purejavacomm>. A binary release of this library,
along with its dependency JNA, is made part of this repository, and can be 
found in the lib-directory.

Windows pty implementation used here is the fork of WinPty by Ryan Prichard (https://github.com/rprichard/winpty)
located here https://github.com/traff/winpty

## Usage

Using this library is relatively easy:

    // The command to run in a PTY...
    String[] cmd = { "/bin/sh", "-l" };
    // The initial environment to pass to the PTY child process...
    String[] env = { "TERM=xterm" };

    PtyProcess pty = PtyProcess.exec(cmd, env);

    OutputStream os = pty.getOutputStream();
    InputStream is = pty.getInputStream();
    
    // ... work with the streams ...
    
    // wait until the PTY child process terminates...
    int result = pty.waitFor();
    
    // free up resources.
    pty.close();

The operating systems currently supported by pty4j are: Linux, OSX and
Windows.  

**Note that this library is not yet fully tested on all platforms.**

## Changes

    0.3 | 16-08-2013 | Native code for fork and process exec.
    0.2 | 03-08-2013 | Linux and Windows supported.
    0.1 | 20-07-2013 | Initial version.

## License

The code in this library is licensed under Eclipse Public License, version 
1.0 and can be found online at: <http://www.eclipse.org/legal/epl-v10.html>.

