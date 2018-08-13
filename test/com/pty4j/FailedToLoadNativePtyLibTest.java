package com.pty4j;

import junit.framework.TestCase;
import org.junit.Assert;
import testData.RepeatTextWithTimeout;

import java.io.IOException;

public class FailedToLoadNativePtyLibTest extends TestCase {
  public void testErrorMessage() {
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000", "Hello, World");
    boolean failed = false;
    try {
      PtyProcess.exec(cmd);
    }
    catch (IOException e) {
      failed = true;
    }
    Assert.assertTrue("Library should be failed to loaded unless -DPTY_LIB_FOLDER=os specified", failed);
  }
}
