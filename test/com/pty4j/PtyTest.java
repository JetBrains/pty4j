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
import testData.PromptReader;
import testData.RepeatTextWithTimeout;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assume.assumeTrue;


/**
 * Test cases for {@link com.pty4j.unix.PtyHelpers}.
 */
public class PtyTest extends TestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestUtil.setLocalPtyLib();
  }

  private static boolean WIFEXITED(int status) {
    return _WSTATUS(status) == 0;
  }

  private static final int _WSTOPPED = 0177;  /* _WSTATUS if process is stopped */

  private static boolean WIFSIGNALED(int status) {
    return _WSTATUS(status) != _WSTOPPED && _WSTATUS(status) != 0 && (status) != 0x13;
  }

  private static int _WSTATUS(int status) {
    return status & 0177;
  }

  /**
   * Remove the 'interactive' prefix to run an interactive bash console.
   */
  public void interactiveTestRunConsoleOk() throws Exception {
    // static void main(String[] args) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);

    // Start the process in a Pty...
    final PtyProcess pty = PtyProcess.exec(new String[]{"/bin/sh", "-i"});

    // Asynchronously check whether the output of the process is captured
    // properly...
    Thread t1 = new Thread() {
      public void run() {
        InputStream is = pty.getInputStream();

        try {
          int ch;
          while (pty.isRunning() && (ch = is.read()) >= 0) {
            if (ch >= 0) {
              System.out.write(ch);
              System.out.flush();
            }
          }

          latch.countDown();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
    t1.start();

    // Asynchronously wait for a little while, then close the Pty, which should
    // force our child process to be terminated...
    Thread t2 = new Thread() {
      public void run() {
        OutputStream os = pty.getOutputStream();

        try {
          int ch;
          while (pty.isRunning() && (ch = System.in.read()) >= 0) {
            if (ch >= 0) {
              os.write(ch);
              os.flush();
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
    t2.start();

    assertTrue(latch.await(600, TimeUnit.SECONDS));
    // We should've waited long enough to have read some of the input...

    int result = pty.waitFor();

    t1.join();
    t2.join();

    assertTrue("Unexpected process result: " + result, -1 == result);
  }

  /**
   * Tests that closing the Pty after the child process is finished works
   * normally. Should keep track of issue #1.
   */
  public void testClosePtyForTerminatedChildOk() throws Exception {
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "0", "Hello, World");
    PtyProcess pty = PtyProcess.exec(cmd);
    Gobbler stdout = startReader(pty.getInputStream(), null);
    assertEquals(0, pty.waitFor());
    stdout.assertEndsWith("#1: Hello, World\r\n#2: Hello, World\r\n", 10000);
    pty.destroy();
    int exitCode = pty.waitFor();
    assertTrue("Unexpected process result: " + exitCode, WIFEXITED(exitCode));
  }

  /**
   * Tests that the child process is terminated if the {@link com.pty4j.unix.Pty} closed before
   * the child process is finished. Should keep track of issue #1.
   */
  public void testClosePtyTerminatesChildOk() throws Exception {
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "15", "1000", "Hello, World");
    PtyProcess pty = PtyProcess.exec(cmd);
    Gobbler stdout = startReader(pty.getInputStream(), null);
    stdout.assertEndsWith("#1: Hello, World\r\n", 30000);
    pty.destroy();
    int exitCode = pty.waitFor();
    assertTrue("Unexpected process result: " + exitCode, WIFSIGNALED(exitCode));
  }

  /**
   * Tests that we can execute a process in a Pty, and wait for its normal
   * termination.
   */
  public void testExecInPTY() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);

    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000", "Hello, World");

    // Start the process in a Pty...
    final PtyProcess pty = PtyProcess.exec(cmd);
    final int[] result = {-1};

    // Asynchronously wait for the process to end...
    Thread t = new Thread() {
      public void run() {
        try {
          Scanner s = new Scanner(pty.getInputStream());
          while (s.hasNextLine()) {
            System.out.println(s.nextLine());
          }
          result[0] = pty.waitFor();

          latch.countDown();
        } catch (InterruptedException e) {
          // Simply stop the thread...
        }
      }
    };
    t.start();

    assertTrue("Child already terminated?!", pty.isRunning());

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    t.join();

    assertEquals("Unexpected process result!", 0, result[0]);
  }

  public void testInitialColumnsAndRows() throws IOException, InterruptedException {
    PtyProcess process = new PtyProcessBuilder(TestUtil.getJavaCommand(ConsoleSizeReporter.class))
      .setInitialColumns(111)
      .setInitialRows(11)
      .start();
    Gobbler stdout = startReader(process.getInputStream(), null);
    startReader(process.getErrorStream(), null);
    assertEquals(new WinSize(111, 11), process.getWinSize());
    stdout.awaitTextEndsWith("columns: 111, rows: 11\r\n", 1000);

    WinSize inputSize = new WinSize(140, 80);
    process.setWinSize(inputSize);
    assertEquals(process.getWinSize(), inputSize);
    writeToStdinAndFlush(process, ConsoleSizeReporter.PRINT_SIZE, true);
    stdout.awaitTextEndsWith(ConsoleSizeReporter.PRINT_SIZE + "\r\n", 1000);
    stdout.awaitTextEndsWith("columns: 140, rows: 80\r\n", 1000);

    writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
    assertTrue(process.waitFor(10, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    checkGetSetSizeFailed(process);
  }

  /**
   * Tests that getting and setting the window size for a file descriptor works.
   */
  public void testGetAndSetWinSize() throws Exception {
    PtyProcess process = PtyProcess.exec(TestUtil.getJavaCommand(ConsoleSizeReporter.class));

    WinSize inputSize = new WinSize(120, 30);
    process.setWinSize(inputSize);
    WinSize outputSize = process.getWinSize();
    assertEquals(inputSize, outputSize);

    writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
    assertTrue(process.waitFor(10, TimeUnit.SECONDS));
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
    String[] command;
    if (Platform.isWindows()) {
      File file = new File(TestUtil.getTestDataPath(), "console-mode-test1.bat");
      assumeTrue(file.exists());
      command = new String[] {
        "cmd.exe", "/c",
        file.getAbsolutePath()
      };
    } else {
      File file = new File(TestUtil.getTestDataPath(), "console-mode-test1.sh");
      assumeTrue(file.exists());
      command = new String[] {
        "/bin/sh", file.getAbsolutePath()
      };
    }

    PtyProcess pty = new PtyProcessBuilder(command).setConsole(true).start();

    final CountDownLatch latch = new CountDownLatch(2);
    Gobbler stdout = startReader(pty.getInputStream(), latch);
    Gobbler stderr = startReader(pty.getErrorStream(), latch);

    assertTrue(latch.await(4, TimeUnit.SECONDS));

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertTrue(pty.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, pty.exitValue());

    assertEquals("abcdefz\r\n", stdout.getOutput());
    assertEquals("ABCDEFZ\r\n", stderr.getOutput());
  }

  public void testPromptReaderConsoleModeOff() throws Exception {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(PromptReader.class))
      .setConsole(false);
    PtyProcess child = builder.start();

    Gobbler stdout = startReader(child.getInputStream(), null);
    Gobbler stderr = startReader(child.getErrorStream(), null);
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(child, "Hi" + enter(child));
    assertEquals("Enter:Hi\r\n", stdout.readLine(1000));
    assertEquals("Read: Hi\r\n", stdout.readLine(1000));

    stdout.assertEndsWith("Enter:");

    writeToStdinAndFlush(child, enter(child));

    assertEquals("Enter:\r\n", stdout.readLine(1000));
    assertEquals("exit: empty line\r\n", stdout.readLine(1000));

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    assertTrue(child.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, child.exitValue());
  }

  public void testPromptReaderConsoleModeOn() throws Exception {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(PromptReader.class))
      .setConsole(true);
    PtyProcess child = builder.start();

    Gobbler stdout = startReader(child.getInputStream(), null);
    Gobbler stderr = startReader(child.getErrorStream(), null);
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(child, "Hi" + enter(child));
    assertEquals("Enter:Read: Hi\r\n", stdout.readLine(1000));
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(child, enter(child));
    assertEquals("Enter:exit: empty line\r\n", stdout.readLine(1000));

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    assertTrue(child.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, child.exitValue());
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

    assertTrue("Process terminated unexpectedly", pty.isRunning());
    pty.getOutputStream().write("Hello\n".getBytes(StandardCharsets.UTF_8));
    pty.getOutputStream().flush();
    assertTrue(stdout.awaitTextEndsWith("Hello\r\nHello\r\n", 1000));

    PtyHelpers.getInstance().kill(pty.getPid(), PtyHelpers.SIGPIPE);
    assertTrue(pty.waitFor(1, TimeUnit.SECONDS));
    assertEquals(PtyHelpers.SIGPIPE, pty.exitValue());
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
    assertTrue(process.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
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
    String line = reader.readLine();

    assertTrue(process.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    assertEquals(arg, line);
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
    PtyProcess pty = builder.start();
    Gobbler stdout = startReader(pty.getInputStream(), null);
    Gobbler stderr = startReader(pty.getErrorStream(), null);
    assertEquals("Success\r\n", stdout.readLine(5000));
    stdout.awaitFinish();
    stderr.awaitFinish();
    boolean done = pty.waitFor(1, TimeUnit.SECONDS);
    assertTrue(done);
    assertEquals(0, pty.exitValue());
    assertEquals("", stderr.getOutput());
  }

  public void testCmdResize() throws IOException, InterruptedException {
    if (!Platform.isWindows()) return;
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"cmd.exe"})
      .setRedirectErrorStream(true)
      .setConsole(false);
    PtyProcess child = builder.start();
    child.setWinSize(new WinSize(80, 20));
    Gobbler stdout = startReader(child.getInputStream(), null);
    Gobbler stderr = startReader(child.getErrorStream(), null);
    String dir = Paths.get(".").toAbsolutePath().normalize().toString();
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          dir + ">", 5000);
    //writeToStdinAndFlush(child, "ping -n 3 127.0.0.1 >NUL" + ENTER);
    writeToStdinAndFlush(child, "echo Hello" + enter(child));
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          //"C:\\Users\\user\\projects\\pty4j>ping -n 3 127.0.0.1 >NUL\r\n\r\n" +
                          dir + ">echo Hello\r\n" +
                          "Hello\r\n\r\n" +
                          dir + ">", 5000);

    writeToStdinAndFlush(child, "exit" + enter(child));
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          //"C:\\Users\\user\\projects\\pty4j>ping -n 3 127.0.0.1 >NUL\r\n\r\n" +
                          dir + ">echo Hello\r\n" +
                          "Hello\r\n\r\n" +
                          dir + ">exit\r\n", 5000);
    boolean done = child.waitFor(1, TimeUnit.SECONDS);
    assertTrue(done);
    assertEquals(0, child.exitValue());
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
                          dir + ">", 5000);
    assertEquals(2, child.getConsoleProcessCount());
    writeToStdinAndFlush(child, "echo Hello" + enter(child));
    stdout.assertEndsWith("\r\nHello\r\n\r\n" + dir + ">");
    writeToStdinAndFlush(child, "cmd.exe" + enter(child));
    stdout.assertEndsWith(" All rights reserved.\r\n\r\n" +
                          dir + ">", 5000);
    assertEquals(3, child.getConsoleProcessCount());

    writeToStdinAndFlush(child, "exit" + enter(child) + "exit" + enter(child));
    boolean done = child.waitFor(1, TimeUnit.SECONDS);
    assertTrue(done);
    assertEquals(0, child.exitValue());
    assertEquals("", stderr.getOutput());
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
    public String readLine(int awaitTimeoutMillis) throws InterruptedException {
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
      assertEndsWith(expectedSuffix, 1000);
    }

    public void assertEndsWith(@NotNull String expectedSuffix, long timeoutMillis) {
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
