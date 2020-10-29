package com.pty4j.unix;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class UnixPtyException extends IOException {

  private final int myErrno;

  UnixPtyException(@NotNull String message, int errno) {
    super(message);
    myErrno = errno;
  }

  public int getErrno() {
    return myErrno;
  }
}
