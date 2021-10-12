package com.pty4j.windows.conpty;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.ptr.ByReference;

interface WinEx {
  class HPCON extends PointerType {}

  class HPCONByReference extends ByReference {
    public HPCONByReference() {
      super(Native.POINTER_SIZE);
    }

    public HPCON getValue() {
      Pointer p = getPointer().getPointer(0);
      if (p == null) {
        return null;
      }
      HPCON hpc = new HPCON();
      hpc.setPointer(p);
      return hpc;
    }
  }

  @Structure.FieldOrder({"X", "Y"})
  class COORDByValue extends Structure implements Structure.ByValue {
    public short X;
    public short Y;
  }

  // Since SIZE_T extends ULONG_PTR, let SIZE_TByReference to extend ULONG_PTRByReference.
  class SIZE_TByReference extends BaseTSD.ULONG_PTRByReference {
    @Override
    public BaseTSD.ULONG_PTR getValue() {
      BaseTSD.ULONG_PTR value = super.getValue();
      return new BaseTSD.SIZE_T(value.longValue());
    }
  }

  @Structure.FieldOrder({"StartupInfo", "lpAttributeList"})
  class STARTUPINFOEX extends Structure {
    public WinBase.STARTUPINFO StartupInfo;
    public Pointer lpAttributeList;
  }
}
