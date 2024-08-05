package testData

import com.pty4j.Ascii

internal class OrderedOscSequences {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      for (i in 0 until args[0].toInt()) {
        val osc = formatOSC(i, "Hello$i")
        print(osc)
        print(i)
      }
    }

    private fun formatOSC(id: Int, body: String): String {
      return "$ESC]$id;$body${Ascii.BEL_CHAR}"
    }

    fun expectedOutput(count: Int): String {
      val result = StringBuilder()
      for (i in 0 until count) {
        result.append(formatOSC(i, "Hello$i"))
        result.append(i.toString())
      }
      return result.toString()
    }
  }
}

private const val ESC: Char = 27.toChar()
