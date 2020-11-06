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


import com.google.common.base.Ascii;
import com.pty4j.unix.PtyHelpers;
import com.pty4j.windows.WinPtyProcess;
import com.sun.jna.Platform;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import testData.ConsoleSizeReporter;
import testData.Printer;
import testData.PromptReader;
import testData.RepeatTextWithTimeout;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
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
    stdout.assertEndsWith("#1: Hello, World\r\n");
    process.destroy();
    if (Platform.isWindows()) {
      assertProcessTerminatedAbnormally(process);
    }
    else {
      assertProcessTerminatedBySignal(PtyHelpers.SIGTERM, process);
    }
  }

  public void testSendSigInt() throws Exception {
    if (Platform.isWindows()) return;
    PtyProcess process = new PtyProcessBuilder(
      TestUtil.getJavaCommand(PromptReader.class)
    ).start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    stdout.assertEndsWith("Enter:");
    PtyHelpers.getInstance().kill(process.getPid(), PtyHelpers.SIGINT);
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
    Gobbler stdout = startReader(process.getInputStream(), null);
    startReader(process.getErrorStream(), null);
    assertEquals(initialSize, process.getWinSize());
    stdout.assertEndsWith("columns: 111, rows: 11\r\n");

    WinSize newSize = new WinSize(140, 80);
    process.setWinSize(newSize);
    assertEquals(newSize, process.getWinSize());
    writeToStdinAndFlush(process, ConsoleSizeReporter.PRINT_SIZE, true);
    stdout.assertEndsWith(ConsoleSizeReporter.PRINT_SIZE + "\r\ncolumns: 140, rows: 80\r\n");

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

  private void checkGetSetSizeFailed(@NotNull PtyProcess terminatedProcess) {
    try {
      terminatedProcess.getWinSize();
      fail("getWinSize should fail for terminated process");
    }
    catch (IOException e) {
      if (!Platform.isWindows()) {
        assertTrue(e.getMessage(), e.getMessage().contains("fd=-1(invalid)"));
      }
    }
    try {
      terminatedProcess.setWinSize(new WinSize(11, 123));
      fail("setWinSize should fail for terminated process");
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
    stdout.assertEndsWith("Enter:Hi\r\nRead: Hi\r\nEnter:");

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
    stdout.assertEndsWith("Enter:Read: Hi\r\nEnter:");
    writeToStdinAndFlush(process, "", true);
    stdout.assertEndsWith("Enter:exit: empty line\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    assertProcessTerminatedNormally(process);
  }

  @NotNull
  private static String convertInvisibleChars(@NotNull String s) {
    return s.replace("\n", "\\n").replace("\r", "\\r")
      .replace("\u001b", "ESC")
      .replace(String.valueOf((char)Ascii.BEL), "BEL");
  }

  private void writeToStdinAndFlush(@NotNull PtyProcess process, @NotNull String input) throws IOException {
    writeToStdinAndFlush(process, input, false);
  }

  private void writeToStdinAndFlush(@NotNull PtyProcess process, @NotNull String input, boolean hitEnter) throws IOException {
    String text = hitEnter ? input + enter(process) : input;
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
    assertTrue("Process terminated unexpectedly", pty.isRunning());

    PtyHelpers.getInstance().kill(pty.getPid(), PtyHelpers.SIGPIPE);
    assertProcessTerminatedBySignal(PtyHelpers.SIGPIPE, pty);
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
      .setRedirectErrorStream(true)
      .setConsole(false);
    PtyProcess process = builder.start();
    process.setWinSize(new WinSize(80, 20));
    Gobbler stdout = startReader(process.getInputStream(), null);
    Gobbler stderr = startReader(process.getErrorStream(), null);
    String dir = Paths.get(".").toAbsolutePath().normalize().toString();
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          dir + ">");
    writeToStdinAndFlush(process, "echo Hello" + enter(process));
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          //"C:\\Users\\user\\projects\\pty4j>ping -n 3 127.0.0.1 >NUL\r\n\r\n" +
                          dir + ">echo Hello\r\n" +
                          "Hello\r\n\r\n" +
                          dir + ">");

    writeToStdinAndFlush(process, "exit", true);
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          //"C:\\Users\\user\\projects\\pty4j>ping -n 3 127.0.0.1 >NUL\r\n\r\n" +
                          dir + ">echo Hello\r\n" +
                          "Hello\r\n\r\n" +
                          dir + ">exit\r\n");
    assertProcessTerminatedNormally(process); 
    assertEquals("", stderr.getOutput());
  }

  public void testConsoleProcessCount() throws IOException, InterruptedException {
    if (!Platform.isWindows()) return;
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"cmd.exe"})
      .setRedirectErrorStream(true)
      .setConsole(false);
    WinPtyProcess child = (WinPtyProcess)builder.start();
    Gobbler stdout = startReader(child.getInputStream(), null);
    Gobbler stderr = startReader(child.getErrorStream(), null);
    String dir = Paths.get(".").toAbsolutePath().normalize().toString();
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          dir + ">");
    assertEquals(2, child.getConsoleProcessCount());
    writeToStdinAndFlush(child, "echo Hello", true);
    stdout.assertEndsWith("\r\nHello\r\n\r\n" + dir + ">");
    writeToStdinAndFlush(child, "cmd.exe" + enter(child));
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          dir + ">");
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

  private static void assertProcessTerminated(int expectedExitCode, @NotNull Process process) throws InterruptedException {
    assertTrue("Process hasn't been terminated within timeout", process.waitFor(2, TimeUnit.MINUTES));
    int exitValue = process.exitValue();
    if (expectedExitCode == Integer.MIN_VALUE) {
      assertTrue("Process terminated with exit code " + exitValue + ", non-zero exit code was expected", exitValue != 0);
    }
    else {
      assertEquals(expectedExitCode, exitValue);
    }
  }

  private static String enter(@NotNull PtyProcess process) {
    return String.valueOf((char)process.getEnterKeyCode());
  }

  @NotNull
  private static Gobbler startReader(@NotNull InputStream in, @Nullable CountDownLatch latch) {
    return new Gobbler(new InputStreamReader(in, StandardCharsets.UTF_8), latch);
  }

  private static class Gobbler implements Runnable {
    private final Reader myReader;
    private final CountDownLatch myLatch;
    private final StringBuffer myOutput;
    private final Thread myThread;
    private final BlockingQueue<String> myLineQueue = new LinkedBlockingQueue<>();
    private final ReentrantLock myNewTextLock = new ReentrantLock();
    private final Condition myNewTextCondition = myNewTextLock.newCondition();

    private Gobbler(@NotNull Reader reader, @Nullable CountDownLatch latch) {
      myReader = reader;
      myLatch = latch;
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
    String getOutput() {
      return myOutput.toString();
    }

    void awaitFinish() throws InterruptedException {
      myThread.join();
    }

    @Nullable
    public String readLine() throws InterruptedException {
      return readLine(TimeUnit.MINUTES.toMillis(2));
    }

    @Nullable
    public String readLine(long awaitTimeoutMillis) throws InterruptedException {
      String line = myLineQueue.poll(awaitTimeoutMillis, TimeUnit.MILLISECONDS);
      if (line != null) {
        line = cleanWinText(line);
      }
      return line;
    }

    public boolean awaitTextEndsWith(@NotNull String suffix, long timeoutMillis) {
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
      return text.endsWith(suffix);
    }

    @NotNull
    private static String cleanWinText(@NotNull String text) {
      if (Platform.isWindows()) {
        text = text.replace("\u001B[0m", "").replace("\u001B[0K", "")
          .replace("\u001B[?25l", "").replace("\u001b[?25h", "").replace("\u001b[1G", "");
        int oscInd = 0;
        do {
          oscInd = text.indexOf("\u001b]0;", oscInd);
          int bellInd = oscInd >= 0 ? text.indexOf(Ascii.BEL, oscInd) : -1;
          if (bellInd >= 0) {
            text = text.substring(0, oscInd) + text.substring(bellInd + 1);
          }
        } while (oscInd >= 0);
      }
      return text;
    }

    public void assertEndsWith(@NotNull String expectedSuffix) {
      assertEndsWith(expectedSuffix, TimeUnit.MINUTES.toMillis(2));
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
        assertEquals("Unmatched suffix, (trailing text: " + convertInvisibleChars(lastText) + ")", expectedSuffix, actual);
        fail("Unexpected failure");
      }
    }
  }
}
