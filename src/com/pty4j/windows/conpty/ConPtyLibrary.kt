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
    private const val KERNEL32: String = "kernel32"
    @Suppress("SpellCheckingInspection")
    private const val CONPTY: String = "conpty.dll"

    @JvmStatic
    val instance: ConPtyLibrary by lazy {
      val bundledLibraryPath: String? = try {
        PtyUtil.resolveNativeFile(CONPTY).absolutePath
      }
      catch (e: Exception) {
        logger<ConPtyLibrary>().warn("Cannot find bundled $CONPTY, fallback to $KERNEL32", e)
        null
      }
      if (bundledLibraryPath != null) {
        try {
          return@lazy Native.load(bundledLibraryPath, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
        catch (e: Exception) {
          logger<ConPtyLibrary>().warn("Failed to load bundled $bundledLibraryPath, fallback to $KERNEL32", e)
        }
      }
      return@lazy Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
  }
}