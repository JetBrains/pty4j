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

    if (myPatchNewline) {
      byte[] newBuf = new byte[len * 2];
      int newPos = 0;
      for (int i = off; i < off + len; ++i) {
        if (b[i] == '\n') {
          newBuf[newPos++] = '\r';
        }
        newBuf[newPos++] = b[i];
      }
      b = newBuf;
      off = 0;
      len = newPos;
    }

    myNamedPipe.write(b, off, len);
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
