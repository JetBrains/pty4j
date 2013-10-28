/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.windows;


import com.sun.jna.platform.win32.WinNT;

import java.io.IOException;
import java.io.InputStream;

public class WinPTYInputStream extends InputStream {
  private final WinPty myWinPty;


  public WinPTYInputStream(WinPty winPty) {
    myWinPty = winPty;
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

    len = myWinPty.read(tmpBuf, len);

    if (len <= 0) {
      return -1;
    }
    System.arraycopy(tmpBuf, 0, buf, off, len);

    int x = 1000;

    return len;
  }

  @Override
  public void close() throws IOException {
    myWinPty.close();
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }


  private boolean close0(WinNT.HANDLE handle) throws IOException {
    return com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(handle);
  }

}
