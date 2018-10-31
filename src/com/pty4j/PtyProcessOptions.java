package com.pty4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PtyProcessOptions {
  private final String[] myCommand;
  private final Map<String, String> myEnvironment;
  private final String myDirectory;
  private final boolean myRedirectErrorStream;
  private final Integer myInitialColumns;
  private final Integer myInitialRows;

  PtyProcessOptions(@NotNull String[] command,
                    @Nullable Map<String, String> environment,
                    @Nullable String directory,
                    boolean redirectErrorStream,
                    @Nullable Integer initialColumns,
                    @Nullable Integer initialRows) {
    myCommand = command;
    myEnvironment = environment;
    myDirectory = directory;
    myRedirectErrorStream = redirectErrorStream;
    myInitialColumns = initialColumns;
    myInitialRows = initialRows;
  }

  @NotNull
  public String[] getCommand() {
    return myCommand;
  }

  @Nullable
  public Map<String, String> getEnvironment() {
    return myEnvironment;
  }

  @Nullable
  public String getDirectory() {
    return myDirectory;
  }

  public boolean isRedirectErrorStream() {
    return myRedirectErrorStream;
  }

  @Nullable
  public Integer getInitialColumns() {
    return myInitialColumns;
  }

  @Nullable
  public Integer getInitialRows() {
    return myInitialRows;
  }
}
