/*
 * JPty - A small PTY interface for Java.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.pty4j;


import com.pty4j.unix.PtyHelpers;
import com.pty4j.windows.winpty.WinPtyProcess;
import com.sun.jna.Platform;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import testData.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Test cases for {@link com.pty4j.unix.PtyHelpers}.
 */
public class PtyTest extends TestCase {

  private static final int WAIT_TIMEOUT_SECONDS = TestUtil.getTestWaitTimeoutSeconds();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestUtil.setLocalPtyLib();
  }

  public void testDestroy() throws Exception {
    PtyProcess process = new PtyProcessBuilder(
      TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000000", "Hello, World")
    ).start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    process.getOutputStream(); // to avoid closing unused process's stdin in destroy method
    stdout.assertEndsWith("#1: Hello, World\r\n");
    process.destroy();
    if (Platform.isWindows()) {
      assertProcessTerminatedAbnormally(process);
    }
    else {
      assertProcessTerminatedBySignal(PtyHelpers.SIGTERM, process);
    }
  }

  public void testDestroyForcibly() throws Exception {
    if (Platform.isWindows()) return;
    PtyProcess process = new PtyProcessBuilder(TestUtil.getJavaCommand(UndestroyablePromptReader.class)).start();
    Gobbler stdout = startStdoutGobbler(process);
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(process, "init", true);
    stdout.assertEndsWith("Enter:init\r\nRead:init\r\nEnter:");
    process.destroy();
    Thread.sleep(300); // let SIGTERM be processed
    writeToStdinAndFlush(process, "SIGTERM ignored", true);
    stdout.assertEndsWith("Enter:SIGTERM ignored\r\nRead:SIGTERM ignored\r\nEnter:");
    process.destroyForcibly();
    assertProcessTerminatedBySignal(PtyHelpers.SIGKILL, process);
  }

  public void testSendSigInt() throws Exception {
    if (Platform.isWindows()) return;
    PtyProcess process = new PtyProcessBuilder(
      TestUtil.getJavaCommand(PromptReader.class)
    ).start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    stdout.assertEndsWith("Enter:");
    PtyHelpers.getInstance().kill((int)process.pid(), PtyHelpers.SIGINT);
    assertProcessTerminatedBySignal(PtyHelpers.SIGINT, process);
  }

  public void testDestroyAfterTermination() throws Exception {
    PtyProcess process = new PtyProcessBuilder(
      TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "0", "Hello, World")
    ).start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    assertProcessTerminatedNormally(process);
    stdout.assertEndsWith("#1: Hello, World\r\n#2: Hello, World\r\n", 10000);
    process.destroy();
    assertEquals(0, process.exitValue());
  }

  public void testNormalTermination() throws Exception {
    PtyProcess process = new PtyProcessBuilder(
      TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1", "Hello, World")
    ).start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    stdout.assertEndsWith("#1: Hello, World\r\n#2: Hello, World\r\n");
    assertProcessTerminatedNormally(process);
  }

  public void testInitialColumnsAndRows() throws IOException, InterruptedException {
    WinSize initialSize = new WinSize(111, 11);
    PtyProcess process = new PtyProcessBuilder(TestUtil.getJavaCommand(ConsoleSizeReporter.class))
      .setInitialColumns(initialSize.getColumns())
      .setInitialRows(initialSize.getRows())
      .start();
    Gobbler stdout = startStdoutGobbler(process);
    startReader(process.getErrorStream(), null);
    assertEquals(initialSize, process.getWinSize());
    stdout.assertEndsWith("columns: 111, rows: 11\r\n");

    WinSize newSize = new WinSize(140, 80);
    assertAlive(process);
    process.setWinSize(newSize);
    assertAlive(process);
    assertEquals(newSize, process.getWinSize());
    writeToStdinAndFlush(process, ConsoleSizeReporter.PRINT_SIZE, true);
    stdout.assertEndsWith(ConsoleSizeReporter.PRINT_SIZE + "\r\ncolumns: 140, rows: 80\r\n");

    writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
    assertProcessTerminatedNormally(process);
    checkGetSetSizeFailed(process);
  }

  public void testResizeTerminalWindow() throws IOException, InterruptedException {
    WinSize initialSize = new WinSize(200, 5);
    File nodeExe = TestUtil.findInPath(Platform.isWindows() ? "node.exe" : "node");
    if (nodeExe == null) {
      return;
    }
    PtyProcess process = new PtyProcessBuilder(new String[]{
      nodeExe.getAbsolutePath(),
      new File(TestUtil.getTestDataPath(), "resize-listener.js").getAbsolutePath()
    }).setInitialColumns(initialSize.getColumns())
      .setInitialRows(initialSize.getRows())
      .start();
    Gobbler stdout = startStdoutGobbler(process);
    startReader(process.getErrorStream(), null);
    assertEquals(initialSize, process.getWinSize());
    stdout.assertEndsWith("columns: " + initialSize.getColumns() + ", rows: " + initialSize.getRows() + "\r\n");
    for (int columns = 50; columns < 60; columns += 2) {
      for (int rows = 10; rows < 20; rows += 2) {
        WinSize newSize = new WinSize(columns, rows);
        process.setWinSize(newSize);
        assertEquals(newSize, process.getWinSize());
        stdout.assertEndsWith("columns: " + columns + ", rows: " + rows + "\r\n");
      }
    }
    writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
    assertProcessTerminatedNormally(process);
    checkGetSetSizeFailed(process);
  }

  /**
   * Tests that getting and setting the window size for a file descriptor works.
   */
  public void testGetAndSetWinSize() throws Exception {
    PtyProcess process = new PtyProcessBuilder(TestUtil.getJavaCommand(ConsoleSizeReporter.class)).start();

    WinSize inputSize = new WinSize(120, 30);
    process.setWinSize(inputSize);
    WinSize outputSize = process.getWinSize();
    assertEquals(inputSize, outputSize);

    writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
    assertProcessTerminatedNormally(process);
    process.destroy();
    checkGetSetSizeFailed(process);
  }

  public static void checkGetSetSizeFailed(@NotNull PtyProcess terminatedProcess) {
    try {
      terminatedProcess.getWinSize();
      fail(terminatedProcess.getClass().getSimpleName() + ": getWinSize should fail for terminated process");
    }
    catch (IOException e) {
      if (!Platform.isWindows()) {
        assertTrue(e.getMessage(), e.getMessage().contains("fd=-1(invalid)"));
      }
    }
    try {
      terminatedProcess.setWinSize(new WinSize(11, 123));
      fail(terminatedProcess.getClass().getSimpleName() + ": setWinSize should fail for terminated process");
    }
    catch (Exception e) {
      if (!Platform.isWindows()) {
        assertTrue(e.getMessage(), e.getMessage().contains("fd=-1(invalid)"));
      }
    }
  }

  public void testConsoleMode() throws Exception {
    PtyProcess process = new PtyProcessBuilder(TestUtil.getJavaCommand(Printer.class)).setConsole(true).start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);

    stdout.awaitFinish();
    stderr.awaitFinish();

    assertProcessTerminatedNormally(process);
    stdout.assertEndsWith(Printer.STDOUT + "\r\n");
    stderr.assertEndsWith(Printer.STDERR + "\r\n");
  }

  public void testPromptReaderConsoleModeOff() throws Exception {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(PromptReader.class))
      .setConsole(false);
    PtyProcess process = builder.start();

    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(process, "Hi", true);
    stdout.assertEndsWith("Enter:Hi\r\nRead:Hi\r\nEnter:");

    writeToStdinAndFlush(process, "", true);

    stdout.assertEndsWith("Enter:\r\nexit: empty line\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    assertProcessTerminatedNormally(process);
  }

  public void testPromptReaderConsoleModeOn() throws Exception {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(PromptReader.class))
      .setConsole(true);
    PtyProcess process = builder.start();

    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(process, "Hi", true);
    stdout.assertEndsWith("Enter:Read:Hi\r\nEnter:");
    writeToStdinAndFlush(process, "", true);
    stdout.assertEndsWith("Enter:exit: empty line\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    assertProcessTerminatedNormally(process);
  }

  private static @NotNull String convertInvisibleChars(@NotNull String s) {
    return s.replace("\n", "\\n").replace("\r", "\\r").replace("\b", "\\b")
      .replace("\u001b", "ESC")
      .replace(String.valueOf(Ascii.BEL_CHAR), "BEL");
  }

  public static void writeToStdinAndFlush(@NotNull PtyProcess process, @NotNull String input,
                                          boolean hitEnter) throws IOException {
    String text = hitEnter ? input + (char) process.getEnterKeyCode() : input;
    process.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
    process.getOutputStream().flush();
  }

  public void testExecCat() throws Exception {
    if (Platform.isWindows()) {
      return;
    }
    PtyProcess pty = new PtyProcessBuilder(new String[]{"cat"}).start();
    Gobbler stdout = startReader(pty.getInputStream(), null);

    pty.getOutputStream().write("Hello\n".getBytes(StandardCharsets.UTF_8));
    pty.getOutputStream().flush();
    stdout.assertEndsWith("Hello\r\nHello\r\n");
    assertTrue("Process terminated unexpectedly", pty.isAlive());

    PtyHelpers.getInstance().kill((int)pty.pid(), PtyHelpers.SIGPIPE);
    assertProcessTerminatedBySignal(PtyHelpers.SIGPIPE, pty);
  }

  public void testWaitForInTheBeginning() throws Exception {
    if (Platform.isWindows()) {
      return;
    }
    PtyProcess process = new PtyProcessBuilder(new String[]{"cat"}).start();

    CountDownLatch latch = new CountDownLatch(1);
    new Thread(() -> {
      try {
        process.waitFor();
        latch.countDown();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).start();

    Gobbler stdout = startReader(process.getInputStream(), null);

    process.getOutputStream().write("Hello\n".getBytes(StandardCharsets.UTF_8));
    process.getOutputStream().flush();
    stdout.assertEndsWith("Hello\r\nHello\r\n");
    process.getOutputStream().write(4); // Ctrl+D
    process.getOutputStream().flush();
    assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  public void testWaitForProcessTerminationWithoutOutputRead() throws IOException, InterruptedException {
    if (Platform.isWindows()) {
      return;
    }
    // After https://github.com/JetBrains/pty4j/pull/94, there's a possibility that the child process will block on
    // output and wait until we've read all its output. This test checks that it isn't the case without us explicitly
    // setting the setUnixOpenTtyToPreserveOutputAfterTermination flag.
    String arg = "hello";
    PtyProcess process = new PtyProcessBuilder(new String[]{"/bin/echo", arg}).start();
    assertProcessTerminatedNormally(process);
  }

  public void testReadPtyProcessOutputAfterTermination() throws IOException, InterruptedException {
    if (Platform.isWindows()) {
      return;
    }
    String arg = "hello";
    PtyProcess process = new PtyProcessBuilder(new String[]{"/bin/echo", arg})
        .setUnixOpenTtyToPreserveOutputAfterTermination(true)
        .start();
    Thread.sleep(2000); // wait for the process to perform all the work and either terminate or block on output
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    assertEquals(arg, reader.readLine());

    assertProcessTerminatedNormally(process);
  }

  public void testReadConsoleEOFWithJdkUnixSpawner() throws Exception {
    if (Platform.isWindows()) return;
    PtyProcess process = new PtyProcessBuilder(new String[]{"/bin/sh", "-c", "/bin/echo stderr 1>&2; /bin/echo stdout; read MY_VAR; /bin/echo done"})
      .setConsole(true)
      .setUnixOpenTtyToPreserveOutputAfterTermination(true)
      .setSpawnProcessUsingJdkOnMacIntel(true)
      .start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);
    stdout.assertEndsWith("stdout\r\n");
    stderr.assertEndsWith("stderr\r\n");
    writeToStdinAndFlush(process, "foo", true); // complete `read` command
    stdout.assertEndsWith("done\r\n");
    stdout.awaitFinish();
    stderr.awaitFinish();
    assertProcessTerminatedNormally(process);
  }

  /*
  public void testStdinInConsoleMode() throws IOException, InterruptedException {
    if (Platform.isWindows()) {
      return;
    }
    PtyProcess pty = new PtyProcessBuilder(new String[]{
      new File(TestUtil.getTestDataPath(), "PTY.exe").getAbsolutePath()
    }).setConsole(true)
      .start();
    Gobbler stdout = startReader(pty.getInputStream(), null);
    Gobbler stderr = startReader(pty.getErrorStream(), null);
    assertEquals("isatty(stdout): 1, isatty(stderr): 1\r\n", stdout.readLine(1000));
    assertEquals("hello from stderr\r\n", stderr.readLine(1000));
    pty.getOutputStream().write("123\n".getBytes(StandardCharsets.UTF_8));
    pty.getOutputStream().flush();
    assertEquals("enter int: entered 123\r\n", stdout.readLine(1000));

    assertTrue(pty.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, pty.exitValue());
  }

  public void testCloseStdinInConsoleMode() throws IOException, InterruptedException {
    if (Platform.isWindows()) {
      return;
    }
    PtyProcess pty = new PtyProcessBuilder(new String[]{
      new File(TestUtil.getTestDataPath(), "PTY.exe").getAbsolutePath()
    }).setConsole(true)
      .setRedirectErrorStream(true)
      .start();
    Gobbler stdout = startReader(pty.getInputStream(), null);
    Gobbler stderr = startReader(pty.getErrorStream(), null);
    assertEquals("isatty(stdout): 1, isatty(stderr): 1\r\n", stdout.readLine(1000));
    assertEquals("hello from stderr\r\n", stdout.readLine(1000));
    pty.getOutputStream().close();
    //assertEquals("enter int: entered 0\r\n", stdout.readLine(1000));

    assertTrue(pty.waitFor(1, TimeUnit.SECONDS));
    System.out.println("stdout: " + stdout.getOutput());
    System.out.println("stderr: " + stderr.getOutput());
    assertEquals(0, pty.exitValue());
  }
  */

  public void testBashEchoOutputInConsoleMode() throws IOException, InterruptedException {
    if (Platform.isWindows() || !new File("/bin/bash").isFile()) {
      return;
    }
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"/bin/bash", "-c", "echo Success"})
      .setRedirectErrorStream(true)
      .setConsole(true);
    PtyProcess process = builder.start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);
    stdout.assertEndsWith("Success\r\n");
    stdout.awaitFinish();
    stderr.awaitFinish();
    assertProcessTerminatedNormally(process);
    assertEquals("", stderr.getOutput());
  }

  public void testCmdResize() throws IOException, InterruptedException {
    if (!Platform.isWindows()) return;
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"cmd.exe"})
      .setEnvironment(mergeCustomAndSystemEnvironment(Collections.singletonMap("PROMPT", "$P$G")))
      .setRedirectErrorStream(true)
      .setConsole(false);
    PtyProcess process = builder.start();
    WinSize winSize = new WinSize(180, 20);
    process.setWinSize(winSize);
    assertEquals(winSize, process.getWinSize());
    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);
    String dir = Paths.get(".").toAbsolutePath().normalize().toString();
    stdout.assertEndsWith(dir + ">");
    writeToStdinAndFlush(process, "echo Hello", true);
    stdout.assertEndsWith(dir + ">echo Hello\r\n" +
                          "Hello\r\n\r\n" +
                          dir + ">");

    writeToStdinAndFlush(process, "exit", true);
    stdout.assertEndsWith(dir + ">echo Hello\r\n" +
                          "Hello\r\n\r\n" +
                          dir + ">exit\r\n");
    assertProcessTerminatedNormally(process); 
    assertEquals("", stderr.getOutput());
  }

  public void testSendInterrupt() throws IOException, InterruptedException {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(PromptReader.class)).setConsole(false);
    PtyProcess process = builder.start();

    Gobbler stdout = startStdoutGobbler(process);
    Gobbler stderr = startStderrGobbler(process);
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(process, String.valueOf(Ascii.ETX_CHAR), false);

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    if (Platform.isWindows()) {
      assertProcessTerminatedAbnormally(process);
    }
    else {
      assertProcessTerminatedBySignal(PtyHelpers.SIGINT, process);
    }
  }

  public void testConsoleProcessCount() throws IOException, InterruptedException {
    if (!Platform.isWindows()) return;
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"cmd.exe"})
      .setEnvironment(mergeCustomAndSystemEnvironment(Collections.singletonMap("PROMPT", "$l-my-custom-prompt-$G")))
      .setRedirectErrorStream(true)
      .setConsole(false);
    String expectedCustomPrompt = "<-my-custom-prompt->";
    WinPtyProcess child = (WinPtyProcess)builder.start();
    Gobbler stdout = startReader(child.getInputStream(), null);
    Gobbler stderr = startReader(child.getErrorStream(), null);
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" + expectedCustomPrompt);
    assertEquals(2, child.getConsoleProcessCount());
    writeToStdinAndFlush(child, "echo Hello", true);
    stdout.assertEndsWith("\r\nHello\r\n\r\n" + expectedCustomPrompt);
    writeToStdinAndFlush(child, "cmd.exe", true);
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" + expectedCustomPrompt);
    assertEquals(3, child.getConsoleProcessCount());

    writeToStdinAndFlush(child, "exit", true);
    writeToStdinAndFlush(child, "exit", true);
    assertProcessTerminatedNormally(child);
    assertEquals("", stderr.getOutput());
  }

  public static void assertProcessTerminatedNormally(@NotNull Process process) throws InterruptedException {
    assertProcessTerminated(0, process);
  }

  public static void assertProcessTerminatedBySignal(int signalNumber, @NotNull Process process) throws InterruptedException {
    assertProcessTerminated(128 + signalNumber, process);
  }

  private static void assertProcessTerminatedAbnormally(@NotNull Process process) throws InterruptedException {
    assertProcessTerminated(Integer.MIN_VALUE, process);
  }

  public static void assertProcessTerminated(int expectedExitCode, @NotNull Process process) throws InterruptedException {
    assertTrue("Process hasn't been terminated within timeout", process.waitFor(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    int exitValue = process.exitValue();
    if (expectedExitCode == Integer.MIN_VALUE) {
      assertTrue("Process terminated with exit code " + exitValue + ", non-zero exit code was expected", exitValue != 0);
    }
    else {
      assertEquals(expectedExitCode, exitValue);
    }
  }

  public static void assertAlive(@NotNull Process process) {
    try {
      int exitValue = process.exitValue();
      fail("process has terminated unexpectedly with exit code " + exitValue);
    }
    catch (Exception ignored) {
    }
  }

  private static @NotNull Map<String, String> mergeCustomAndSystemEnvironment(@NotNull Map<String, String> customEnv) {
    Map<String, String> env = new HashMap<>(System.getenv());
    env.putAll(customEnv);
    return env;
  }

  public static @NotNull Gobbler startStdoutGobbler(@NotNull PtyProcess process) {
    return new Gobbler(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), null, process);
  }

  public static @NotNull Gobbler startStderrGobbler(@NotNull PtyProcess process) {
    return new Gobbler(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8), null, process);
  }

  @NotNull
  private static Gobbler startReader(@NotNull InputStream in, @Nullable CountDownLatch latch) {
    return new Gobbler(new InputStreamReader(in, StandardCharsets.UTF_8), latch, null);
  }

  public static class Gobbler implements Runnable {
    private final Reader myReader;
    private final CountDownLatch myLatch;
    private final @Nullable PtyProcess myProcess;
    private final StringBuffer myOutput;
    private final Thread myThread;
    private final BlockingQueue<String> myLineQueue = new LinkedBlockingQueue<>();
    private final ReentrantLock myNewTextLock = new ReentrantLock();
    private final Condition myNewTextCondition = myNewTextLock.newCondition();

    private Gobbler(@NotNull Reader reader, @Nullable CountDownLatch latch, @Nullable PtyProcess process) {
      myReader = reader;
      myLatch = latch;
      myProcess = process;
      myOutput = new StringBuffer();
      myThread = new Thread(this, "Stream gobbler");
      myThread.start();
    }

    @Override
    public void run() {
      try {
        char[] buf = new char[32 * 1024];
        String linePrefix = "";
        while (true) {
          int count = myReader.read(buf);
          if (count <= 0) {
            myReader.close();
            return;
          }
          myOutput.append(buf, 0, count);
          linePrefix = processLines(linePrefix + new String(buf, 0, count));
          myNewTextLock.lock();
          try {
            myNewTextCondition.signalAll();
          }
          finally {
            myNewTextLock.unlock();
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        if (myLatch != null) {
          myLatch.countDown();
        }
      }
    }

    @NotNull
    private String processLines(@NotNull String text) {
      int start = 0;
      while (true) {
        int end = text.indexOf('\n', start);
        if (end < 0) {
          return text.substring(start);
        }
        myLineQueue.add(text.substring(start, end + 1));
        start = end + 1;
      }
    }

    @NotNull
    public String getOutput() {
      return cleanWinText(myOutput.toString());
    }

    public void awaitFinish() throws InterruptedException {
      myThread.join(TimeUnit.SECONDS.toMillis(WAIT_TIMEOUT_SECONDS));
      if (myThread.isAlive()) {
        fail("Reader thread hasn't been finished in " + WAIT_TIMEOUT_SECONDS + "s");
      }
    }

    @Nullable
    public String readLine() throws InterruptedException {
      return readLine(TimeUnit.SECONDS.toMillis(WAIT_TIMEOUT_SECONDS));
    }

    @Nullable
    public String readLine(long awaitTimeoutMillis) throws InterruptedException {
      String line = myLineQueue.poll(awaitTimeoutMillis, TimeUnit.MILLISECONDS);
      if (line != null) {
        line = cleanWinText(line);
      }
      return line;
    }

    private boolean awaitTextEndsWith(@NotNull String suffix, long timeoutMillis) {
      long startTimeMillis = System.currentTimeMillis();
      long nextTimeoutMillis = timeoutMillis;
      do {
        myNewTextLock.lock();
        try {
          try {
            if (endsWith(suffix)) {
              return true;
            }
            myNewTextCondition.await(nextTimeoutMillis, TimeUnit.MILLISECONDS);
            if (endsWith(suffix)) {
              return true;
            }
          }
          catch (InterruptedException e) {
            e.printStackTrace();
            return false;
          }
        }
        finally {
          myNewTextLock.unlock();
        }
        nextTimeoutMillis = startTimeMillis + timeoutMillis - System.currentTimeMillis();
      } while (nextTimeoutMillis >= 0);
      return false;
    }

    private boolean endsWith(@NotNull String suffix) {
      String text = cleanWinText(myOutput.toString());
      if (Platform.isWindows()) {
        int lastInd = text.lastIndexOf(suffix);
        if (lastInd == -1) return false;
        String rest = text.substring(lastInd + suffix.length());
        return rest.trim().isEmpty();
      }
      return text.endsWith(suffix);
    }

    @NotNull
    private static String cleanWinText(@NotNull String text) {
      if (Platform.isWindows()) {
        text = text.replace("\u001B[0m", "").replace("\u001B[m", "").replace("\u001B[0K", "").replace("\u001B[K", "")
          .replace("\u001B[?25l", "").replace("\u001b[?25h", "").replaceAll("\u001b\\[\\d*G", "")
                .replace("\u001b[2J", "").replaceAll("\u001B\\[\\d*;?\\d*H", "")
                .replaceAll("\u001B\\[\\d*X", "")
                .replaceAll("\u001B\\[\\d*A", "")
                .replaceAll(" *\\r\\n", "\r\n").replaceAll(" *$", "").replaceAll("(\\r\\n)+\\r\\n$", "\r\n");
        int oscInd = 0;
        do {
          oscInd = text.indexOf("\u001b]0;", oscInd);
          int bellInd = oscInd >= 0 ? text.indexOf(Ascii.BEL, oscInd) : -1;
          if (bellInd >= 0) {
            text = text.substring(0, oscInd) + text.substring(bellInd + 1);
          }
        } while (oscInd >= 0);
        int backspaceInd = text.indexOf(Ascii.BS);
        while (backspaceInd >= 0) {
          text = text.substring(0, Math.max(0, backspaceInd - 1)) + text.substring(backspaceInd + 1);
          backspaceInd = text.indexOf(Ascii.BS);
        }
      }
      return text;
    }

    public void assertEndsWith(@NotNull String expectedSuffix) {
      assertEndsWith(expectedSuffix, TimeUnit.SECONDS.toMillis(WAIT_TIMEOUT_SECONDS));
    }

    private void assertEndsWith(@NotNull String expectedSuffix, long timeoutMillis) {
      boolean ok = awaitTextEndsWith(expectedSuffix, timeoutMillis);
      if (!ok) {
        String output = getOutput();
        String cleanOutput = cleanWinText(output);
        String actual = cleanOutput.substring(Math.max(0, cleanOutput.length() - expectedSuffix.length()));
        if (expectedSuffix.equals(actual)) {
          fail("awaitTextEndsWith could detect suffix within timeout, but it is there");
        }
        expectedSuffix = convertInvisibleChars(expectedSuffix);
        actual = convertInvisibleChars(actual);
        int lastTextSize = 1000;
        String lastText = output.substring(Math.max(0, output.length() - lastTextSize));
        if (output.length() > lastTextSize) {
          lastText = "..." + lastText;
        }
        assertEquals("Unmatched suffix (trailing text: " + convertInvisibleChars(lastText) +
          (myProcess != null ? ", " + getProcessStatus(myProcess) : "") + ")", expectedSuffix, actual);
        fail("Unexpected failure");
      }
    }
  }

  private static @NotNull String getProcessStatus(@NotNull PtyProcess process) {
    boolean running = process.isAlive();
    Integer exitCode = getExitCode(process);
    if (running && exitCode == null) {
      return "alive process";
    }
    return "process running:" + running + ", exit code:" + (exitCode != null ? exitCode : "N/A");
  }

  private static @Nullable Integer getExitCode(@NotNull PtyProcess process) {
    Integer exitCode = null;
    try {
      exitCode = process.exitValue();
    }
    catch (IllegalThreadStateException ignored) {
    }
    return exitCode;
  }
}
