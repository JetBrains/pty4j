package com.pty4j.util

internal object ParametersListUtil {
  /**
   * Splits single parameter string (as created by [.join]) into list of parameters.
   *
   * **Conversion rules:**
   *
   *  * starting/whitespaces are trimmed;
   *  * parameters are split by whitespaces, whitespaces itself are dropped
   *  * parameters inside double quotes (`"a b"`) are kept as single one;
   *  * double quotes are dropped, escaped double quotes (`&#92;"`) are un-escaped.
   *  * For single quotes support see [.parse]
   *
   * **Examples:**
   *
   * `' a  b ' => ['a', 'b']`<br></br>
   * `'a="1 2" b' => ['a=1 2', 'b']`<br></br>
   * `'a " " b' => ['a', ' ', 'b']`<br></br>
   * `'"a &#92;"1 2&#92;"" b' => ['a="1 2"', 'b']`
   *
   * @param parameterString parameter string to split.
   * @return list of parameters.
   */
  fun parse(parameterString: String): List<String> {
    return parse(parameterString, false)
  }

  fun parse(parameterString: String, keepQuotes: Boolean): ArrayList<String> {
    return parse(parameterString, keepQuotes, false)
  }

  fun parse(parameterString: String, keepQuotes: Boolean, supportSingleQuotes: Boolean): ArrayList<String> {
    return parse(parameterString, keepQuotes, supportSingleQuotes, false)
  }

  fun parse(
    parameterString: String,
    keepQuotes: Boolean,
    supportSingleQuotes: Boolean,
    keepEmptyParameters: Boolean
  ): ArrayList<String> {
    var parameterString = parameterString
    if (!keepEmptyParameters) {
      parameterString = parameterString.trim { it <= ' ' }
    }

    val params: ArrayList<String> = ArrayList()
    if (parameterString.isEmpty()) {
      return params
    }
    val token = StringBuilder(128)
    var inQuotes = false
    var escapedQuote = false

    fun isPossibleQuote(ch: Char): Boolean = ch == '"' || (supportSingleQuotes && ch == '\'')

    var currentQuote = 0.toChar()
    var nonEmpty = false

    for (i in 0..<parameterString.length) {
      val ch = parameterString.get(i)
      if ((if (inQuotes) currentQuote == ch else isPossibleQuote(ch))) {
        if (!escapedQuote) {
          inQuotes = !inQuotes
          currentQuote = ch
          nonEmpty = true
          if (!keepQuotes) {
            continue
          }
        }
        escapedQuote = false
      }
      else if (Character.isWhitespace(ch)) {
        if (!inQuotes) {
          if (keepEmptyParameters || token.length > 0 || nonEmpty) {
            params.add(token.toString())
            token.setLength(0)
            nonEmpty = false
          }
          continue
        }
      }
      else if (ch == '\\' && i < parameterString.length - 1) {
        val nextchar = parameterString.get(i + 1)
        if (if (inQuotes) currentQuote == nextchar else isPossibleQuote(nextchar)) {
          escapedQuote = true
          if (!keepQuotes) {
            continue
          }
        }
      }

      token.append(ch)
    }

    if (keepEmptyParameters || token.length > 0 || nonEmpty) {
      params.add(token.toString())
    }

    return params
  }
}