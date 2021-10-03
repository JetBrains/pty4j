package com.pty4j.windows.conpty;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.win32.W32APIOptions;

interface Kernel32 extends com.sun.jna.platform.win32.Kernel32 {
    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);
    DWORD_PTR PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE = new DWORD_PTR(0x00020016L);

    static void throwLastError() {
        int lastError = Native.getLastError();
        throw new LastErrorException(lastError);
    }

    HRESULT CreatePseudoConsole(WinNT.COORDByValue size, HANDLE hInput, HANDLE hOutput, DWORD dwFlags, WinNT.HPCONByReference phPC);
    void ClosePseudoConsole(WinNT.HPCON hPC);
    HRESULT ResizePseudoConsole(WinNT.HPCON hPC, WinNT.COORDByValue size);

    boolean InitializeProcThreadAttributeList(
            Memory lpAttributeList,
            DWORD dwAttributeCount,
            DWORD dwFlags,
            WinNT.SIZE_TByReference lpSize);
    boolean UpdateProcThreadAttribute(
            Memory lpAttributeList,
            DWORD dwFlags,
            DWORD_PTR Attribute,
            PVOID lpValue,
            SIZE_T cbSize,
            PVOID lpPreviousValue,
            WinNT.SIZE_TByReference lpReturnSize);

    boolean CreateProcessW(
            String lpApplicationName,
            char[] lpCommandLine,
            WinBase.SECURITY_ATTRIBUTES lpProcessAttributes,
            WinBase.SECURITY_ATTRIBUTES lpThreadAttributes,
            boolean bInheritHandles,
            DWORD dwCreationFlags,
            Pointer lpEnvironment,
            String lpCurrentDirectory,
            WinNT.STARTUPINFOEX lpStartupInfo,
            WinBase.PROCESS_INFORMATION lpProcessInformation);
}