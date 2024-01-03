package com.pty4j.util

import com.pty4j.TestUtil
import com.sun.jna.Platform
import org.junit.Assert
import org.junit.Test

class PtyUtilTest {
  @Test
  fun `find bundled native file`() {
    TestUtil.useLocalNativeLib(true)
    try {
      Assert.assertTrue(PtyUtil.resolveNativeFile(getLibraryName()).exists())
    }
    finally {
      TestUtil.useLocalNativeLib(false)
    }
  }

  private fun getLibraryName(): String = when {
    Platform.isMac() -> "libpty.dylib"
    Platform.isWindows() -> "winpty.dll"
    else -> "libpty.so"
  }
}
