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
import java.util.Locale

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
    private const val DISABLE_BUNDLED_CONPTY_PROP_NAME: String = "com.pty4j.windows.disable.bundled.conpty"

    @JvmStatic
    val instance: ConPtyLibrary
      get() = libraryWithName.first

    @JvmStatic
    val isBundled: Boolean
      get() = runCatching { libraryWithName.second != KERNEL32 }.getOrDefault(false)

    private val libraryWithName: Pair<ConPtyLibrary, String> by lazy {
      //val osName = System.getProperty("os.name")
      //if (shouldUseSystemConPty(osName)) {
      //  logger<ConPtyLibrary>().info("Loading bundled $CONPTY is disabled on $osName due to missing icu.dll")
      //  return@lazy loadLibrary(KERNEL32)
      //}
      if (System.getProperty(DISABLE_BUNDLED_CONPTY_PROP_NAME).toBoolean()) {
        logger<ConPtyLibrary>().warn("Loading bundled $CONPTY is disabled by '$DISABLE_BUNDLED_CONPTY_PROP_NAME'")
        return@lazy loadLibrary(KERNEL32)
      }
      try {
        val bundledConptyDll = PtyUtil.resolveNativeFile(CONPTY)
        loadLibrary(bundledConptyDll.absolutePath)
      }
      catch (e: Throwable) {
        logger<ConPtyLibrary>().warn("Failed to load bundled $CONPTY, fallback to $KERNEL32", e)
        loadLibrary(KERNEL32)
      }
    }

    private fun loadLibrary(name: String): Pair<ConPtyLibrary, String> {
      return Native.load(name, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS) to name
    }
    
    private fun shouldUseSystemConPty(osName: String): Boolean {
      val lowerCasedOsName = osName.lowercase(Locale.ENGLISH)
      return "windows server 2019" == lowerCasedOsName || "windows server 2016" == lowerCasedOsName
    }
  }
}
