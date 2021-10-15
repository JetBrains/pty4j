package com.pty4j.windows;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyTest;
import com.pty4j.TestUtil;
import com.pty4j.windows.conpty.WinConPtyProcess;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import testData.Printer;
import testData.PromptReader;

import java.io.IOException;

public class ConPtyProcessTest {

  @Before
  public void beforeEach() {
    System.setProperty(PtyProcessBuilder.WINDOWS_CON_PTY_SYSTEM_PROPERTY, "true");
  }

  @After
  public void afterEach() {
    System.setProperty(PtyProcessBuilder.WINDOWS_CON_PTY_SYSTEM_PROPERTY, "false");
  }

  @Test
  public void testEmpty() {
  }

  public void _testProcessBuilder() throws IOException, InterruptedException {
    String[] cmd = TestUtil.getJavaCommand(Printer.class);
    PtyProcessBuilder builder = new PtyProcessBuilder(cmd);
    PtyProcess process = builder.start();
    Assert.assertTrue(process instanceof WinConPtyProcess);
    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testPromptReader() throws Exception {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(PromptReader.class))
        .setInitialColumns(200)
        .setConsole(false);
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

  public void _testHelloWorld() throws IOException, InterruptedException {
    String[] cmd = TestUtil.getJavaCommand(Printer.class);
    PtyProcessBuilder builder = new PtyProcessBuilder(cmd);
    PtyProcess process = builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(Printer.STDOUT);
    stderr.assertEndsWith(Printer.STDERR);
    PtyTest.assertProcessTerminatedNormally(process);
  }
}
