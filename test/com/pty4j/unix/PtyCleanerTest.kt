package com.pty4j.unix

import com.pty4j.TestUtil
import com.sun.jna.Platform
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/** A [Pty] that is never closed releases its descriptors once it is garbage collected. */
class PtyCleanerTest {
  @Before
  fun setUp() {
    Assume.assumeFalse(Platform.isWindows())
    TestUtil.setLocalPtyLib()
  }

  @Test(timeout = 30_000)
  fun testUnclosedPtyIsReleasedAfterGarbageCollection() {
    val master = openAndForget()
    assertTrue("master not open right after construction", isOpen(master))
    val deadline = System.currentTimeMillis() + 10_000
    while (isOpen(master) && System.currentTimeMillis() < deadline) {
      System.gc()
      Thread.sleep(50)
    }
    assertTrue("master descriptor $master is still open", !isOpen(master))
  }

  /** Opens a pty, drops the only reference to it, and returns its master descriptor. */
  private fun openAndForget(): Int = Pty(true).masterFD

  private fun isOpen(fd: Int): Boolean = (PtyHelpers.getPtyExecutor() as NativePtyExecutor).isValidFd(fd)
}
