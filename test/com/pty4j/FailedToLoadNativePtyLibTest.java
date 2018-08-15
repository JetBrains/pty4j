package com.pty4j;

import org.junit.Test;
import testData.RepeatTextWithTimeout;

import java.io.IOException;

public class FailedToLoadNativePtyLibTest {

  @Test(expected = IOException.class)
  public void testErrorMessage() throws IOException {
    TestUtil.unsetLocalPtyLib();
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000", "Hello, World");
    PtyProcess.exec(cmd);
  }
}
