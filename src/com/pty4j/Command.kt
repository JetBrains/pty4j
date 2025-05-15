package com.pty4j

import com.pty4j.util.ParametersListUtil
import com.pty4j.windows.winpty.WinPtyProcess.joinCmdArgs

sealed class Command {
  abstract fun toList(): List<String>
  open fun toArray(): Array<String> = toList().toTypedArray()

  abstract fun toCommandLine(): String
  override fun toString(): String = toCommandLine()

  class RawCommandString(private val commandLine: String) : Command() {
    override fun toCommandLine(): String = commandLine
    override fun toList(): List<String> = ParametersListUtil.parse(commandLine)
  }

  class CommandList(private val commandLine: List<String>) : Command() {
    internal constructor(commandLineArray: Array<String>) : this(commandLineArray.toList())
    override fun toList(): List<String> = commandLine
    override fun toCommandLine(): String = joinCmdArgs(commandLine.toTypedArray())
  }
}
