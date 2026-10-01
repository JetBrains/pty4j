@file:Suppress("SpellCheckingInspection")

package com.pty4j.unix

import com.sun.jna.*
import com.sun.jna.platform.unix.LibCAPI.size_t
import com.sun.jna.platform.unix.LibCAPI.ssize_t

internal object CLibrary {

  const val O_WRONLY: Int = 0x00000001
  const val O_RDWR: Int = 0x00000002
  const val POLLIN: Short = 0x00000001
  const val EINTR: Int = 0x00000004

  @JvmField
  val EAGAIN: Int = when {
    Platform.isLinux() || Platform.isSolaris() -> 0x0000000b
    else -> 0x00000023
  }

  @JvmField
  val O_NOCTTY: Int = when {
    Platform.isLinux() -> 0x00000100
    Platform.isFreeBSD() -> 0x00008000
    Platform.isSolaris() -> 0x00000800
    else -> 0x00020000
  }

  const val ENOTTY: Int = 25 // Not a typewriter / "Inappropriate ioctl for device" (errno.h)

  private val libc: CLibraryNative = Native.load(Platform.C_LIBRARY_NAME, CLibraryNative::class.java)

  @JvmStatic
  fun open(path: String, flags: Int): Int = libc.open(path, flags)

  @JvmStatic
  fun close(fd: Int): Int = libc.close(fd)

  @JvmStatic
  fun read(fd: Int, buf: ByteArray, len: Int): Int {
    val result = libc.read(fd, buf, size_t(len.toLong()))
    return result.toInt()
  }

  @JvmStatic
  fun write(fd: Int, buf: ByteArray, len: Int): Int {
    val result = libc.write(fd, buf, size_t(len.toLong()))
    return result.toInt()
  }

  @JvmStatic
  fun read(fd: Int, buf: ByteArray, off: Int, len: Int): Int {
    if (off == 0 && len == buf.size) return read(fd, buf, len)
    val tmp = ByteArray(len)
    val result = read(fd, tmp, len)
    if (result > 0) tmp.copyInto(buf, off, 0, result)
    return result
  }

  @JvmStatic
  fun write(fd: Int, buf: ByteArray, off: Int, len: Int): Int {
    val bufToWrite = if (off == 0 && len == buf.size) buf else buf.copyOfRange(off, off + len)
    return write(fd, bufToWrite, len)
  }

  @JvmStatic
  fun pipe(fds: IntArray): Int = libc.pipe(fds)

  // https://pubs.opengroup.org/onlinepubs/009696699/functions/errno.html
  @JvmStatic
  fun errno(): Int = Native.getLastError()

  /**
   * Upon successful completion, poll() shall return a non-negative value.
   * A positive value indicates the total number of file descriptors that have been selected
   * (that is, file descriptors for which the revents member is non-zero).
   * A value of 0 indicates that the call timed out and no file descriptors have been selected.
   * Upon failure, poll() shall return -1 and set errno to indicate the error.
   */
  @JvmStatic
  fun poll(fds: Array<Pollfd>, timeout: Int): Int {
    val pollfdsReference = PollfdStructureByReference()
    @Suppress("UNCHECKED_CAST")
    val pollfdStructures: Array<PollfdStructure> = pollfdsReference.toArray(fds.size) as Array<PollfdStructure>
    for (i in fds.indices) {
      pollfdStructures[i].fd = fds[i].fd
      pollfdStructures[i].events = fds[i].events
    }
    val ret: Int = libc.poll(pollfdsReference, fds.size, timeout)
    for (i in fds.indices) {
      fds[i].revents = pollfdStructures[i].revents
    }
    return ret
  }
}

internal class Pollfd(val fd: Int, val events: Short) {
  var revents: Short = 0
}

private interface CLibraryNative : Library {

  // https://pubs.opengroup.org/onlinepubs/009695399/functions/open.html
  fun open(path: String, flags: Int): Int

  // https://pubs.opengroup.org/onlinepubs/009604499/functions/close.html
  fun close(fd: Int): Int

  // https://pubs.opengroup.org/onlinepubs/009604599/functions/read.html
  fun read(fd: Int, buf: ByteArray, len: size_t): ssize_t

  // https://pubs.opengroup.org/onlinepubs/009695399/functions/write.html
  fun write(fd: Int, buf: ByteArray, len: size_t): ssize_t

  // https://pubs.opengroup.org/onlinepubs/009695399/functions/pipe.html
  fun pipe(fds: IntArray): Int

  // https://pubs.opengroup.org/onlinepubs/009604599/functions/poll.html
  fun poll(pollfds: PollfdStructureByReference, nfds: Int, timeout: Int): Int
}

// https://pubs.opengroup.org/onlinepubs/009604599/basedefs/poll.h.html

@Structure.FieldOrder(value = ["fd", "events", "revents"])
internal open class PollfdStructure : Structure() {
  @JvmField
  var fd: Int = 0
  @JvmField
  var events: Short = 0
  @JvmField
  var revents: Short = 0
}

internal class PollfdStructureByReference : PollfdStructure(), Structure.ByReference
