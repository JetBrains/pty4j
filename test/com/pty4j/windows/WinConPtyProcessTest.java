package com.pty4j.windows;

import com.pty4j.*;
import com.pty4j.windows.conpty.WinConPtyProcess;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import testData.ConsoleSizeReporter;
import testData.EnvPrinter;
import testData.Printer;
import testData.PromptReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("TextBlockMigration")
public class WinConPtyProcessTest {

  @Before
  public void setUp() {
    Assume.assumeTrue(Platform.isWindows());
    TestUtil.useLocalNativeLib(true);
  }

  @After
  public void tearDown() {
    TestUtil.useLocalNativeLib(false);
  }

  private @NotNull PtyProcessBuilder builder() {
    return new PtyProcessBuilder() {
      @Override
      public @NotNull PtyProcess start() throws IOException {
        PtyProcess process = super.start();
        assertTrue(process instanceof WinConPtyProcess);
        return process;
      }
    }.setUseWinConPty(true).setConsole(false);
  }

  @Test
  public void testBasic() throws Exception {
    PtyProcess process = builder().setCommand(TestUtil.getJavaCommand(Printer.class)).start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(Printer.STDOUT + "\r\n" + Printer.STDERR + "\r\n");
    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());
    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testPromptReader() throws Exception {
    PtyProcessBuilder builder = builder().setCommand(TestUtil.getJavaCommand(PromptReader.class));
    PtyProcess process = builder.start();

    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith("Enter:");
    PtyTest.writeToStdinAndFlush(process, "Hi", true);
    stdout.assertEndsWith("Enter:Hi\r\nRead:Hi\r\nEnter:");

    PtyTest.writeToStdinAndFlush(process, "", true);

    stdout.assertEndsWith("Enter:\r\nexit: empty line\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());

    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testPassingEnv() throws Exception {
    PtyProcess process = builder().setCommand(TestUtil.getJavaCommand(EnvPrinter.class))
            .setEnvironment(mergeCustomAndSystemEnvironment(Map.of("foo", "bar", "HELLO", "WORLD")))
            .start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith("Enter env name:");
    PtyTest.writeToStdinAndFlush(process, "foo", true);
    stdout.assertEndsWith("Enter env name:foo\r\n" +
                          "Env foo=bar\r\n" +
                          "Enter env name:");

    PtyTest.writeToStdinAndFlush(process, "HELLO", true);
    stdout.assertEndsWith("Enter env name:HELLO\r\n" +
                          "Env HELLO=WORLD\r\n" +
                          "Enter env name:");

    PtyTest.writeToStdinAndFlush(process, "", true);

    stdout.assertEndsWith("Enter env name:\r\n" +
                          "exit: empty env name\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());

    PtyTest.assertProcessTerminatedNormally(process);
  }

  private static @NotNull Map<String, String> mergeCustomAndSystemEnvironment(@NotNull Map<String, String> customEnv) {
    Map<String, String> env = new HashMap<>(System.getenv());
    env.putAll(customEnv);
    return env;
  }

  @Test
  public void testWorkingDirectory() throws Exception {
    Path dir = getProjectRoot().resolve("test\\testData").normalize();
    PtyProcess process = builder().setCommand(new String[] {"cmd.exe", "/C", "echo %cd%"})
            .setDirectory(dir.toString()).start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(dir + "\r\n");
    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());
    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testResize() throws Exception {
    WinSize initialSize = new WinSize(111, 11);
    PtyProcess process = builder().setCommand(TestUtil.getJavaCommand(ConsoleSizeReporter.class))
            .setInitialColumns(initialSize.getColumns())
            .setInitialRows(initialSize.getRows())
            .start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    Assert.assertEquals(initialSize, process.getWinSize());
    stdout.assertEndsWith("columns: 111, rows: 11\r\n");

    WinSize newSize = new WinSize(140, 80);
    PtyTest.assertAlive(process);
    process.setWinSize(newSize);
    PtyTest.assertAlive(process);
    Assert.assertEquals(newSize, process.getWinSize());
    PtyTest.writeToStdinAndFlush(process, ConsoleSizeReporter.PRINT_SIZE, true);
    stdout.assertEndsWith(ConsoleSizeReporter.PRINT_SIZE + "\r\ncolumns: 140, rows: 80\r\n");

    PtyTest.writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
    stdout.awaitFinish();
    stderr.awaitFinish();
    PtyTest.assertProcessTerminatedNormally(process);
    PtyTest.checkGetSetSizeFailed(process);
  }

  @Test
  public void testResizeEventInRawMode() throws Exception {
    WinSize initialSize = new WinSize(200, 5);
    File nodeExe = TestUtil.findInPath(Platform.isWindows() ? "node.exe" : "node");
    if (nodeExe == null) {
      return;
    }
    PtyProcess process = builder().setCommand(new String[]{
                    nodeExe.getAbsolutePath(),
                    new File(TestUtil.getTestDataPath(), "print-terminal-size-in-raw-mode.js").getAbsolutePath()
            }).setInitialColumns(initialSize.getColumns())
            .setInitialRows(initialSize.getRows())
            .start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    Assert.assertEquals(initialSize, process.getWinSize());
    stdout.assertEndsWith("init: columns: " + initialSize.getColumns() + ", rows: " + initialSize.getRows() + "\r\n");
    for (int columns = 50; columns < 60; columns += 2) {
      for (int rows = 10; rows < 20; rows += 2) {
        WinSize newSize = new WinSize(columns, rows);
        process.setWinSize(newSize);
        Assert.assertEquals(newSize, process.getWinSize());
        PtyTest.assertAlive(process);
        stdout.assertEndsWith("resize: columns: " + columns + ", rows: " + rows + "\r\n");
      }
    }
    PtyTest.writeToStdinAndFlush(process, "q", false);
    stdout.awaitFinish();
    stderr.awaitFinish();
    PtyTest.assertProcessTerminatedNormally(process);
    PtyTest.checkGetSetSizeFailed(process);
  }

  @Test
  public void testGettingChangedWorkingDirectory() throws Exception {
    Path workingDir = getProjectRoot();
    PtyProcessBuilder builder = builder().setCommand(new String[]{"cmd.exe"})
        // configure cmd prompt as "currentPath>"
        // https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/prompt
        .setEnvironment(mergeCustomAndSystemEnvironment(Map.of("PROMPT", "$p$g")))
        .setDirectory(workingDir.toString());
    WinConPtyProcess process = (WinConPtyProcess)builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(workingDir + ">");
    assertWorkingDirectory(workingDir.toString(), process);

    Path newWorkingDir = workingDir.resolve("test");
    PtyTest.writeToStdinAndFlush(process, "cd " + newWorkingDir, true);
    stdout.assertEndsWith(newWorkingDir + ">");
    assertWorkingDirectory(newWorkingDir.toString(), process);

    PtyTest.writeToStdinAndFlush(process, "exit", true);

    stdout.awaitFinish();
    stderr.awaitFinish();

    PtyTest.assertProcessTerminatedNormally(process);
  }

  private static void assertWorkingDirectory(@NotNull String expectedWorkingDir,
                                             @NotNull WinConPtyProcess process) throws IOException {
    // it's impossible to get working directory from 32-bit java -> 32-bit winpty-agent.exe
    if (Platform.is64Bit()) {
      String path = Path.of(process.getWorkingDirectory()).normalize().toAbsolutePath().toString();
      assertEquals(expectedWorkingDir, path);
    }
  }

  @Test
  public void testDestroyJava() throws Exception {
    PtyProcessBuilder builder = builder().setCommand(TestUtil.getJavaCommand(PromptReader.class));
    PtyProcess process = builder.start();

    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith("Enter:");
    process.destroy();

    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());

    PtyTest.assertProcessTerminated(1, process);
  }

  @Test
  public void testDestroyCmd() throws Exception {
    PtyProcessBuilder builder = builder().setCommand(new String[] {"cmd.exe"})
        .setEnvironment(mergeCustomAndSystemEnvironment(Map.of("PROMPT", "$g")));
    PtyProcess process = builder.start();

    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(">");
    process.destroy();

    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());

    PtyTest.assertProcessTerminated(1, process);
  }

  @Test
  public void testConsoleProcessCount() throws Exception {
    Path rootDir = getProjectRoot();
    PtyProcessBuilder builder = builder().setCommand(new String[]{"cmd.exe"})
        .setEnvironment(mergeCustomAndSystemEnvironment(Collections.singletonMap("PROMPT", "$P$G")))
        .setDirectory(rootDir.toString());
    WinConPtyProcess process = (WinConPtyProcess)builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(rootDir + ">");
    assertEquals(1, process.getConsoleProcessCount());
    PtyTest.writeToStdinAndFlush(process, "cd test", true);
    Path testDir = rootDir.resolve("test");
    stdout.assertEndsWith(testDir + ">");
    PtyTest.writeToStdinAndFlush(process, "cmd.exe", true);
    stdout.assertEndsWith(testDir + ">");
    assertEquals(2, process.getConsoleProcessCount());

    PtyTest.writeToStdinAndFlush(process, "exit", true);
    PtyTest.writeToStdinAndFlush(process, "exit", true);
    PtyTest.assertProcessTerminatedNormally(process);
    assertEquals("", stderr.getOutput());
  }

  private static @NotNull Path getProjectRoot() {
    Path root = Paths.get(".").toAbsolutePath().normalize();
    if (!root.getFileName().toString().equals("pty4j")) {
      assertTrue(Files.isRegularFile(root.resolve("VERSION")));
    }
    return root;
  }

  @Test
  public void testReadAllOutput() throws Exception {
    PtyProcessBuilder builder = builder().setCommand(new String[]{"cmd.exe", "/C", "echo Hello"});
    WinConPtyProcess process = (WinConPtyProcess)builder.start();
    PtyTest.assertProcessTerminatedNormally(process);
    Thread.sleep(200); // emulate postponed reading
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    stdout.awaitFinish();
    assertTrue(stdout.getOutput(), stdout.getOutput().contains("Hello"));
  }

  @Test
  public void testLargeOscBodyPassedUntouched() throws Exception {
    int A_CNT = 1024 * 1024 * 2;
    // https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_powershell_exe
    PtyProcessBuilder builder = builder().setCommand(new String[]{
            "powershell.exe",
            "-ExecutionPolicy", "Bypass",
            "-File", TestUtil.getTestDataFilePath("win/large-osc-seq.ps1")
    }).setEnvironment(mergeCustomAndSystemEnvironment(Map.of("A_CNT", String.valueOf(A_CNT))));
    WinConPtyProcess process = (WinConPtyProcess)builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.assertProcessTerminatedNormally(process);
    stdout.awaitFinish();
    String output = stdout.getOutput();
    assertTrue(output, output.contains("\u001b]1341;" + "A".repeat(A_CNT) + "\u0007"));
  }

  @Test
  public void textCarryingOverWithoutLineBreaks() throws Exception {
    int A_CNT = 1000;
    PtyProcessBuilder builder = builder().setCommand(new String[]{
            "powershell.exe",
            "-ExecutionPolicy", "Bypass",
            "-File", TestUtil.getTestDataFilePath("win/char-printer.ps1")
    }).setEnvironment(mergeCustomAndSystemEnvironment(Map.of("A_CNT", String.valueOf(A_CNT))))
            .setInitialColumns(20)
            .setInitialRows(60)
            ;
    WinConPtyProcess process = (WinConPtyProcess)builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.assertProcessTerminatedNormally(process);
    stdout.awaitFinish();
    String output = stdout.getOutput();
    assertTrue(output, output.contains("Q".repeat(A_CNT) + "Done"));
  }

  @Test
  public void testSuspendedProcessCallback() throws Exception {
    AtomicBoolean callbackInvoked = new AtomicBoolean(false);
    PtyProcessBuilder builder = builder()
      .setCommand(new String[]{"cmd.exe", "/C", "echo Hello"})
      .setWindowsSuspendedProcessCallback(value -> {
        callbackInvoked.set(true);
      });
    WinConPtyProcess process = (WinConPtyProcess)builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    stdout.awaitFinish();
    assertTrue("Windows suspended process callback has not been invoked", callbackInvoked.get());
  }
}
