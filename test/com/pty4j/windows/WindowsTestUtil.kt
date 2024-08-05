package com.pty4j.windows

import com.pty4j.TestUtil

internal object WindowsTestUtil {
  /**
   * @param ps1FileRelativePath path to .ps1 file relative to [testData] folder
   */
  @JvmStatic
  fun getPowerShellScriptCommand(ps1FileRelativePath: String): List<String> {
    // https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_powershell_exe
    return listOf(
      "powershell.exe",
      "-ExecutionPolicy", "Bypass",
      "-File", TestUtil.getTestDataFilePath(ps1FileRelativePath)
    )
  }

}
