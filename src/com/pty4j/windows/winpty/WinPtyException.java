package com.pty4j.windows.winpty;

import org.jetbrains.annotations.NotNull;

public class WinPtyException extends Exception {
  WinPtyException(@NotNull String message) {
    super(message);
  }
}
