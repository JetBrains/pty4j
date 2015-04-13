/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.windows;

import java.io.IOException;
import java.io.OutputStream;

public class WinPTYOutputStream extends OutputStream {
  private final NamedPipe myNamedPipe;
  private boolean myClosed;
  private final boolean myPatchNewline;
  private final boolean mySendEOFInsteadClose;

  public WinPTYOutputStream(NamedPipe namedPipe) {
    this(namedPipe, false, false);
  }

  public WinPTYOutputStream(NamedPipe namedPipe, boolean patchNewline, boolean sendEOFInsteadClose) {
    myNamedPipe = namedPipe;
    myPatchNewline = patchNewline;
    mySendEOFInsteadClose = sendEOFInsteadClose;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (myClosed) {
      return;
    }

    if (b == null) {
      throw new NullPointerException();
    }
    else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    }
    else if (len == 0) {
      return;
    }

    byte[] tmpBuf;
    if (myPatchNewline) {
      tmpBuf = new byte[len * 2];
      int newLen = len;
      int ind_b = off;
      int ind_tmp = 0;
      while (ind_b < off + len) {
        if (b[ind_b] == '\n') {
          tmpBuf[ind_tmp++] = '\r';
          newLen++;
        }
        tmpBuf[ind_tmp++] = b[ind_b++];
      }
      len = newLen;
    }
    else {
      tmpBuf = new byte[len];
      System.arraycopy(b, off, tmpBuf, 0, len);
    }

    myNamedPipe.write(tmpBuf, len);
  }

  @Override
  public void write(int b) throws IOException {
    byte[] buf = new byte[1];
    buf[0] = (byte)b;
    write(buf, 0, 1);
  }

  @Override
  public void close() throws IOException {
    if (mySendEOFInsteadClose) {
      write(new byte[]{'^', 'Z', '\n'});
    }
    else {
      myClosed = true;
      myNamedPipe.close();
    }
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }
}
