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
import com.sun.jna.Platform;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import testData.PromptReader;
import testData.RepeatTextWithTimeout;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assume.assumeTrue;


/**
 * Test cases for {@link com.pty4j.unix.PtyHelpers}.
 */
public class PtyTest extends TestCase {

  private static final String ENTER = Platform.isWindows() ? "\r" : "\n";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestUtil.setLocalPtyLib();
  }

  static class Command {
    final String m_command;
    final String[] m_args;

    public Command(String command, String[] args) {
      m_command = command;
      m_args = args;
    }
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
    final CountDownLatch latch = new CountDownLatch(1);

    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000", "Hello, World");

    // Start the process in a Pty...
    final PtyProcess pty = PtyProcess.exec(cmd);
    final int[] result = {-1};

    final AtomicInteger readChars = new AtomicInteger();

    // Asynchronously check whether the output of the process is captured
    // properly...
    Thread t1 = new Thread() {
      public void run() {
        InputStream is = pty.getInputStream();

        try {
          int ch;
          while (pty.isRunning() && (ch = is.read()) >= 0) {
            if (ch >= 0) {
              readChars.incrementAndGet();
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
    t1.start();

    // Asynchronously wait for a little while, then close the Pty, but our child
    // process should be terminated already...
    Thread t2 = new Thread() {
      public void run() {
        try {
          TimeUnit.MILLISECONDS.sleep(2500L);

          pty.destroy();

          result[0] = pty.waitFor();

          latch.countDown();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
    t2.start();

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    // We should've waited long enough to have read some of the input...
    assertTrue(readChars.get() > 0);

    t1.join();
    t2.join();

    assertTrue("Unexpected process result: " + result[0], WIFEXITED(result[0]));
  }

  /**
   * Tests that the child process is terminated if the {@link com.pty4j.unix.Pty} closed before
   * the child process is finished. Should keep track of issue #1.
   */
  public void testClosePtyTerminatesChildOk() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);

    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "15", "1000", "Hello, World");

    // Start the process in a Pty...
    final PtyProcess pty = PtyProcess.exec(cmd);
    final int[] result = {-1};

    final AtomicInteger readChars = new AtomicInteger();

    // Asynchronously check whether the output of the process is captured
    // properly...
    Thread t1 = new Thread() {
      public void run() {
        InputStream is = pty.getInputStream();

        try {
          int ch;
          while (pty.isRunning() && (ch = is.read()) >= 0) {
            if (ch >= 0) {
              readChars.incrementAndGet();
            }
          }
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
        try {
          TimeUnit.MILLISECONDS.sleep(500L);

          pty.destroy();

          result[0] = pty.waitFor();

          latch.countDown();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
    t2.start();

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    // We should've waited long enough to have read some of the input...
    assertTrue(readChars.get() > 0);

    t1.join();
    t2.join();

    assertTrue("Unexpected process result: " + result[0], WIFSIGNALED(result[0]));
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

  /**
   * Tests that getting and setting the window size for a file descriptor works.
   */
  public void testGetAndSetWinSize() throws Exception {
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000", "Hello, World");

    PtyProcess pty = PtyProcess.exec(cmd);

    WinSize ws = new WinSize();
    ws.ws_col = 120;
    ws.ws_row = 30;
    pty.setWinSize(ws);

    WinSize ws1 = pty.getWinSize();

    assertNotNull(ws1);
    assertEquals(120, ws1.ws_col);
    assertEquals(30, ws1.ws_row);

    pty.waitFor();
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
    writeToStdinAndFlush(child, "Hi" + ENTER);
    assertEquals("Enter:Hi\r\n", stdout.readLine(1000));
    assertEquals("Read: Hi\r\n", stdout.readLine(1000));

    stdout.assertEndsWith("Enter:");

    writeToStdinAndFlush(child, ENTER);

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
    writeToStdinAndFlush(child, "Hi" + ENTER);
    assertEquals("Enter:Read: Hi\r\n", stdout.readLine(1000));
    stdout.assertEndsWith("Enter:");
    writeToStdinAndFlush(child, ENTER);
    assertEquals("Enter:exit: empty line\r\n", stdout.readLine(1000));

    stdout.awaitFinish();
    stderr.awaitFinish();
    assertEquals("", stderr.getOutput());

    assertTrue(child.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, child.exitValue());
  }

  @NotNull
  private static String convertInvisibleChars(@NotNull String s) {
    return s.replace("\n", "\\n").replace("\r", "\\r").replace("\u001b", "ESC");
  }

  private void writeToStdinAndFlush(@NotNull Process process, @NotNull String input) throws IOException {
    process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
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
    assertEquals("Hello\r\n", stdout.readLine(1000));
    Process killProcess = Runtime.getRuntime().exec(new String[] {"kill", "-SIGPIPE", String.valueOf(pty.getPid())});
    Gobbler killStdout = startReader(killProcess.getInputStream(), null);
    Gobbler killStderr = startReader(killProcess.getErrorStream(), null);
    killStdout.awaitFinish();
    killStderr.awaitFinish();
    assertEquals("", killStdout.getOutput());
    assertEquals("", killStderr.getOutput());
    assertTrue(killProcess.waitFor(1, TimeUnit.SECONDS));
    assertEquals(0, killProcess.exitValue());
    assertTrue(pty.waitFor(1, TimeUnit.SECONDS));
    assertEquals(PtyHelpers.SIGPIPE, pty.exitValue());
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

  public void testGoDebug() throws IOException, InterruptedException {
    if (Platform.isWindows() || !new File("/bin/bash").isFile()) {
      return;
    }
    PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"/bin/bash", "-c", "echo Success"})
      .setRedirectErrorStream(true)
      .setConsole(true);
    PtyProcess pty = builder.start();
    Gobbler stdout = startReader(pty.getInputStream(), null);
    Gobbler stderr = startReader(pty.getErrorStream(), null);
    assertEquals("Success\r\n", stdout.readLine(1000));
    stdout.awaitFinish();
    stderr.awaitFinish();
    boolean done = pty.waitFor(1, TimeUnit.SECONDS);
    assertTrue(done);
    assertEquals(0, pty.exitValue());
    assertEquals("", stderr.getOutput());
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

    public boolean awaitTextEndsWith(@NotNull String suffix, int timeoutMillis) {
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
          .replace("\u001B[?25l", "").replace("\u001b[?25h", "");
      }
      return text;
    }

    public void assertEndsWith(@NotNull String expectedSuffix) {
      boolean ok = awaitTextEndsWith(expectedSuffix, 1000);
      if (!ok) {
        String output = getOutput();
        String actual = output.substring(Math.max(0, output.length() - expectedSuffix.length()));
        if (expectedSuffix.equals(actual)) {
          fail("awaitTextEndsWith could detect suffix within timeout, but it is there");
        }
        expectedSuffix = convertInvisibleChars(expectedSuffix);
        actual = convertInvisibleChars(actual);
        int lastTextSize = 100;
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
