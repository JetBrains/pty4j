package com.pty4j;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Process with pseudo-terminal(PTY).
 * On Unix systems the process is created with real pseudo-terminal (PTY).
 * <p>
 * On Windows, ConPTY is used with fallback to WinPty.
 * <p>
 * Note that on Unix to be sure that no file descriptors are left unclosed after process termination
 * one of two things should be accomplished:
 * 1) Streams returned by getInputStream() and getOutputStream() method should be acquired and closed
 * 2) Method destroy() should be invoked even after the process termination
 *
 * @author traff
 */
public abstract class PtyProcess extends Process {
  /**
   * Sets the terminal grid and, on Unix, its text-area pixel dimensions.
   * Windows backends apply and cache only columns and rows; supplied pixel dimensions are ignored.
   * Zero pixel dimensions mean unknown and clear previous pixel values on Unix.
   *
   * @param winSize new terminal dimensions
   */
  public abstract void setWinSize(@NotNull WinSize winSize);

  /**
   * Reads terminal dimensions from the backend.
   *
   * @return grid size and available text-area pixel dimensions; Windows backends report zero pixel dimensions,
   *         even when nonzero pixel dimensions were supplied to {@link #setWinSize(WinSize)}
   * @throws IOException if the backend cannot read the terminal dimensions
   */
  public abstract @NotNull WinSize getWinSize() throws IOException;

  /**
   * @return byte to send to process's input on Enter key pressed
   */
  public byte getEnterKeyCode() {
    return '\r';
  }

  @SuppressWarnings("unused") // used in IntelliJ
  public boolean isConsoleMode() {
    return false;
  }

  /**
   * @deprecated use {@link #isAlive()} instead
   */
  @Deprecated
  public boolean isRunning() {
    return isAlive();
  }

  /** @deprecated use {@link PtyProcessBuilder} instead */
  @Deprecated
  public static PtyProcess exec(String[] command, Map<String, String> environment, String workingDirectory) throws IOException {
    return exec(command, environment, workingDirectory, false, false, null);
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
