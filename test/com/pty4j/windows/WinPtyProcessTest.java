package com.pty4j.windows;

import com.google.common.base.Charsets;
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

import java.io.*;

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
    String workingDirectory = getWorkingDirectory(process);
    if (workingDirectory != null) {
      assertEquals(workingDir, trimTrailingSlash(workingDirectory));
    }
    process.waitFor();
  }

  @Test
  public void testChangingWorkingDirectory() throws IOException, InterruptedException {
    File testNodeScript = new File(TestUtil.getTestDataPath(), "change-working-dir.bat");
    Assert.assertTrue(testNodeScript.isFile());
    String workingDir1 = testNodeScript.getAbsoluteFile().getParent();
    String workingDir2 = testNodeScript.getAbsoluteFile().getParentFile().getParentFile().getParent();
    String[] cmd = {testNodeScript.getAbsolutePath(), "1", workingDir2, "2"};
    PtyProcessBuilder builder = new PtyProcessBuilder(cmd)
      .setDirectory(workingDir1)
      .setConsole(false)
      .setEnvironment(System.getenv())
      .setCygwin(false);
    WinPtyProcess process = (WinPtyProcess) builder.start();
    printProcessOutput(process);
    String workingDirectory = getWorkingDirectory(process);
    if (workingDirectory != null) {
      assertEquals(workingDir1, trimTrailingSlash(workingDirectory));
    }
    PtyTest.assertProcessTerminatedNormally(process);
    workingDirectory = getWorkingDirectory(process);
    if (workingDirectory != null) {
      assertEquals(workingDir2, trimTrailingSlash(workingDirectory));
    }
  }

  @Nullable
  private static String getWorkingDirectory(@NotNull WinPtyProcess process) throws IOException {
    try {
      return process.getWorkingDirectory();
    }
    catch (IOException e) {
      if (!Platform.is64Bit()) {
        // it's impossible to get working directory from 32-bit java -> 32-bit winpty-agent.exe
        return null;
      }
      throw e;
    }
  }

  @Nullable
  private static String trimTrailingSlash(@Nullable String workingDirectory) {
    if (workingDirectory != null && workingDirectory.endsWith("\\")) {
      workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
    }
    return workingDirectory;
  }

  private static void printProcessOutput(@NotNull Process process) {
    Thread stdoutReader = new ReaderThread(new InputStreamReader(process.getInputStream(), Charsets.UTF_8), System.out);
    Thread stderrReader = new ReaderThread(new InputStreamReader(process.getErrorStream(), Charsets.UTF_8), System.err);
    stdoutReader.start();
    stderrReader.start();
  }

  private static class ReaderThread extends Thread {
    private final Reader myIn;
    private final PrintStream myOut;

    ReaderThread(@NotNull Reader in, @NotNull PrintStream out) {
      myIn = in;
      myOut = out;
    }

    @Override
    public void run() {
      try {
        char[] buf = new char[32 * 1024];
        while (true) {
          int count = myIn.read(buf);
          if (count < 0) {
            return;
          }
          myOut.print(new String(buf, 0, count));
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
