package com.pty4j.unix

import com.pty4j.PtyProcess
import com.pty4j.WinSize
import com.pty4j.util.PtyUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

internal class ProcessBuilderUnixLauncher @Throws(Exception::class) constructor(
  command: List<String>,
  environmentMap: Map<String, String>,
  workingDirectory: String,
  pty: Pty,
  errPty: Pty?,
  consoleMode: Boolean,
  initialColumns: Int?,
  initialRows: Int?,
  ptyProcess: PtyProcess
) {

  val process: Process

  init {
    val spawnHelper = PtyUtil.resolveNativeFile("pty4j-unix-spawn-helper")
    val builder = ProcessBuilder()
    builder.command(listOf(spawnHelper.absolutePath,
                           workingDirectory,
                           (if (consoleMode) 1 else 0).toString(),
                           pty.slaveName,
                           pty.masterFD.toString(),
                           errPty?.slaveName.orEmpty(),
                           (errPty?.masterFD ?: -1).toString()
                           ) + command)
    val environment = builder.environment()
    environment.clear()
    environment.putAll(environmentMap)
    builder.directory(File(workingDirectory))
    builder.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    if (errPty == null) {
      builder.redirectErrorStream(true)
    }
    else {
      builder.redirectError(ProcessBuilder.Redirect.DISCARD)
    }
    process = builder.start()

    initTermSize(pty, ptyProcess, WinSize(initialColumns ?: 80, initialRows ?: 25))
  }

  private fun initTermSize(pty: Pty, ptyProcess: PtyProcess, initSize: WinSize) {
    // Pty will be fully initialized after `open(slave_name, O_RDWR)` in the child process.
    // Until it happens, resize attempts will fail with `ENOTTY`.
    val start = TimeSource.Monotonic.markNow()
    var lastException: UnixPtyException? = null
    var performedAttempts = 0
    for (attempt in 1..1000) {
      try {
        performedAttempts++
        pty.setWindowSize(initSize, ptyProcess)
        lastException = null
        break
      }
      catch (e: UnixPtyException) {
        lastException = e
        if (e.errno != CLibrary.ENOTTY) {
          break
        }
        Thread.sleep(2)
      }
    }
    if (lastException != null) {
      LOG.warn("Failed to set initial terminal size, attempts: $performedAttempts", lastException)
    }
    else if (LOG.isDebugEnabled) {
      val elapsed = start.elapsedNow()
      LOG.debug("Terminal initial size set to ($initSize) in ${elapsed.toString(DurationUnit.MILLISECONDS)}, attempt: $performedAttempts")
    }
  }

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(ProcessBuilderUnixLauncher::class.java)
  }

}
