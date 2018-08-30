package com.pty4j.util;

import com.pty4j.TestUtil;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ExtractedNativeTest {

  private static final Logger LOG = Logger.getLogger(ExtractedNativeTest.class);

  private ClassLoader myPrevClassLoader;
  private String myResourceNamePrefix = null;

  @Before
  public void setUp() throws Exception {
    myPrevClassLoader = null;
    if (shouldAddNativeDirToClassPath()) {
      myPrevClassLoader = Thread.currentThread().getContextClassLoader();
      myResourceNamePrefix = "";
      URL url = TestUtil.getBuiltNativeFolder().toUri().toURL();
      LOG.info("Adding " + url + " to current thread classpath");
      ClassLoader urlCl = URLClassLoader.newInstance(new URL[]{url}, myPrevClassLoader);
      Thread.currentThread().setContextClassLoader(urlCl);
    }
  }

  private boolean shouldAddNativeDirToClassPath() {
    String resourceName = ExtractedNative.DEFAULT_RESOURCE_NAME_PREFIX + ExtractedNative.LOCATIONS[0];
    if (ExtractedNative.class.getResource(resourceName) != null) {
      return false;
    }
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    return contextClassLoader != null && contextClassLoader.getResource(resourceName) == null;
  }

  @After
  public void tearDown() {
    if (myPrevClassLoader != null) {
      Thread.currentThread().setContextClassLoader(myPrevClassLoader);
    }
    myResourceNamePrefix = null;
  }

  @Test
  public void locationsListedCorrectly() throws IOException {
    Path nativeFolder = TestUtil.getBuiltNativeFolder();
    List<String> fsNativePaths = new ArrayList<>();
    Files.walkFileTree(nativeFolder, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        fsNativePaths.add(nativeFolder.relativize(file).toString().replace("\\", "/"));
        return FileVisitResult.CONTINUE;
      }
    });
    Collections.sort(fsNativePaths);
    List<String> expectedNativePaths = Arrays.asList(ExtractedNative.LOCATIONS);
    Collections.sort(expectedNativePaths);
    Assert.assertEquals(expectedNativePaths, fsNativePaths);
  }

  @Test
  public void extractsFiles() {
    System.setProperty("pty4j.tmpdir", "/home/segrey/temp");
    Set<Pair<String, String>> pairs = new HashSet<>();
    for (String location : ExtractedNative.LOCATIONS) {
      int ind = location.indexOf("/");
      Assert.assertTrue(ind > 0);
      String platformName = location.substring(0, ind);
      int ind2 = location.indexOf("/", ind + 1);
      Assert.assertTrue(ind2 > 0);
      String archName = location.substring(ind + 1, ind2);
      Assert.assertEquals(-1, location.indexOf("/", ind2 + 1));
      pairs.add(Pair.create(platformName, archName));
    }
    for (Pair<String, String> pair : pairs) {
      File destDir = new ExtractedNative(pair.first, pair.second, myResourceNamePrefix).getDestDir();
      for (String location : ExtractedNative.LOCATIONS) {
        if (location.startsWith(pair.first + "/" + pair.second + "/")) {
          int ind = location.lastIndexOf("/");
          String name = location.substring(ind + 1);
          File file = new File(destDir, name);
          Assert.assertTrue("File exists " + file.getAbsolutePath(), file.isFile());
        }
      }
    }
  }

}
