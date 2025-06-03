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
    val instance: ConPtyLibrary by lazy {
      val osName = System.getProperty("os.name")
      if (shouldUseSystemConPty(osName)) {
        logger<ConPtyLibrary>().info("Loading bundled $CONPTY is disabled on $osName due to missing icu.dll")
        return@lazy Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
      if (System.getProperty(DISABLE_BUNDLED_CONPTY_PROP_NAME).toBoolean()) {
        logger<ConPtyLibrary>().warn("Loading bundled $CONPTY is disabled by '$DISABLE_BUNDLED_CONPTY_PROP_NAME'")
        return@lazy Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
      try {
        val bundledConptyDll = PtyUtil.resolveNativeFile(CONPTY)
        Native.load(bundledConptyDll.absolutePath, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
      catch (e: Throwable) {
        logger<ConPtyLibrary>().warn("Failed to load bundled $CONPTY, fallback to $KERNEL32", e)
        Native.load(KERNEL32, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      }
    }

    private fun shouldUseSystemConPty(osName: String): Boolean {
      val lowerCasedOsName = osName.lowercase(Locale.ENGLISH)
      return "windows server 2019" == lowerCasedOsName || "windows server 2016" == lowerCasedOsName
    }
  }
}
