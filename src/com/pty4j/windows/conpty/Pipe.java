package com.pty4j.windows.conpty;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

import java.io.IOException;

final class Pipe {

  private final WinNT.HANDLE readPipe;
  private final WinNT.HANDLE writePipe;

  public Pipe() throws IOException {
    WinNT.HANDLEByReference readPipeRef = new WinNT.HANDLEByReference();
    WinNT.HANDLEByReference writePipeRef = new WinNT.HANDLEByReference();
    if (!Kernel32.INSTANCE.CreatePipe(readPipeRef, writePipeRef, null, 0)) {
      throw new LastErrorExceptionEx("CreatePipe");
    }
    readPipe = readPipeRef.getValue();
    writePipe = writePipeRef.getValue();
  }

  public WinNT.HANDLE getReadPipe() {
    return readPipe;
  }

  public WinNT.HANDLE getWritePipe() {
    return writePipe;
  }
}
