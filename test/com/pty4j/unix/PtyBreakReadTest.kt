package com.pty4j.unix

import com.pty4j.TestUtil
import com.sun.jna.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Waking up and closing a [Pty] whose reader is blocked in `poll()`.
 *
 * The slave side is kept open, so the master never reports a hangup and the wake-up
 * pipe is the only way out of `poll()`. Every potentially blocking call runs on a
 * helper thread and is awaited with a timeout, so a deadlock fails the test instead
 * of hanging the build.
 */
class PtyBreakReadTest {
  @Before
  fun setUp() {
    Assume.assumeFalse(Platform.isWindows())
    TestUtil.setLocalPtyLib()
  }

  @Test(timeout = 20_000)
  fun testBreakReadWakesBlockedReader() = withPty { pty ->
    val reader = startBlockedReader(pty)
    Task("pty waker") { pty.breakRead() }.start().await("breakRead() with a reader blocked in poll()")
    assertEquals(-1, reader.await("reader after breakRead()"))
  }

  @Test(timeout = 20_000)
  fun testCloseReleasesBlockedReader() = withPty { pty ->
    val reader = startBlockedReader(pty)
    Task("pty closer") { pty.close() }.start().await("close() with a reader blocked in poll()")
    assertEquals(-1, reader.await("reader after close()"))
  }

  /**
   * A `breakRead()` on a closed pty returns and disturbs no other pty. This documents
   * the post-close contract; it does not reproduce the race from #181.
   */
  @Test(timeout = 20_000)
  fun testBreakReadAfterCloseIsNoOp() = withPty { pty ->
    val closed = Pty(true)
    closed.close()
    val reader = startBlockedReader(pty)
    Task("pty waker") { closed.breakRead() }.start().await("breakRead() on a closed pty")
    assertTrue("breakRead() on a closed pty woke up a reader of another pty", reader.isStillRunning(300))
    Task("pty closer") { pty.close() }.start().await("close() with a reader blocked in poll()")
    assertEquals(-1, reader.await("reader after close()"))
  }

  /** A body on a daemon thread whose result, or exception, is awaited with a timeout. */
  private class Task<T>(name: String, body: () -> T) {
    private val future = FutureTask { body() }
    private val thread = Thread(future, name).apply { isDaemon = true }

    fun start(): Task<T> {
      thread.start()
      return this
    }

    fun await(what: String): T = get(what, TIMEOUT_MILLIS) ?: throw AssertionError("$what is still blocked")

    fun isStillRunning(millis: Long): Boolean = get("task", millis) == null

    /** The body's result, or null if it has not finished within [millis]. */
    private fun get(what: String, millis: Long): T? =
      try {
        future.get(millis, TimeUnit.MILLISECONDS)
      }
      catch (_: TimeoutException) {
        null
      }
      catch (e: ExecutionException) {
        throw AssertionError("$what failed", e.cause)
      }

    /** True while the thread is inside the native `poll()` behind [CLibrary.poll]. */
    val isInsidePoll: Boolean
      get() {
        val frames = thread.stackTrace
        return frames.any { it.className == CLibrary::class.java.name && it.methodName == "poll" } &&
               frames.any { it.className == "com.sun.jna.Native" }
      }
  }

  private fun startBlockedReader(pty: Pty): Task<Int> {
    val reader = Task("pty reader") { pty.read(ByteArray(16), 0, 16) }.start()
    val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
    while (System.currentTimeMillis() < deadline) {
      if (reader.isInsidePoll) return reader
      if (!reader.isStillRunning(0)) throw AssertionError("Reader returned before blocking in poll()")
      Thread.sleep(10)
    }
    throw AssertionError("Reader did not reach the native poll() in time")
  }

  /**
   * Runs [body] with a pty whose slave is kept open, then closes the pty on a helper
   * thread. A hanging or failing cleanup is reported without hiding a failure of [body].
   */
  private fun withPty(body: (Pty) -> Unit) {
    val pty = Pty(true)
    var failure: Throwable? = null
    try {
      body(pty)
    }
    catch (t: Throwable) {
      failure = t
    }
    try {
      if (Task("pty cleanup") { pty.close() }.start().isStillRunning(TIMEOUT_MILLIS)) {
        throw AssertionError("close() is still blocked during cleanup")
      }
    }
    catch (cleanupFailure: Throwable) {
      if (failure == null) throw cleanupFailure
      failure.addSuppressed(cleanupFailure)
    }
    if (failure != null) throw failure
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
