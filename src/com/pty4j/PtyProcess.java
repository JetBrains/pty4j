package com.pty4j;

import com.google.common.base.Ascii;
import com.pty4j.unix.Pty;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.WinPtyProcess;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Process with pseudo-terminal(PTY).
 * On Unix systems the process is created with real pseudo-terminal (PTY).
 * <p/>
 * On Windows, ConPTY is used. If unavailable, WinPty is used.
 * <p/>
 * Note that on Unix to be sure that no file descriptors are left unclosed after process termination
 * one of two things should be accomplished:
 * 1) Streams returned by getInputStream() and getOutputStream() method should be acquired and closed
 * 2) Method destroy() should be invoked even after the process termination
 * <p/>
 *
 * @author traff
 */
public abstract class PtyProcess extends Process {
  public abstract void setWinSize(WinSize winSize);

  public abstract @NotNull WinSize getWinSize() throws IOException;

  public long pid() {
    return getPid();
  }

  public abstract int getPid();

  /**
   * @return byte to send to process's input on Enter key pressed
   */
  public byte getEnterKeyCode() {
    return Ascii.CR;
  }

  @SuppressWarnings("unused") // used in IntelliJ
  public boolean isConsoleMode() {
    return false;
  }

  /**
   * @deprecated use {@link #isAlive()} instead
   */
  @Deprecated
  public abstract boolean isRunning();

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command) throws IOException {
    return exec(command, (Map<String, String>)null);
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, Map<String, String> environment) throws IOException {
    return exec(command, environment, null, false, false, null);
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, Map<String, String> environment, String workingDirectory) throws IOException {
    return exec(command, environment, workingDirectory, false, false, null);
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, String[] environment) throws IOException {
    return exec(command, environment, null, false);
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, String[] environment, String workingDirectory, boolean console) throws IOException {
    if (Platform.isWindows()) {
      return new WinPtyProcess(command, environment, workingDirectory, console);
    }
    return new UnixPtyProcess(command, environment, workingDirectory, new Pty(console), console ? new Pty() : null, console);
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, Map<String, String> environment, String workingDirectory, boolean console)
    throws IOException {
    return exec(command, environment, workingDirectory, console, false, null);
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, Map<String, String> environment, String workingDirectory, boolean console, boolean cygwin,
                                File logFile) throws IOException {
    PtyProcessBuilder builder = new PtyProcessBuilder(command)
        .setEnvironment(environment)
        .setDirectory(workingDirectory)
        .setConsole(console)
        .setCygwin(cygwin)
        .setLogFile(logFile);
    return builder.start();
  }
}
