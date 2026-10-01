package com.pty4j.unix;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Signals that a native pty call failed.
 */
public class UnixPtyException extends IOException {

  private final int myErrno;

  UnixPtyException(@NotNull String message, int errno) {
    super(message);
    myErrno = errno;
  }

  /** The {@code errno} value the failed native call left behind. */
  public int getErrno() {
    return myErrno;
  }
}
