package com.pty4j;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;

/**
 * @author traff
 */
public class TestPathsManager {
  @NotNull
  public static String getTestDataPath() {
    return Paths.get("test/testData").toAbsolutePath().normalize().toString();
  }
}
