/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix;


import jtermios.JTermios;

import java.io.IOException;
import java.io.InputStream;

public class PTYInputStream extends InputStream {
  int myFD;

  /**
   * From a Unix valid file descriptor set a Reader.
   *
   * @param fd file descriptor.
   */
  public PTYInputStream(int fd) {
    myFD = fd;
  }

  /**
   * Implementation of read for the InputStream.
   *
   * @throws java.io.IOException on error.
   */
  @Override
  public int read() throws IOException {
    byte b[] = new byte[1];
    if (1 != read(b, 0, 1)) {
      return -1;
    }
    return b[0];
  }

  @Override
  public int read(byte[] buf, int off, int len) throws IOException {
    if (buf == null) {
      throw new NullPointerException();
    }
    if ((off < 0) || (off > buf.length) || (len < 0) || ((off + len) > buf.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    }
    if (len == 0) {
      return 0;
    }
    byte[] tmpBuf = new byte[len];
    len = read0(myFD, tmpBuf, len);
    if (len <= 0) {
      return -1;
    }
    System.arraycopy(tmpBuf, 0, buf, off, len);

    return len;
  }

  @Override
  public void close() throws IOException {
    if (myFD == -1) {
      return;
    }
    close0(myFD);
    myFD = -1;
  }

  @Override
  public int available() throws IOException {
    if (myFD == -1) {
      throw new IOException("File descriptor is closed");
    }
    return 0;
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();  
  }

  private int read0(int fd, byte[] buf, int len) throws IOException {
    return JTermios.read(fd, buf, len);
  }

  private int close0(int fd) throws IOException {
    return JTermios.close(fd);
  }
}
