package com.pty4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PtyProcessOptions {
  private final Command command;
  private final Map<String, String> myEnvironment;
  private final String myDirectory;
  private final boolean myRedirectErrorStream;
  private final Integer myInitialColumns;
  private final Integer myInitialRows;
  private final boolean myWindowsAnsiColorEnabled;
  private final boolean myUnixOpenTtyToPreserveOutputAfterTermination;
  private final boolean mySpawnProcessUsingJdkOnMacIntel;

  PtyProcessOptions(@NotNull Command command,
                    @NotNull Map<String, String> environment,
                    @Nullable String directory,
                    boolean redirectErrorStream,
                    @Nullable Integer initialColumns,
                    @Nullable Integer initialRows,
                    boolean windowsAnsiColorEnabled,
                    boolean unixOpenTtyToPreserveOutputAfterTermination,
                    boolean spawnProcessUsingJdkOnMacIntel) {
    this.command = command;
    myEnvironment = environment;
    myDirectory = directory;
    myRedirectErrorStream = redirectErrorStream;
    myInitialColumns = initialColumns;
    myInitialRows = initialRows;
    myWindowsAnsiColorEnabled = windowsAnsiColorEnabled;
    myUnixOpenTtyToPreserveOutputAfterTermination = unixOpenTtyToPreserveOutputAfterTermination;
    mySpawnProcessUsingJdkOnMacIntel = spawnProcessUsingJdkOnMacIntel;
  }

  /**
   * Retrieves the command associated with this process. See {@code Command.toList()} for more details.
   *
   * @deprecated May return a processed commandline. Use {@link #getCommandWrapper()} instead.
   *
   * @return a {@link List} of strings representing the command line.
   */
  @Deprecated
  @NotNull
  public String[] getCommand() {
    return command.toArray();
  }

  @NotNull
  public Command getCommandWrapper() {
    return command;
  }

  public @NotNull Map<String, String> getEnvironment() {
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

  public boolean isWindowsAnsiColorEnabled() {
    return myWindowsAnsiColorEnabled;
  }

  public boolean isUnixOpenTtyToPreserveOutputAfterTermination() {
    return myUnixOpenTtyToPreserveOutputAfterTermination;
  }

  public boolean isSpawnProcessUsingJdkOnMacIntel() {
    return mySpawnProcessUsingJdkOnMacIntel;
  }
}
