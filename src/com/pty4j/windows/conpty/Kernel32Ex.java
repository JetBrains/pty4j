package com.pty4j.windows.conpty;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

interface Kernel32Ex extends Library {
    Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
    long PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE = 0x00020016L;

    WinNT.HRESULT CreatePseudoConsole(WinEx.COORDByValue size,
                                      WinNT.HANDLE hInput,
                                      WinNT.HANDLE hOutput,
                                      WinDef.DWORD dwFlags,
                                      WinEx.HPCONByReference phPC);

    void ClosePseudoConsole(WinEx.HPCON hPC);

    WinNT.HRESULT ResizePseudoConsole(WinEx.HPCON hPC, WinEx.COORDByValue size);

    boolean InitializeProcThreadAttributeList(
            Memory lpAttributeList,
            WinDef.DWORD dwAttributeCount,
            WinDef.DWORD dwFlags,
            WinEx.SIZE_TByReference lpSize);

    boolean UpdateProcThreadAttribute(
            Memory lpAttributeList,
            WinDef.DWORD dwFlags,
            BaseTSD.DWORD_PTR Attribute,
            WinDef.PVOID lpValue,
            BaseTSD.SIZE_T cbSize,
            WinDef.PVOID lpPreviousValue,
            WinEx.SIZE_TByReference lpReturnSize);

    boolean CreateProcessW(
            String lpApplicationName,
            char[] lpCommandLine,
            WinBase.SECURITY_ATTRIBUTES lpProcessAttributes,
            WinBase.SECURITY_ATTRIBUTES lpThreadAttributes,
            boolean bInheritHandles,
            WinDef.DWORD dwCreationFlags,
            Pointer lpEnvironment,
            String lpCurrentDirectory,
            WinEx.STARTUPINFOEX lpStartupInfo,
            WinBase.PROCESS_INFORMATION lpProcessInformation);

    boolean CancelIoEx(WinNT.HANDLE hFile, Pointer lpOverlapped);
}