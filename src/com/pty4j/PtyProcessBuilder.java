package com.pty4j;

import com.pty4j.unix.Pty;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.util.PtyUtil;
import com.pty4j.windows.CygwinPtyProcess;
import com.pty4j.windows.WinPtyProcess;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class PtyProcessBuilder {
  private String[] myCommand;
  private Map<String, String> myEnvironment;
  private String myDirectory;
  private boolean myConsole;
  private boolean myCygwin;
  private File myLogFile;

  public PtyProcessBuilder(@NotNull String[] command) {
    myCommand = command;
  }

  public void setCommand(@NotNull String[] command) {
    myCommand = command;
  }

  @NotNull
  public PtyProcessBuilder setEnvironment(@Nullable Map<String, String> environment) {
    myEnvironment = environment;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setDirectory(String directory) {
    myDirectory = directory;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setConsole(boolean console) {
    myConsole = console;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setCygwin(boolean cygwin) {
    myCygwin = cygwin;
    return this;
  }

  @NotNull
  public PtyProcessBuilder setLogFile(@Nullable File logFile) {
    myLogFile = logFile;
    return this;
  }

  @NotNull
  public PtyProcess start() throws IOException {
    if (Platform.isWindows()) {
      Map<String, String> environment = myEnvironment;
      if (environment == null) {
        environment = new TreeMap<String, String>();
      }
      if (myCygwin) {
        return new CygwinPtyProcess(myCommand, environment, myDirectory, myLogFile, myConsole);
      }
      return new WinPtyProcess(myCommand, Advapi32Util.getEnvironmentBlock(environment), myDirectory, myConsole);
    }
    return new UnixPtyProcess(myCommand, PtyUtil.toStringArray(myEnvironment), myDirectory, new Pty(myConsole), myConsole ? new Pty() : null);
  }
}
