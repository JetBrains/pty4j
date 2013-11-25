package com.pty4j;

import com.pty4j.unix.Pty;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.util.PtyUtil;
import com.pty4j.windows.WinPtyProcess;
import com.sun.jna.Platform;

import java.io.IOException;
import java.util.Map;

/**
 * Process with pseudo-terminal(PTY).
 * On Unix systems the process is created with real pseudo-terminal (PTY).
 * <p/>
 * On Windows, where there is no such entity like TTY, we make an emulation: invisible console window
 * is created and all character updates are sent to output stream and all character input is requested from
 * input stream.
 * <p/>
 * Note that on Unix to be sure that no file descriptors are left unclosed after process termination
 * one of two things should be accomplished:
 * 1) Streams returned by getInputStream() and getOutputStream() method should be acquired and closed
 * 2) Method destroy() should be invoked even after the process termination
 * <p/>
 * See {@link UnixPtyProcess#closeUnusedStreams()} method javadoc for details.
 * <p/>
 * This behavior may change in future versions.
 *
 * @author traff
 */
public abstract class PtyProcess extends Process {
  public abstract boolean isRunning();

  public abstract void setWinSize(WinSize winSize);

  public abstract WinSize getWinSize() throws IOException;

  public static PtyProcess exec(String[] command) throws IOException {
    return exec(command, (String[])null);
  }

  public static PtyProcess exec(String[] command, String[] environment) throws IOException {
    return exec(command, environment, null, false);
  }

  public static PtyProcess exec(String[] command, Map<String, String> environment, String workingDirectory) throws IOException {
    return exec(command, PtyUtil.toStringArray(environment), workingDirectory, false);
  }

  public static PtyProcess exec(String[] command, Map<String, String> environment, String workingDirectory, boolean console)
    throws IOException {
    return exec(command, PtyUtil.toStringArray(environment), workingDirectory, console);
  }

  public static PtyProcess exec(String[] command, String[] environment, String workingDirectory, boolean console) throws IOException {
    if (Platform.isWindows()) {
      return new WinPtyProcess(command, environment, workingDirectory);
    }
    return new UnixPtyProcess(command, environment, workingDirectory, new Pty(console));
  }
}
