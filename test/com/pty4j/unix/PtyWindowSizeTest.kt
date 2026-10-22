package com.pty4j.unix

import com.pty4j.TestUtil
import com.pty4j.WinSize
import com.sun.jna.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class PtyWindowSizeTest {
  @Before
  fun setUp() {
    Assume.assumeFalse(Platform.isWindows())
    TestUtil.setLocalPtyLib()
  }

  @Test
  fun `native PTY preserves pixel dimensions and clears unknown values`() {
    val pty = Pty(true)
    try {
      val slave = CLibrary.open(pty.slaveName, CLibrary.O_RDWR or CLibrary.O_NOCTTY)
      assertTrue("Cannot open the slave PTY", slave >= 0)
      try {
        val sizes = arrayOf(WinSize(80, 24, 1280, 720), WinSize(80, 24, 1600, 960), WinSize(81, 25, 65535, 32768), WinSize(81, 25))
        for (size in sizes) {
          pty.setWindowSize(size, null)
          assertEquals(size, pty.getWinSize(null))
          assertEquals(size, PtyHelpers.getWinSize(slave, null))
        }
      }
      finally {
        CLibrary.close(slave)
      }
    }
    finally {
      pty.close()
    }
  }
}
