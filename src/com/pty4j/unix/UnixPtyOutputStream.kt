/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix

import java.io.IOException
import java.io.OutputStream
import java.util.Objects

internal class UnixPtyOutputStream(private val pty: Pty) : OutputStream() {

  @Throws(IOException::class)
  override fun write(b: ByteArray, off: Int, len: Int) {
    Objects.checkFromIndexSize(off, len, b.size)
    if (len > 0) {
      pty.write(b, off, len)
    }
  }

  @Throws(IOException::class)
  override fun write(b: Int) {
    write(byteArrayOf(b.toByte()), 0, 1)
  }

  @Throws(IOException::class)
  override fun close() {
    pty.close()
  }
}
