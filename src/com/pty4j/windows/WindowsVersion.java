package com.pty4j.windows;

import com.pty4j.util.LazyValue;
import com.sun.jna.platform.win32.VerRsrc;
import com.sun.jna.platform.win32.VersionUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

class WindowsVersion {

  private static final Logger LOG = Logger.getLogger(WindowsVersion.class);
  private static LazyValue<Version> myVersionValue = new LazyValue<Version>(new Callable<Version>() {
    @Override
    public Version call() throws Exception {
      return getVersion();
    }
  });

  @NotNull
  public static Version getVersion() {
    try {
      VerRsrc.VS_FIXEDFILEINFO x = VersionUtil.getFileVersionInfo("kernel32.dll");
      Version version = new Version(x.getProductVersionMajor(), x.getProductVersionMinor(), x.getProductVersionRevision());
      LOG.info("Windows version: " + version);
      return version;
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
