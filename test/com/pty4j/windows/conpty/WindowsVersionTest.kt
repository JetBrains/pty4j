package com.pty4j.windows.conpty

import org.assertj.core.api.Assertions
import org.junit.Test

class WindowsVersionTest {
  @Test
  fun `#isGreaterThanOrEqualTo basic`() {
    version("Linux", "6.8.0-1035-aws").less(19041)

    // Windows 11
    version("Windows 11", "10.0").greaterOrEqual(19041)

    // Windows 10
    version("Windows 10", "10.0", 19045).greaterOrEqual(19041)
    version("Windows 10", "10.0", 19045).greaterOrEqual(19045)
    version("Windows 10", "10.0", 19045).less(22000)

    // Windows Server 2022
    version("Windows Server 2022", "10.0", 20348).greaterOrEqual(19041)
    version("Windows Server 2022", "10.0", 20348).greaterOrEqual(20348)
    version("Windows Server 2022", "10.0", 20348).less(22000)

    // Windows Server 2019
    version("Windows Server 2019", "10.0", 17763).less(19041)
    version("Windows Server 2019", "10.0", 17763).greaterOrEqual(17763)
    version("Windows Server 2019", "10.0", 17763).greaterOrEqual(14393)

    // Windows Server 2016
    version("Windows Server 2016", "10.0", 14393).less(19041)
    version("Windows Server 2016", "10.0", 14393).greaterOrEqual(14393)
    version("Windows Server 2016", "10.0", 14393).greaterOrEqual(10586)
  }

  @Test
  fun `#isGreaterThanOrEqualTo version parsing`() {
    version(null, "10.0").less(19041)
    version(null, "11.0").less(19041)
    version(null, null).less(19041)
    version("Windows", null, 18000).less(19041)
    version("Windows 11", null).greaterOrEqual(19041)
    version("Windows  11", null, 18000).less(19041)

    version("Windows ", "10.0", 18000).less(19041)
    version("Windows ", "11.0").greaterOrEqual(19041)

    version("Windows", "11").greaterOrEqual(19041)
    version("Windows", "11.10").greaterOrEqual(19041)
    version("Windows", "11.10.12").greaterOrEqual(19041)

    version("Windows", "10", 19045).greaterOrEqual(19041)
    version("Windows", "10.11", 19045).greaterOrEqual(19041)
    version("Windows", "10.11.12", 19045).greaterOrEqual(19041)

    version("Windows X", "10.0", 18000).less(19041)
    version("Windows 11.a", "10.0", 18000).less(19041)
    version("Windows 11a", "10.0", 18000).less(19041)
    version("Windows 11.0", "10.0", 18000).less(19041)
    version("Windows 11", "10.0").greaterOrEqual(19041)
    version("Windows 12", "10.0").greaterOrEqual(19041)
  }
}

internal fun version(
  sysPropOsName: String?,
  sysPropOsVersion: String?,
  fixedWinBuildNumber: Long? = null,
) = MockWindowsVersion(sysPropOsName, sysPropOsVersion, fixedWinBuildNumber)

internal class MockWindowsVersion(
  sysPropOsName: String?,
  sysPropOsVersion: String?,
  private val fixedBuildNumber: Long?,
) : WindowsVersion(sysPropOsName, sysPropOsVersion) {

  @Volatile
  private var buildNumberFetched: Boolean = false

  override val buildNumber: Long?
    get() = fixedBuildNumber.also {
      buildNumberFetched = true
    }

  internal fun less(buildNumberToCompare: Int): Unit = assertCompare(buildNumberToCompare, false)

  internal fun greaterOrEqual(buildNumberToCompare: Int): Unit = assertCompare(buildNumberToCompare, true)

  private fun assertCompare(buildNumberToCompare: Int, expectedGreaterOrEqual: Boolean) {
    val actualGreaterOrEqual = isGreaterThanOrEqualTo(buildNumberToCompare)

    Assertions.assertThat(stringify(actualGreaterOrEqual, buildNumberFetched))
      .describedAs("$this.isGreaterThanOrEqualTo($buildNumberToCompare)")
      .isEqualTo(stringify(expectedGreaterOrEqual, fixedBuildNumber != null))
  }

  private fun stringify(greaterOrEqual: Boolean, buildNumberFetched: Boolean): String {
    return "greaterOrEqual=$greaterOrEqual, buildNumberFetched=$buildNumberFetched"
  }

}
