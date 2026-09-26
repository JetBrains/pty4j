package com.pty4j

import com.sun.jna.Platform
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.OutputStream

class PtyOutputStreamTest {
  @Before
  fun setUp() {
    Assume.assumeFalse(Platform.isWindows())
    TestUtil.setLocalPtyLib()
  }

  @Test
  fun testWriteFromBufferRegion() {
    writeFromBufferRegion(off = 20)
  }

  @Test
  fun testWriteFromBufferStart() {
    writeFromBufferRegion(off = 0)
  }

  private fun writeFromBufferRegion(off: Int) {
    val process = PtyProcessBuilder(arrayOf("/bin/cat")).start()
    val stdout = PtyTest.startStdoutGobbler(process)
    val text = "hello\n"
    // Surround the text with bytes that must not reach the process.
    val buf = ByteArray(64) { 'x'.code.toByte() }
    text.toByteArray(Charsets.UTF_8).copyInto(buf, off)
    process.outputStream.write(buf, off, text.length)
    process.outputStream.flush()
    // The terminal echoes the input and then cat prints it.
    stdout.assertEndsWith("hello\r\nhello\r\n")
    Assert.assertEquals("hello\r\nhello\r\n", stdout.output)
    process.outputStream.write(4) // Ctrl+D
    process.outputStream.flush()
    PtyTest.assertProcessTerminatedNormally(process)
  }

  @Test
  fun testWriteRejectsInvalidRange() {
    val process = PtyProcessBuilder(arrayOf("/bin/cat")).start()
    val stdout = PtyTest.startStdoutGobbler(process)
    val outputStream = process.outputStream
    val buf = ByteArray(10) { 'x'.code.toByte() }
    assertWriteRejectsRange(outputStream, buf, 0, buf.size + 1)
    assertWriteRejectsRange(outputStream, buf, 1, buf.size)
    assertWriteRejectsRange(outputStream, buf, buf.size, 1)
    assertWriteRejectsRange(outputStream, buf, -1, 1)
    assertWriteRejectsRange(outputStream, buf, 0, -1)
    assertWriteRejectsRange(outputStream, buf, 0, Int.MAX_VALUE)
    assertWriteRejectsRange(outputStream, buf, Int.MAX_VALUE, 1)
    outputStream.write(buf, 0, 0)
    outputStream.write(buf, buf.size, 0)
    outputStream.flush()
    // None of the calls above wrote anything, so the echoed text below is the whole output.
    outputStream.write("hello\n".toByteArray(Charsets.UTF_8))
    outputStream.flush()
    stdout.assertEndsWith("hello\r\nhello\r\n")
    Assert.assertEquals("hello\r\nhello\r\n", stdout.output)
    outputStream.write(4) // Ctrl+D
    outputStream.flush()
    PtyTest.assertProcessTerminatedNormally(process)
  }

  private fun assertWriteRejectsRange(outputStream: OutputStream, buf: ByteArray, off: Int, len: Int) {
    Assert.assertThrows(IndexOutOfBoundsException::class.java) {
      outputStream.write(buf, off, len)
    }
  }
}
