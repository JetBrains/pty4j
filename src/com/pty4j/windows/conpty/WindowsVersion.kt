package com.pty4j.windows.conpty

import com.pty4j.util.logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlin.math.max
import kotlin.text.split

internal abstract class WindowsVersion(private val sysPropOsName: String?, private val sysPropOsVersion: String?) {

  /**
   * Returns the Windows OS build number.
   * For example, `winver` command may display "OS Build 19045.6216".
   * In this case, 19045 is the build number, and 6216 is the update revision number.
   */
  protected abstract val buildNumber: Long?

  private val buildNumberLazy: Lazy<Long?> = lazy { buildNumber }

  fun isGreaterThanOrEqualTo(buildNumber: Int): Boolean {
    if (!isWindows()) {
      return false
    }
    if (buildNumber <= WIN11_MIN_BUILD_NUMBER && isWindows11OrNewer()) {
      // fast path for Windows 11 without fetching a build number using native call
      return true
    }
    val currentBuildNumber = buildNumberLazy.value
    return currentBuildNumber != null && currentBuildNumber >= buildNumber
  }

  private fun isWindows(): Boolean {
    return sysPropOsName != null && sysPropOsName.lowercase().startsWith(NAME_PREFIX_LOWERCASE)
  }

  /**
   * Returns true if the current Windows version is 11 or newer.
   * Similar to
   * ```
   * com.intellij.util.system.OS.Windows.isAtLeast(11, 0)
   * ```
   */
  private fun isWindows11OrNewer(): Boolean {
    sysPropOsName ?: return false
    // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
    val nameVersion = sysPropOsName.lowercase().removePrefix("$NAME_PREFIX_LOWERCASE ").toIntOrNull() ?: -1
    val version = max(nameVersion, parseMajorVersion())
    return version >= 11
  }

  private fun parseMajorVersion(): Int {
    val versions = sysPropOsVersion.orEmpty().split(".", limit = 2)
    return versions.getOrNull(0)?.toIntOrNull() ?: -1
  }

  override fun toString(): String {
    val buildNumberStr = if (buildNumberLazy.isInitialized()) buildNumberLazy.value.toString() else "N/A"
    return "(os.name: $sysPropOsName, os.version: $sysPropOsVersion, buildNumber: $buildNumberStr)"
  }
}

/**
 * All Windows 11 build numbers >= 22000, see
 * https://learn.microsoft.com/en-us/windows/release-health/windows11-release-information
 */
private const val WIN11_MIN_BUILD_NUMBER: Int = 22000
private const val NAME_PREFIX_LOWERCASE: String = "windows"

internal class WindowsVersionImpl : WindowsVersion(System.getProperty("os.name"), System.getProperty("os.version")) {
  override val buildNumber: Long?
    // See `com.intellij.openapi.util.WinBuildNumber#getWinBuildNumber`
    get() = try {
      // this key is undocumented but mentioned heavily all over the Internet
      Advapi32Util.registryGetStringValue(
        WinReg.HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
        "CurrentBuildNumber"
      ).toLong()
    }
    catch (e: Exception) {
      logger<WindowsVersionImpl>().warn("Unrecognized Windows build number", e)
      null
    }
}
