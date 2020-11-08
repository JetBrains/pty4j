package com.pty4j.windows;

import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyTest;
import com.pty4j.TestUtil;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import testData.RepeatTextWithTimeout;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author traff
 */
public class WinPtyProcessTest {

  @Before
  public void setUp() {
    TestUtil.setLocalPtyLib();
    Assume.assumeTrue(Platform.isWindows());
  }

  @Test
  public void testCmdLineWithSpaces() {
    assertEquals(
      "C:\\Python35\\python.exe \"C:\\Program Files (x86)\\JetBrains\\PyCharm 5.0.2\\helpers\\pydev\\pydevd.py\"",
      WinPtyProcess.joinCmdArgs(new String[]{
        "C:\\Python35\\python.exe",
        "C:\\Program Files (x86)\\JetBrains\\PyCharm 5.0.2\\helpers\\pydev\\pydevd.py"
      })
    );
  }

  @Test
  public void testCmdLineWithSpacesAndQuotes() {
    assertEquals(
      "test \"\\\"quoted with spaces\\\"\"",
      WinPtyProcess.joinCmdArgs(new String[]{"test", "\\\"quoted with spaces\\\""})
    );
  }

  @Test
  public void testGettingWorkingDirectory() throws Exception {
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "1", "1000", "Hello, World\n");

    String workingDir = new File("test\\testData").getAbsolutePath();
    PtyProcessBuilder builder = new PtyProcessBuilder(cmd)
      .setDirectory(workingDir)
      .setConsole(false)
      .setCygwin(false);
    WinPtyProcess process = (WinPtyProcess) builder.start();
    assertWorkingDirectory(workingDir, process);
    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testChangingWorkingDirectory() throws IOException, InterruptedException {
    File script = new File(TestUtil.getTestDataPath(), "change-working-dir.bat");
    Assert.assertTrue(script.isFile());
    String workingDir1 = script.getAbsoluteFile().getParent();
    String workingDir2 = script.getAbsoluteFile().getParentFile().getParentFile().getParent();
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{script.getAbsolutePath()})
      .setDirectory(workingDir1)
      .setConsole(false)
      .setEnvironment(System.getenv())
      .setCygwin(false);
    WinPtyProcess process = (WinPtyProcess) builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    stdout.assertEndsWith("Current directory is " + workingDir1 + "\r\ncd to new directory:");
    assertWorkingDirectory(workingDir1, process);
    PtyTest.writeToStdinAndFlush(process, workingDir2, true);

    stdout.assertEndsWith("New current directory is " + workingDir2 + "\r\nPress Enter to exit");
    assertWorkingDirectory(workingDir2, process);
    PtyTest.writeToStdinAndFlush(process, "any non empty text to complete successfully", true);

    PtyTest.assertProcessTerminatedNormally(process);
  }

  private static void assertWorkingDirectory(@NotNull String expectedWorkingDir,
                                             @NotNull WinPtyProcess process) throws IOException {
    // it's impossible to get working directory from 32-bit java -> 32-bit winpty-agent.exe
    if (Platform.is64Bit()) {
      assertEquals(expectedWorkingDir, trimTrailingSlash(process.getWorkingDirectory()));
    }
  }

  @Nullable
  private static String trimTrailingSlash(@Nullable String workingDirectory) {
    if (workingDirectory != null && workingDirectory.endsWith("\\")) {
      workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
    }
    return workingDirectory;
  }
}
