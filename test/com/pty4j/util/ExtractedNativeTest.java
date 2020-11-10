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
    Set<String> osArchSubPaths = new HashSet<>();
    for (String location : ExtractedNative.LOCATIONS) {
      int ind = location.lastIndexOf("/");
      Assert.assertTrue(ind > 0);
      osArchSubPaths.add(location.substring(0, ind));
    }
    for (String osArchSubPath : osArchSubPaths) {
      File destDir = new ExtractedNative(osArchSubPath, myResourceNamePrefix).getDestDir();
      for (String location : ExtractedNative.LOCATIONS) {
        if (location.startsWith(osArchSubPath + "/")) {
          int ind = location.lastIndexOf("/");
          String name = location.substring(ind + 1);
          File file = new File(destDir, name);
          Assert.assertTrue("File doesn't exist " + file.getAbsolutePath(), file.isFile());
        }
      }
    }
  }

}
