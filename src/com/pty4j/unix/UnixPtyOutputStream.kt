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
import java.io.OutputStream;
import java.util.Objects;

class PTYOutputStream extends OutputStream {
  Pty myPty;

  public PTYOutputStream(Pty pty) {
    myPty = pty;
  }

  @Override public void write(byte @NotNull [] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    if (len > 0) {
      myPty.write(b, off, len);
    }
  }

  @Override public void write(int b) throws IOException {
    byte[] buf = new byte[1];
    buf[0] = (byte) b;
    write(buf, 0, 1);
  }

  @Override public void close() throws IOException {
    myPty.close();
  }
}
