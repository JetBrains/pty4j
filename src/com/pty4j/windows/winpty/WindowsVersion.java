package com.pty4j.windows.winpty;

import com.pty4j.util.LazyValue;
import com.sun.jna.platform.win32.VerRsrc;
import com.sun.jna.platform.win32.VersionUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WindowsVersion {

  private static final Logger LOG = LoggerFactory.getLogger(WindowsVersion.class);
  private static final LazyValue<Version> myVersionValue = new LazyValue<>(WindowsVersion::doGetVersion);

  static void getVersion() {
    try {
      myVersionValue.getValue();
    }
    catch (Exception e) {
      LOG.warn("Cannot get Windows version", e);
    }
  }

  private static @NotNull Version doGetVersion() {
    try {
      VerRsrc.VS_FIXEDFILEINFO x = VersionUtil.getFileVersionInfo("kernel32.dll");
      Version version = new Version(x.getProductVersionMajor(), x.getProductVersionMinor(), x.getProductVersionRevision());
      LOG.info("Windows version: {}", version);
      logSystemProperty("os.name");
      logSystemProperty("os.version");
      return version;
    }
    catch (Exception e) {
      LOG.info("Cannot get Windows version", e);
    }
    return new Version(-1, -1, -1);
  }

  private static void logSystemProperty(@NotNull String name) {
    LOG.info("System property: {}={}", name, System.getProperty(name));
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
