package com.pty4j.windows

@Deprecated("Use com.pty4j.windows.winpty.WinPtyException instead",
            replaceWith = ReplaceWith("com.pty4j.windows.winpty.WinPtyException"))
class WinPtyException private constructor(message: String) : Exception(message)