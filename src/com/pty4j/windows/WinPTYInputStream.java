/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.windows;


import java.io.IOException;
import java.io.InputStream;

public class WinPTYInputStream extends InputStream {
  private final WinPty myWinPty;
  private final NamedPipe myNamedPipe;

  public WinPTYInputStream(WinPty winPty, NamedPipe namedPipe) {
    myWinPty = winPty;
    myNamedPipe = namedPipe;
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
    return myNamedPipe.read(buf, off, len);
  }

  @Override
  public void close() throws IOException {
    // We need to keep a reference to WinPty for as long as we're reading
    // output, because allowing WinPty to be finalized would kill the agent
    // (and the child process whose output we're reading).
    //
    // Once we've read all the program's output (or the process has exited),
    // we close the pty's CONOUT and CONERR input streams.  Once they're
    // closed, we want to close the WinPty object, which kills the agent
    // process.

    myWinPty.decrementOpenInputStreamCount();
    myNamedPipe.close();
  }

  @Override
  public int available() throws IOException {
    return myNamedPipe.available();
  }
}
