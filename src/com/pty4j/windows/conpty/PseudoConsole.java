package com.pty4j.windows.conpty;

import com.pty4j.WinSize;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;

import java.io.IOException;

final class PseudoConsole {

  private final WinEx.HPCON hpc;

  private static WinEx.COORDByValue getSizeCoords(WinSize size) {
    WinEx.COORDByValue sizeCoords = new WinEx.COORDByValue();
    sizeCoords.X = (short) size.getColumns();
    sizeCoords.Y = (short) size.getRows();
    return sizeCoords;
  }

  public PseudoConsole(WinSize size, WinNT.HANDLE input, WinNT.HANDLE output) throws LastErrorExceptionEx {
    WinEx.HPCONByReference hpcByReference = new WinEx.HPCONByReference();
    if (!Kernel32Ex.INSTANCE.CreatePseudoConsole(getSizeCoords(size), input, output, new WinDef.DWORD(0L), hpcByReference).equals(WinError.S_OK)) {
      throw new LastErrorExceptionEx("CreatePseudoConsole");
    }
    hpc = hpcByReference.getValue();
  }

  public WinEx.HPCON getHandle() {
    return hpc;
  }

  public void resize(WinSize newSize) throws IOException {
    if (!Kernel32Ex.INSTANCE.ResizePseudoConsole(hpc, getSizeCoords(newSize)).equals(WinError.S_OK)) {
      throw new LastErrorExceptionEx("ResizePseudoConsole");
    }
  }

  public void close() {
    Kernel32Ex.INSTANCE.ClosePseudoConsole(hpc);
  }
}
