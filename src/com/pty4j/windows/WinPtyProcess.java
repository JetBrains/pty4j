package com.pty4j.windows;

import com.google.common.base.Joiner;
import com.pty4j.PtyException;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author traff
 */
public class WinPtyProcess extends PtyProcess {
  private final WinPty myWinPty;
  private final WinPTYInputStream myInputStream;
  private final InputStream myErrorStream;
  private final WinPTYOutputStream myOutputStream;

  @Deprecated
  public WinPtyProcess(String[] command, String[] environment, String workingDirectory, boolean consoleMode) throws IOException {
    this(command, convertEnvironment(environment), workingDirectory, consoleMode);
  }

  private static String convertEnvironment(String[] environment) {
    StringBuilder envString = new StringBuilder();
    for (String s : environment) {
      envString.append(s).append('\0');
    }
    envString.append('\0');
    return envString.toString();
  }

  public WinPtyProcess(String[] command, String environment, String workingDirectory, boolean consoleMode) throws IOException {
    try {
      myWinPty = new WinPty(Joiner.on(" ").join(command), workingDirectory, environment, consoleMode);
    }
    catch (PtyException e) {
      throw new IOException("Couldn't create PTY", e);
    }
    myInputStream = new WinPTYInputStream(myWinPty.getInputPipe());
    myOutputStream = new WinPTYOutputStream(myWinPty.getOutputPipe(), consoleMode);
    if (!consoleMode) {
      myErrorStream = new InputStream() {
        @Override
        public int read() {
          return -1;
        }
      };
    }
    else {
      myErrorStream = new WinPTYInputStream(myWinPty.getErrorPipe());
    }
  }

  @Override
  public boolean isRunning() {
    return myWinPty.exitValue() == -1;
  }

  @Override
  public void setWinSize(WinSize winSize) {
    myWinPty.setWinSize(winSize);
  }

  @Override
  public WinSize getWinSize() throws IOException {
    return null; //TODO
  }

  @Override
  public OutputStream getOutputStream() {
    return myOutputStream;
  }

  @Override
  public InputStream getInputStream() {
    return myInputStream;
  }

  @Override
  public InputStream getErrorStream() {
    return myErrorStream;
  }

  @Override
  public int waitFor() throws InterruptedException {
    for (; ; ) {
      int exitCode = myWinPty.exitValue();
      if (exitCode != -1) {
        return exitCode;
      }
      Thread.sleep(1000);
    }
  }

  @Override
  public int exitValue() {
    int exitValue = myWinPty.exitValue();
    if (exitValue == -1) {
      throw new IllegalThreadStateException("Not terminated yet");
    }
    return exitValue;
  }

  @Override
  public void destroy() {
    myWinPty.close();
  }
}
