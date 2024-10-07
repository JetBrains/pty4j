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

  const val ENOTTY: Int = 25 // Not a typewriter / Inappropriate ioctl for device (errno.h)

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
  fun pipe(fds: IntArray): Int = libc.pipe(fds)

  @Suppress("SpellCheckingInspection")
  @JvmStatic
  fun errno(): Int = libc.errno()

  /**
   * Upon successful completion, poll() shall return a non-negative value.
   * A positive value indicates the total number of file descriptors that have been selected
   * (that is, file descriptors for which the revents member is non-zero).
   * A value of 0 indicates that the call timed out and no file descriptors have been selected.
   * Upon failure, poll() shall return -1 and set errno to indicate the error.
   */
  @JvmStatic
  fun poll(fds: Array<Pollfd>, nfds: Int, timeout: Int): Int {
    if (nfds <= 0 || nfds > fds.size) {
      throw IllegalArgumentException(("nfds $nfds").toString() + " must be <= fds.size " + fds.size)
    }
    val pollfdsReference = PollfdStructureByReference()
    @Suppress("UNCHECKED_CAST")
    val pollfdStructures: Array<PollfdStructure> = pollfdsReference.toArray(nfds) as Array<PollfdStructure>
    for (i in 0 until nfds) {
      pollfdStructures[i].fd = fds[i].fd
      pollfdStructures[i].events = fds[i].events
    }
    val ret: Int = libc.poll(pollfdsReference, nfds, timeout)
    for (i in 0 until nfds) {
      fds[i].revents = pollfdStructures[i].revents
    }
    return ret
  }

  @JvmStatic
  fun select(nfds: Int, readfds: FDSet): Int {
    return libc.select(nfds, readfds as fd_set, null, null, null)
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

  @Suppress("SpellCheckingInspection")
  fun errno(): Int

  // https://pubs.opengroup.org/onlinepubs/009604599/functions/poll.html
  fun poll(pollfds: PollfdStructureByReference, nfds: Int, timeout: Int): Int

  // https://pubs.opengroup.org/onlinepubs/7908799/xsh/select.html
  fun select(nfds: Int, readfds: fd_set?, writefds: fd_set?, errorfds: fd_set?, timeout: timeval?): Int
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

@Suppress("FunctionName")
internal interface FDSet {
  fun FD_SET(fd: Int)
  fun FD_ISSET(fd: Int): Boolean
}

@Suppress("ClassName")
@Structure.FieldOrder(value = ["fd_array"])
internal class fd_set : Structure(), FDSet {

  @Suppress("PropertyName")
  @JvmField
  var fd_array: IntArray = IntArray((fd_count + NFBBITS - 1) / NFBBITS)

  override fun FD_SET(fd: Int) {
    fd_array[fd / NFBBITS] = fd_array[fd / NFBBITS] or (1 shl (fd % NFBBITS))
  }

  override fun FD_ISSET(fd: Int): Boolean {
    return (fd_array[fd / NFBBITS] and (1 shl (fd % NFBBITS))) != 0
  }

  companion object {
    private const val NFBBITS = 32
    private const val fd_count = 1024
  }
}

@Suppress("PropertyName", "unused", "ClassName")
@Structure.FieldOrder(value = ["tv_sec", "tv_usec"])
internal class timeval : Structure() {
  @JvmField
  var tv_sec: NativeLong = NativeLong(0)
  @JvmField
  var tv_usec: NativeLong = NativeLong(0)
}
