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

  @Suppress("SpellCheckingInspection")
  companion object {
    private const val CONPTY: String = "conpty.dll"
    private const val KERNEL32: String = "kernel32"
    private const val DISABLE_BUNDLED_CONPTY_PROP_NAME: String = "com.pty4j.windows.disable.bundled.conpty"

    /**
     * The Windows OS minimal build number required to load the bundled ConPTY.
     * The bundled ConPTY is a recent release that requires a modern Windows OS.
     * For example, it expects icu.dll (International Components for Unicode) to be available on the system.
     * Since [Windows Terminal](https://github.com/microsoft/terminal) requires Windows 10 2004 (build 19041) or later,
     * let's adopt the same requirement for consistency and safety.
     */
    private const val BUNDLED_CONPTY_OS_MIN_BUILD_NUMBER: Int = 19041

    @JvmStatic
    val instance: ConPtyLibrary
      get() = libraryWithName.first

    @JvmStatic
    val isBundled: Boolean
      get() = runCatching { libraryWithName.second != KERNEL32 }.getOrDefault(false)

    private val libraryWithName: Pair<ConPtyLibrary, String> by lazy {
      if (System.getProperty(DISABLE_BUNDLED_CONPTY_PROP_NAME).toBoolean()) {
        logger<ConPtyLibrary>().warn("Bundled $CONPTY is disabled by '$DISABLE_BUNDLED_CONPTY_PROP_NAME'")
        return@lazy loadLibrary(KERNEL32)
      }
      val osVersion = WindowsVersionImpl()
      if (!osVersion.isGreaterThanOrEqualTo(BUNDLED_CONPTY_OS_MIN_BUILD_NUMBER)) {
        logger<ConPtyLibrary>().info("Bundled $CONPTY is disabled: OS build number requirement not met $osVersion")
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
      val library = Native.load(name, ConPtyLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
      val type = if (name == KERNEL32) "system" else "bundled"
      logger<ConPtyLibrary>().info("Loaded $type ConPTY from $name")
      return library to name
    }
  }
}
