/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

class PTYInputStream extends InputStream {
  Pty myPty;

  public PTYInputStream(Pty pty) {
    myPty = pty;
  }

  /**
   * Implementation of read for the InputStream.
   *
   * @throws java.io.IOException on error.
   */
  @Override
  public int read() throws IOException {
    byte[] b = new byte[1];
    if (read(b, 0, 1) != 1) {
      return -1;
    }
    return Byte.toUnsignedInt(b[0]);
  }

  @Override
  public int read(byte @NotNull [] buf, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, buf.length);
    if (len == 0) {
      return 0;
    }
    int readBytes = myPty.read(buf, off, len);
    return readBytes <= 0 ? -1 : readBytes;
  }

  @Override
  public void close() throws IOException {
    myPty.close();
  }

  @Override
  public int available() throws IOException {
    if (myPty.isClosed()) {
      throw new IOException("File descriptor is closed");
    }
    return 0;
  }
}
