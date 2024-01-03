package com.pty4j.windows.conpty

import com.pty4j.util.PtyUtil
import com.pty4j.util.logger
import com.pty4j.windows.conpty.WinEx.*
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.W32APIOptions

@Suppress("FunctionName")
internal interface ConPtyLibrary : Library {
  fun CreatePseudoConsole(size: COORDByValue,
                          hInput: WinNT.HANDLE,
                          hOutput: WinNT.HANDLE,
                          dwFlags: DWORD,
                          phPC: HPCONByReference): HRESULT

  fun ClosePseudoConsole(hPC: HPCON)

  fun ResizePseudoConsole(hPC: HPCON, size: COORDByValue): HRESULT

  companion object {
    @Suppress("SpellCheckingInspection")
    private const val CONPTY: String = "conpty.dll"
    private const val KERNEL32: String = "kernel32"

    @JvmStatic
    val instance: ConPtyLibrary by lazy {
      try {
        val bundledLibraryPath = PtyUtil.resolveNativeFile(CONPTY).absolutePath
        Native.load(bundledLibraryPath, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
      catch (e: Throwable) {
        logger<ConPtyLibrary>().warn("Failed to load bundled $CONPTY, fallback to $KERNEL32", e)
        Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
    }
  }
}