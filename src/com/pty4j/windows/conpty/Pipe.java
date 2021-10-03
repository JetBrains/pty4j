package com.pty4j.windows.conpty;

import com.sun.jna.platform.win32.WinNT;

public class Pipe implements AutoCloseable {

    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private final WinNT.HANDLE readPipe;
    private final WinNT.HANDLE writePipe;

    public Pipe() {
        WinNT.HANDLEByReference readPipeRef = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference writePipeRef = new WinNT.HANDLEByReference();
        if (!kernel32.CreatePipe(readPipeRef, writePipeRef, null, 0)) {
            Kernel32.throwLastError();
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

    @Override
    public void close() {
        if (!kernel32.CloseHandle(readPipe)) {
            Kernel32.throwLastError();
        }
        if (!kernel32.CloseHandle(writePipe)) {
            Kernel32.throwLastError();
        }
    }
}
