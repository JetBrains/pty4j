package com.pty4j.windows.cygwin;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;

class CygwinKernel32 {

  static final Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);

  interface Kernel32Ex extends Library {
    WinNT.HANDLE CreateNamedPipeA(String lpName,
                                  int dwOpenMode,
                                  int dwPipeMode,
                                  int nMaxInstances,
                                  int nOutBufferSize,
                                  int nInBufferSize,
                                  int nDefaultTimeout,
                                  WinBase.SECURITY_ATTRIBUTES securityAttributes);

    WinNT.HANDLE CreateEventA(WinBase.SECURITY_ATTRIBUTES lpEventAttributes, boolean bManualReset, boolean bInitialState, String lpName);

    @SuppressWarnings("UnusedReturnValue")
    boolean CancelIo(WinNT.HANDLE hFile);
  }

}
