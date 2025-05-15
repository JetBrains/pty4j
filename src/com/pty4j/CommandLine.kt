package com.pty4j

import com.pty4j.util.ParametersListUtil
import com.pty4j.windows.winpty.WinPtyProcess.joinCmdArgs


sealed class CommandLine {
  abstract fun toList(): List<String>
  open fun toArray(): Array<String> = toList().toTypedArray()

  abstract fun toCommandLineString(): String
  override fun toString(): String = toCommandLineString()

  class RawCommandLineString(private val commandLine: String) : CommandLine() {
    override fun toCommandLineString(): String = commandLine
    override fun toList(): List<String> = ParametersListUtil.parse(commandLine)
  }

  class CommandLineList(private val commandLine: List<String>) : CommandLine() {
    internal constructor(commandLineArray: Array<String>) : this(commandLineArray.toList())
    override fun toList(): List<String> = commandLine
    override fun toCommandLineString(): String = joinCmdArgs(commandLine.toTypedArray())
  }
}
