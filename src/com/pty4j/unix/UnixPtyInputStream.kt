/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix

import java.io.IOException
import java.io.InputStream
import java.util.Objects

internal class UnixPtyInputStream(private val pty: Pty) : InputStream() {

  @Throws(IOException::class)
  override fun read(): Int {
    val buf = ByteArray(1)
    if (read(buf, 0, 1) != 1) {
      return -1
    }
    return java.lang.Byte.toUnsignedInt(buf[0])
  }

  @Throws(IOException::class)
  override fun read(buf: ByteArray, off: Int, len: Int): Int {
    Objects.checkFromIndexSize(off, len, buf.size)
    if (len == 0) {
      return 0
    }
    val readBytes = pty.read(buf, off, len)
    return if (readBytes <= 0) -1 else readBytes
  }

  @Throws(IOException::class)
  override fun close() {
    pty.close()
  }

  @Throws(IOException::class)
  override fun available(): Int {
    if (pty.isClosed) {
      throw IOException("File descriptor is closed")
    }
    return 0
  }
}
