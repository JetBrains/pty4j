package com.pty4j.util;

import com.pty4j.TestUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExtractedNativeTest {
  @Test
  public void locationsListedCorrectly() throws IOException {
    Path nativeFolder = TestUtil.getBuiltNativeFolder();
    List<String> fsNativePaths = new ArrayList<>();
    Files.walkFileTree(nativeFolder, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        fsNativePaths.add(nativeFolder.relativize(file).toString());
        return FileVisitResult.CONTINUE;
      }
    });
    Collections.sort(fsNativePaths);
    List<String> expectedNativePaths = Arrays.asList(ExtractedNative.LOCATIONS);
    Collections.sort(expectedNativePaths);
    Assert.assertEquals(expectedNativePaths, fsNativePaths);
  }

  @Test
  public void locationsCanBeFound() {
    for (String location : ExtractedNative.LOCATIONS) {
      int ind = location.indexOf("/");
      Assert.assertTrue(ind > 0);
      String platformName = location.substring(0, ind);
      int ind2 = location.indexOf("/", ind + 1);
      Assert.assertTrue(ind2 > 0);
      String archName = location.substring(ind + 1, ind2);
      Assert.assertEquals(-1, location.indexOf("/", ind2 + 1));
      new ExtractedNative(platformName, archName);
    }
  }
}
