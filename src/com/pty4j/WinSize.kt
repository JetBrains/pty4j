package com.pty4j

/**
 * Terminal grid and optional text-area pixel dimensions. Equality includes all four dimensions.
 *
 * Unix PTYs preserve pixel dimensions. Windows backends ignore supplied pixel dimensions;
 * [PtyProcess.getWinSize] reports zero pixels on Windows, including after a resize with nonzero pixels.
 *
 * @property columns number of character columns.
 * @property rows number of character rows.
 * @property width text-area width in pixels, or zero when unknown; must fit an unsigned 16-bit native field.
 * @property height text-area height in pixels, or zero when unknown; must fit an unsigned 16-bit native field.
 * @throws IllegalArgumentException if either pixel dimension is outside `0..65535`.
 */
class WinSize(val columns: Int, val rows: Int, val width: Int, val height: Int) {

  init {
    require(width in 0..65535) { "Pixel width must be in 0..65535: $width" }
    require(height in 0..65535) { "Pixel height must be in 0..65535: $height" }
  }

  /**
   * Creates a grid size with unknown pixel dimensions.
   * Keeps the existing two-argument JVM constructor for Java and already compiled callers.
   *
   * @param columns number of character columns.
   * @param rows number of character rows.
   */
  constructor(columns: Int, rows: Int) : this(columns, rows, 0, 0)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WinSize

    return columns == other.columns && rows == other.rows && width == other.width && height == other.height
  }

  override fun hashCode(): Int {
    return ((31 * columns + rows) * 31 + width) * 31 + height
  }

  override fun toString(): String = "columns=$columns, rows=$rows, width=$width, height=$height"
}
