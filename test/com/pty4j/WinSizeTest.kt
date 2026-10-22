package com.pty4j

import org.junit.Assert.*
import org.junit.Test

class WinSizeTest {
  @Test
  fun `two argument constructor means unknown pixels`() {
    val size = WinSize(80, 24)
    assertEquals(80, size.columns)
    assertEquals(24, size.rows)
    assertEquals(0, size.width)
    assertEquals(0, size.height)
    assertEquals(WinSize(80, 24, 0, 0), size)
    assertEquals(WinSize(80, 24, 0, 0).hashCode(), size.hashCode())
  }

  @Test
  fun `four argument constructor retains pixels and value semantics`() {
    val size = WinSize(80, 24, 1280, 720)
    assertEquals(1280, size.width)
    assertEquals(720, size.height)
    assertEquals(size, WinSize(80, 24, 1280, 720))
    assertEquals(size.hashCode(), WinSize(80, 24, 1280, 720).hashCode())
    assertNotEquals(size, WinSize(81, 24, 1280, 720))
    assertNotEquals(size, WinSize(80, 25, 1280, 720))
    assertNotEquals(size, WinSize(80, 24, 1281, 720))
    assertNotEquals(size, WinSize(80, 24, 1280, 721))
    assertEquals("columns=80, rows=24, width=1280, height=720", size.toString())
  }

  @Test
  fun `pixel dimensions have unsigned native bounds`() {
    assertEquals(0, WinSize(80, 24, 0, 65535).width)
    assertEquals(65535, WinSize(80, 24, 65535, 0).width)
    assertEquals(65535, WinSize(80, 24, 0, 65535).height)
    for (invalid in intArrayOf(-1, Int.MIN_VALUE, 65536, Int.MAX_VALUE)) {
      assertThrows(IllegalArgumentException::class.java) { WinSize(80, 24, invalid, 0) }
      assertThrows(IllegalArgumentException::class.java) { WinSize(80, 24, 0, invalid) }
    }
  }
}
