package com.pty4j.windows

import com.pty4j.PtyProcessOptions
import com.pty4j.windows.winpty.WinPtyProcess

@Deprecated("Use com.pty4j.windows.winpty.WinPtyProcess instead",
            replaceWith = ReplaceWith("com.pty4j.windows.winpty.WinPtyProcess"))
class WinPtyProcess(options: PtyProcessOptions, consoleMode: Boolean) : WinPtyProcess(options, consoleMode)