package com.pty4j.windows.conpty;

import java.io.IOException;
import java.io.InputStream;

final class NullInputStream extends InputStream {

  static final NullInputStream INSTANCE = new NullInputStream();

  private NullInputStream() {}

  @Override
  public int read() throws IOException {
    return -1;
  }

  public int available() {
    return 0;
  }
}
