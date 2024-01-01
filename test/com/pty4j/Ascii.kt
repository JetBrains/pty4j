package com.pty4j

internal object Ascii {

  /**
   * End of Text: A communication control character used to terminate a sequence of characters
   * started with STX and transmitted as an entity.
   */
  const val ETX: Byte = 3

  const val ETX_CHAR: Char = ETX.toInt().toChar()

  /**
   * Bell ('\a'): A character for use when there is a need to call for human attention. It may
   * control alarm or attention devices.
   */
  const val BEL: Byte = 7

  const val BEL_CHAR: Char = BEL.toInt().toChar()

  /**
   * Backspace ('\b'): A format effector which controls the movement of the printing position one
   * printing space backward on the same printing line. (Applicable also to display devices.)
   */
  const val BS: Byte = 8

}
