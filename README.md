# Pty4J - Pseudo terminal(PTY) implementation in Java. [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.pty4j/pty4j/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.pty4j/pty4j)

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)


This is a Java implementation of PTY. Written in JNA with native code to make fork of a process.

It is based on two projects that provide the same functionality: [JPty](https://github.com/jawi/JPty)
and [elt](https://code.google.com/p/elt/). 

While JPty is pretty good and written using only JNA it suffers from a 
hang on Mac OS X when Java is under debug (https://github.com/jawi/JPty/issues/2), because
fork doesn't work well in java.

elt works ok, but it has mostly native code(using JNI).

So this one is a mix of the other two: a port of elt to JNA in the style it is made in JPty with only
fork and process exec written in native.

Also pty4j implements java interface for pty for windows, using [WinPty](https://github.com/rprichard/winpty) library.

## Dependencies

Windows pty implementation used here is the magnificent WinPty library written by Ryan Prichard: https://github.com/rprichard/winpty

## Adding Pty4J to your build

The releases are published to Maven Central: [org.jetbrains.pty4j:pty4j](https://search.maven.org/artifact/org.jetbrains.pty4j/pty4j).

### Maven

```
<dependency>
  <groupId>org.jetbrains.pty4j</groupId>
  <artifactId>pty4j</artifactId>
  <version>0.13.10</version>
</dependency>
```

### Gradle

```
dependencies {
  implementation 'org.jetbrains.pty4j:pty4j:0.13.10'
}
```

## Usage

Using this library is relatively easy:

    String[] cmd = { "/bin/sh", "-l" };
    Map<String, String> env = new HashMap<>(System.getenv());
    if (!env.containsKey("TERM")) env.put("TERM", "xterm");
    PtyProcess process = new PtyProcessBuilder().setCommand(cmd).setEnvironment(env).start();

    OutputStream os = process.getOutputStream();
    InputStream is = process.getInputStream();
    
    // ... work with the streams ...
    
    // wait until the PTY child process is terminated
    int result = process.waitFor();

The operating systems currently supported by pty4j are: Linux, OSX, Windows and FreeBSD.

## License

The code in this library is licensed under Eclipse Public License, version 
1.0 and can be found online at: <http://www.eclipse.org/legal/epl-v10.html>.

