package com.pty4j

class WinSize(val columns: Int, val rows: Int) {

  @Suppress("UNUSED_PARAMETER")
  @Deprecated("Use WinSize(columns: Int, width: Int) constructor instead", replaceWith = ReplaceWith("WinSize(columns, width))"))
  constructor(columns: Int, rows: Int, width: Int, height: Int) : this(columns, rows)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WinSize

    return columns == other.columns && rows == other.rows
  }

  override fun hashCode(): Int {
    return 31 * columns + rows
  }

  override fun toString(): String = "columns=$columns, rows=$rows"
}
