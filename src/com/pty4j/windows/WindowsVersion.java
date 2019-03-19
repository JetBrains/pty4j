package com.pty4j.windows;

import com.pty4j.util.LazyValue;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

class WindowsVersion {

  private static final Logger LOG = Logger.getLogger(WindowsVersion.class);
  private static LazyValue<Version> myVersionValue = new LazyValue<Version>(WindowsVersion::getVersion);

  @NotNull
  public static Version getVersion() {
    try {
      Kernel32 kernel = Kernel32.INSTANCE;
      WinNT.OSVERSIONINFOEX vex = new WinNT.OSVERSIONINFOEX();
      if (kernel.GetVersionEx(vex)) {
        Version version = new Version(vex.dwMajorVersion.longValue(),
                                      vex.dwMinorVersion.longValue(),
                                      vex.dwBuildNumber.longValue());
        LOG.info("Windows version: " + version);
        return version;
      }
      LOG.info("Cannot determine Windows version");
    }
    catch (Exception e) {
      LOG.info("Cannot get Windows version", e);
    }
    return new Version(-1, -1, -1);
  }

  static boolean isEqualTo(long majorVersion, long minorVersion, long buildNumber) {
    Version version;
    try {
      version = myVersionValue.getValue();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return majorVersion == version.myMajorVersion &&
           minorVersion == version.myMinorVersion &&
           buildNumber == version.myBuildNumber;
  }

  private static class Version {

    private final long myMajorVersion;
    private final long myMinorVersion;
    private final long myBuildNumber;

    Version(long majorVersion, long minorVersion, long buildNumber) {
      myMajorVersion = majorVersion;
      myMinorVersion = minorVersion;
      myBuildNumber = buildNumber;
    }

    @Override
    public String toString() {
      return myMajorVersion + "." + myMinorVersion + "." + myBuildNumber;
    }
  }
}
