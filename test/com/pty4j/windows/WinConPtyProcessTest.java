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

public class WinConPtyProcessTest {

  @Before
  public void beforeEach() {
    System.setProperty(PtyProcessBuilder.WINDOWS_CON_PTY_SYSTEM_PROPERTY, "true");
  }

  @After
  public void afterEach() {
    System.setProperty(PtyProcessBuilder.WINDOWS_CON_PTY_SYSTEM_PROPERTY, "false");
  }

  @Test
  public void testBasic() throws Exception {
    PtyProcessBuilder builder = new PtyProcessBuilder(TestUtil.getJavaCommand(Printer.class));
    PtyProcess process = builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    Assert.assertTrue(process instanceof WinConPtyProcess);
    stdout.awaitFinish();
    stderr.awaitFinish();
    stdout.assertEndsWith(Printer.STDOUT + "\r\n" + Printer.STDERR + "\r\n");
    Assert.assertEquals("", stderr.getOutput());
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
}
