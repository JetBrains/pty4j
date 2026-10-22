package com.pty4j.unix;

import com.pty4j.WinSize;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WinSizeStructureTest {
  @Test
  public void testNativeLayoutAndUnsignedDimensions() {
    for (int value : new int[]{0, 1, 32767, 32768, 65535}) {
      WinSize size = new WinSize(value, value, value, value);
      NativePtyExecutor.WinSizeStructure nativeSize = new NativePtyExecutor.WinSizeStructure(size);
      assertEquals(8, nativeSize.size());
      nativeSize.write();
      for (int offset = 0; offset < 8; offset += 2) {
        assertEquals(value, Short.toUnsignedInt(nativeSize.getPointer().getShort(offset)));
      }
      nativeSize.read();
      assertEquals(size, nativeSize.toWinSize());
    }
  }

  @Test
  public void testFieldsKeepTheirNativeOrder() {
    NativePtyExecutor.WinSizeStructure nativeSize = new NativePtyExecutor.WinSizeStructure(new WinSize(80, 24, 1280, 720));
    nativeSize.write();
    assertEquals(24, nativeSize.getPointer().getShort(0));
    assertEquals(80, nativeSize.getPointer().getShort(2));
    assertEquals(1280, nativeSize.getPointer().getShort(4));
    assertEquals(720, nativeSize.getPointer().getShort(6));
    nativeSize.getPointer().setShort(4, (short)65535);
    nativeSize.getPointer().setShort(6, (short)32768);
    nativeSize.read();
    assertEquals(new WinSize(80, 24, 65535, 32768), nativeSize.toWinSize());
  }
}
