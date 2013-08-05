package com.pty4j;

import com.pty4j.unix.Pty;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.WinPtyProcess;
import com.sun.jna.Platform;

import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public abstract class PtyProcess extends Process{
  public abstract boolean isRunning();

  public abstract void setWinSize(WinSize winSize);

  public abstract  WinSize getWinSize() throws IOException;
  
  public static PtyProcess exec(String[] command) throws IOException {
    return exec(command, null, null);
  }

  public static PtyProcess exec(String[] command, String[] environment) throws IOException {
    return exec(command, environment, null);
  }

  public static PtyProcess exec(String[] command, String[] environment, String workingDirectory) throws IOException {
    if (Platform.isWindows()) {
      return new WinPtyProcess(command, environment, workingDirectory);
    }
    return new UnixPtyProcess(command, environment, workingDirectory, new Pty());
  }
}
