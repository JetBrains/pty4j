package com.pty4j.unix

import com.pty4j.PtyProcess
import com.pty4j.WinSize
import com.pty4j.util.PtyUtil
import java.io.File

internal class ProcessBuilderUnixLauncher @Throws(Exception::class) constructor(
  command: MutableList<String>,
  environmentMap: MutableMap<String, String>,
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

    if (initialColumns != null || initialRows != null) {
      val size = WinSize(initialColumns ?: 80, initialRows ?: 25)

      // On OSX, there is a race condition with pty initialization
      // If we call com.pty4j.unix.Pty.setTerminalSize(com.pty4j.WinSize) too early, we can get ENOTTY
      for (attempt in 0..999) {
        try {
          pty.setWindowSize(size, ptyProcess)
          break
        }
        catch (e: UnixPtyException) {
          if (e.errno != UnixPtyProcess.ENOTTY) {
            break
          }
        }
      }
    }

  }

}
