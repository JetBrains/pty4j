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
package com.pty4j.unix;

import com.sun.jna.Platform;
import junit.framework.TestCase;

/** Regression test for #181: breakRead() must not write after close(). */
public class PtyBreakReadTest extends TestCase {

  public void testBreakReadAfterCloseDoesNotThrow() throws Exception {
    if (Platform.isWindows()) return;

    Pty pty = new Pty();
    pty.breakRead();
    pty.close();
    assertTrue(pty.isClosed());

    pty.breakRead(); // the call this fix guards
    pty.close();
    assertTrue(pty.isClosed());
  }
}
