package com.pty4j.windows.conpty;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32Util;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class LastErrorExceptionEx extends IOException {

  public LastErrorExceptionEx(@NotNull String action) {
    super(getErrorMessage(action));
  }

  public LastErrorExceptionEx(@NotNull String action, int lastError) {
    super(getErrorMessage(action, lastError));
  }

  public static @NotNull String getErrorMessage(@NotNull String action) {
    return getErrorMessage(action, Native.getLastError());
  }

  private static @NotNull String getErrorMessage(@NotNull String action, int lastError) {
    return action + " failed: GetLastError() returned " + lastError + ": " + Kernel32Util.formatMessage(lastError);
  }
}
