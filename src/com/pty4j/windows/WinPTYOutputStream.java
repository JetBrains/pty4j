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
  private final WinPty myWinPty;
  private final NamedPipe myNamedPipe;
  private final boolean myPatchNewline;

  public WinPTYOutputStream(WinPty winPty, NamedPipe namedPipe, boolean patchNewline) {
    // Keep a reference to WinPty to prevent it from being finalized as long as
    // the WinPTYOutputStream object is alive.
    myWinPty = winPty;
    myNamedPipe = namedPipe;
    myPatchNewline = patchNewline;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (b == null) {
      throw new NullPointerException();
    }
    if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException();
    }

    if (myPatchNewline) {
      byte[] newBuf = new byte[len];
      int newPos = 0;
      for (int i = off; i < off + len; ++i) {
        if (b[i] == '\n') {
          newBuf[newPos++] = '\r';
        } else {
          newBuf[newPos++] = b[i];
        }
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
    myNamedPipe.close();
  }
}
