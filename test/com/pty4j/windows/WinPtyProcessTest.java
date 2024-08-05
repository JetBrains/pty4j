package com.pty4j.windows;

import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyTest;
import com.pty4j.TestUtil;
import com.pty4j.windows.winpty.WinPtyProcess;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import testData.RepeatTextWithTimeout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author traff
 */
public class WinPtyProcessTest {

  @Before
  public void setUp() {
    TestUtil.setLocalPtyLib();
    Assume.assumeTrue(Platform.isWindows());
  }

  private static void assumeWorkingDirectoryAvailable() {
    Assume.assumeTrue("Impossible to get working directory from 32-bit java -> 32-bit winpty-agent.exe", Platform.is64Bit());
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
    assumeWorkingDirectoryAvailable();
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
  public void changeWorkingDirectoryInCommandPrompt() throws IOException, InterruptedException {
    assumeWorkingDirectoryAvailable();

    Path script = Path.of(TestUtil.getTestDataFilePath("win/change-working-directory.bat"));
    String workingDir1 = script.toAbsolutePath().getParent().toString();
    String workingDir2 = script.toAbsolutePath().getParent().getParent().getParent().toString();
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{script.toString()})
      .setDirectory(workingDir1)
      .setConsole(false)
      .setEnvironment(System.getenv())
      .setCygwin(false);
    WinPtyProcess process = (WinPtyProcess) builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    checkWorkingDirectoryAndChange(process, stdout, workingDir1, "Enter new working directory:", workingDir2);
    checkWorkingDirectoryAndChange(process, stdout, workingDir2, "Press Enter to exit", "anything non-empty");

    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Ignore("WinPtyProcess.getWorkingDirectory() doesn't work for PowerShell")
  @Test
  public void changeWorkingDirectoryInPowerShell() throws IOException, InterruptedException {
    assumeWorkingDirectoryAvailable();

    List<String> command = WindowsTestUtil.getPowerShellScriptCommand("win/change-working-directory.ps1");
    String initialWorkingDirectory = Path.of(TestUtil.getTestDataPath()).resolve("win").toString();
    PtyProcessBuilder builder = new PtyProcessBuilder(command.toArray(String[]::new))
      .setDirectory(initialWorkingDirectory)
      .setConsole(false)
      .setInitialColumns(("Working directory:" + initialWorkingDirectory).length() + /* safety gap */ 10);
    WinPtyProcess process = (WinPtyProcess) builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    String newWorkingDirectory = Path.of(TestUtil.getTestDataPath()).toString();
    checkWorkingDirectoryAndChange(process, stdout, initialWorkingDirectory, "Enter new working directory:", newWorkingDirectory);
    checkWorkingDirectoryAndChange(process, stdout, newWorkingDirectory, "Enter anything:", "anything non-empty");

    PtyTest.assertProcessTerminatedNormally(process);
  }

  private static void checkWorkingDirectoryAndChange(@NotNull WinPtyProcess process,
                                                     @NotNull PtyTest.Gobbler stdout,
                                                     @NotNull String expectedWorkingDir,
                                                     @NotNull String promptText,
                                                     @NotNull String newWorkingDirectory) throws IOException {
    stdout.assertEndsWith("\r\n" + promptText);
    String actualWorkingDir = assertWorkingDirectory(expectedWorkingDir, process);
    stdout.assertEndsWith("Working directory is " + actualWorkingDir + "\r\n" + promptText);
    PtyTest.writeToStdinAndFlush(process, newWorkingDirectory, true);
  }

  private static @NotNull String assertWorkingDirectory(@NotNull String expectedWorkingDir,
                                                        @NotNull WinPtyProcess process) throws IOException {
    String actualWorkingDir = trimTrailingSlash(process.getWorkingDirectory());
    assertNotNull("Cannot get working directory", actualWorkingDir);
    assertEquals(expectedWorkingDir, actualWorkingDir);
    return actualWorkingDir;
  }

  @Nullable
  private static String trimTrailingSlash(@Nullable String workingDirectory) {
    if (workingDirectory != null && workingDirectory.endsWith("\\")) {
      workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
    }
    return workingDirectory;
  }
}
