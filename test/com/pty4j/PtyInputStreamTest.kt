package com.pty4j

import com.sun.jna.Platform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class PtyInputStreamTest {
  @Before
  fun setUp() {
    Assume.assumeFalse(Platform.isWindows())
    TestUtil.setLocalPtyLib()
  }

  @Test
  fun testReadIntoBufferRegion() {
    val text = "hello, world"
    val process = startEcho(text)
    val input = process.inputStream
    val off = 20
    val len = 3
    val sentinel = 'x'.code.toByte()
    val output = ByteArrayOutputStream()
    while (output.size() < text.length) {
      val buf = ByteArray(64) { sentinel }
      val readBytes = input.read(buf, off, len)
      assertTrue("Unexpected end of stream, read so far: ${output.toUtf8Text().toPresentable()}", readBytes > 0)
      assertTrue("Read $readBytes bytes, but at most $len were requested", readBytes <= len)
      val expected = ByteArray(buf.size) { sentinel }.also {
        buf.copyInto(it, off, off, off + readBytes)
      }
      assertArrayEquals("Bytes outside the requested region were modified", expected, buf)
      output.write(buf, off, readBytes)
    }
    assertTrue("Unexpected output: ${output.toUtf8Text().toPresentable()}", output.toUtf8Text().startsWith(text))
    PtyTest.startStdoutGobbler(process) // consume the rest of the output
    PtyTest.assertProcessTerminatedNormally(process)
  }

  @Test
  fun testReadRejectsInvalidRange() {
    val process = startEcho("hello")
    val input = process.inputStream
    val buf = ByteArray(10)
    val invalidRanges = listOf(
      0 to buf.size + 1,
      1 to buf.size,
      buf.size to 1,
      -1 to 1,
      0 to -1,
      0 to Int.MAX_VALUE,
      Int.MAX_VALUE to 1,
    )
    for ((off, len) in invalidRanges) {
      assertThrows("read(byte[${buf.size}], $off, $len)", IndexOutOfBoundsException::class.java) {
        input.read(buf, off, len)
      }
    }
    assertEquals(0, input.read(buf, 0, 0))
    assertEquals(0, input.read(buf, buf.size, 0))
    // None of the calls above consumed output.
    PtyTest.startStdoutGobbler(process).assertEndsWith("hello\r\n")
    PtyTest.assertProcessTerminatedNormally(process)
  }

  private fun startEcho(text: String): PtyProcess =
    PtyProcessBuilder(arrayOf("/bin/echo", text))
      .setUnixOpenTtyToPreserveOutputAfterTermination(true)
      .start()

  private fun ByteArrayOutputStream.toUtf8Text(): String = String(toByteArray(), Charsets.UTF_8)
  private fun String.toPresentable(): String = PtyTest.convertInvisibleChars(this)
}
