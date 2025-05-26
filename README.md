# Pty4J - Pseudo terminal(PTY) implementation in Java.

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)


This is a Java implementation of a PTY. It's written in JNA with native code to fork the JVM process.

It is based on two projects that provide the same functionality: [JPty](https://github.com/jawi/JPty)
and [elt](https://code.google.com/p/elt/):

* While JPty is pretty good and written using only JNA it suffers from a 
  hang on Mac OS X when Java is under debug (https://github.com/jawi/JPty/issues/2), because
  fork doesn't work well in java.

* elt works ok, but it has mostly native code(using JNI).

So this one is a mix of the other two: A port of elt to JNA in the style it is made in JPty with only
fork and process exec written in native code.

## Dependencies

Windows PTY implementation used here is [the magnificent WinPty library written by Ryan Prichard](https://github.com/rprichard/winpty).

## Adding Pty4J to your build

The releases are published to Maven Central as [org.jetbrains.pty4j:pty4j](https://search.maven.org/artifact/org.jetbrains.pty4j/pty4j).

### Maven

```xml
<dependency>
  <groupId>org.jetbrains.pty4j</groupId>
  <artifactId>pty4j</artifactId>
  <version>0.13.4</version>
</dependency>
```

### Gradle

```gradle
dependencies {
  implementation 'org.jetbrains.pty4j:pty4j:0.13.4'
}
```

## Usage

Using this library is relatively easy, and currently supports Linux, OSX, Windows and FreeBSD:

```java
String[] cmd = { "/bin/sh", "-li" };
Map<String, String> env = new HashMap<>(System.getenv());
if (!env.containsKey("TERM")) env.put("TERM", "xterm-256color");
PtyProcess process = new PtyProcessBuilder().setCommand(cmd).setEnvironment(env).start();

OutputStream os = process.getOutputStream();
InputStream is = process.getInputStream();

// ... work with the streams ...

// wait until the PTY child process is terminated
int result = process.waitFor();
```

The _... work with the streams ..._ part could be e.g. connecting to a Terminal Emulator GUI,
or perhaps WebSocket-ing them to & fro something like [Xterm.js](https://xtermjs.org).
For a "local shell overlay" sort of usage ([#170](https://github.com/JetBrains/pty4j/issues/170)),
you need to combine this with [JLine's Terminal](https://jline.org/docs/terminal) to enter raw mode, and using a
[🏞️ _"Stream Pumper"_ ⛽](https://github.com/enola-dev/enola/blob/d6f6bf37f6efc70ad50bd0fb60b41ec6c1504d61/java/dev/enola/common/exec/pty/StreamPumper.java#L39) like so:

```java
try (Terminal terminal = TerminalBuilder.builder().build()) {
    terminal.enterRawMode(); // !!

    String[] cmd = {"/bin/sh", "-li"};
    Map<String, String> env = new HashMap<>(System.getenv());
    if (!env.containsKey("TERM")) env.put("TERM", "xterm-256color");
    PtyProcess process = new PtyProcessBuilder().setCommand(cmd).setEnvironment(env).start();

    new StreamPumper("In", terminal.reader(), process.getOutputStream());
    new StreamPumper("Out", process.getInputStream(), terminal.writer());

    return process.waitFor();
}
```

## License

The code in this library is licensed under Eclipse Public License, version 
1.0 and can be found online at: <http://www.eclipse.org/legal/epl-v10.html>.

