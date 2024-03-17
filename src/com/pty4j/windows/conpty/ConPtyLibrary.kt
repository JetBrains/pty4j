package com.pty4j.windows.conpty

import com.pty4j.util.PtyUtil
import com.pty4j.util.logger
import com.pty4j.windows.conpty.WinEx.*
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.MAX_PATH
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.W32APIOptions
import java.io.File

@Suppress("FunctionName")
internal interface ConPtyLibrary : Library {
  fun CreatePseudoConsole(size: COORDByValue,
                          hInput: WinNT.HANDLE,
                          hOutput: WinNT.HANDLE,
                          dwFlags: DWORD,
                          phPC: HPCONByReference): HRESULT

  fun ClosePseudoConsole(hPC: HPCON)

  fun ResizePseudoConsole(hPC: HPCON, size: COORDByValue): HRESULT

  @Suppress("SpellCheckingInspection")
  companion object {
    private const val CONPTY: String = "conpty.dll"
    private const val KERNEL32: String = "kernel32"

    @JvmStatic
    val instance: ConPtyLibrary by lazy {
      try {
        val bundledConptyDll = PtyUtil.resolveNativeFile(CONPTY)
        // Load bundled conpty.dll only if the command line of "OpenConsole.exe" doesn't exceed 259 characters to fit into MAX_PATH.
        // Workaround for https://github.com/microsoft/terminal/issues/16860
        if (estimateOpenConsoleCommandLineLength(bundledConptyDll) < MAX_PATH) {
          Native.load(bundledConptyDll.absolutePath, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
        else {
          logger<ConPtyLibrary>().warn(
            "Skipping loading bundled conpty.dll, because its full path is too long: ${bundledConptyDll.absolutePath.length} characters"
          )
          Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
      }
      catch (e: Throwable) {
        logger<ConPtyLibrary>().warn("Failed to load bundled $CONPTY, fallback to $KERNEL32", e)
        Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
    }

    private fun estimateOpenConsoleCommandLineLength(conptyDll: File): Int {
      val parentDirPath = conptyDll.parentFile.absolutePath
      // Estimate OpenConsole.exe command line length by looking at how it's constructed:
      // https://github.com/microsoft/terminal/blob/a38388615e299658072f906578acd60e976fe787/src/winconpty/winconpty.cpp#L142
      val commandLine = "\"$parentDirPath\\OpenConsole.exe\" --headless --width 120 --height 100 --signal 0x950 --server 0x958"
      val reservedOptions = "--resizeQuirk --passthrough " // unused now, works as safety gap
      return commandLine.length + reservedOptions.length
    }
  }
}