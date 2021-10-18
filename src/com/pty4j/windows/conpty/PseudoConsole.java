package com.pty4j.windows.conpty;

import com.pty4j.WinSize;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class PseudoConsole {

  private final WinEx.HPCON hpc;
  private WinSize myLastWinSize;
  private boolean myClosed = false;

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
    myLastWinSize = size;
  }

  public WinEx.HPCON getHandle() {
    return hpc;
  }

  public void resize(WinSize newSize) throws IOException {
    if (!Kernel32Ex.INSTANCE.ResizePseudoConsole(hpc, getSizeCoords(newSize)).equals(WinError.S_OK)) {
      throw new LastErrorExceptionEx("ResizePseudoConsole");
    }
    myLastWinSize = newSize;
  }

  public @NotNull WinSize getWinSize() throws IOException {
    if (myClosed) {
      throw new IOException(WinConPtyProcess.class.getName() + ": unable to get window size for closed PseudoConsole");
    }
    return myLastWinSize;
  }

  public void close() {
    if (!myClosed) {
      myClosed = true;
      Kernel32Ex.INSTANCE.ClosePseudoConsole(hpc);
    }
  }
}
