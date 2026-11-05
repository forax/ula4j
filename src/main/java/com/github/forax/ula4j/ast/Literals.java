package com.github.forax.ula4j.ast;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Decoding of number and string tokens, independent of ANTLR so it can be tested against CPython.
 * Every method takes the position of the token, used for diagnostics.
 */
final class Literals {
  private static final int MAX_DECIMAL_DIGITS = 4300;  // CPython's default int_max_str_digits

  private Literals() {}

  /** Returns a Long, a BigInteger (only if the value does not fit in a long) or a Double. */
  static Object parseNumber(String text, int line, int column) {
    var digits = text.replace("_", "").toLowerCase(Locale.ROOT);
    if (digits.endsWith("j")) {
      throw new UnsupportedSyntaxException("imaginary literal", line, column);
    }
    if (digits.startsWith("0x")) {
      return integer(digits.substring(2), 16, line, column);
    }
    if (digits.startsWith("0o")) {
      return integer(digits.substring(2), 8, line, column);
    }
    if (digits.startsWith("0b")) {
      return integer(digits.substring(2), 2, line, column);
    }
    if (digits.indexOf('.') >= 0 || digits.indexOf('e') >= 0) {
      return Double.parseDouble(digits);  // "1e999" gives Infinity, like CPython
    }
    return integer(digits, 10, line, column);
  }

  private static Object integer(String digits, int radix, int line, int column) {
    if (radix == 10 && digits.length() > MAX_DECIMAL_DIGITS) {
      throw new SyntaxErrorException(
          "exceeds the limit (" + MAX_DECIMAL_DIGITS + " digits) for integer string conversion",
          line, column);
    }
    var value = new BigInteger(digits, radix);
    if (value.bitLength() < 64) {
      return value.longValue();
    }
    return value;
  }

  /** Decodes one STRING token (prefix, quotes and escapes). */
  static String decodeString(String text, int line, int column) {
    var raw = false;
    var start = 0;
    while (true) {
      var c = text.charAt(start);
      if (c == '\'' || c == '"') {
        break;
      }
      switch (Character.toLowerCase(c)) {
        case 'r' -> raw = true;
        case 'u' -> { }
        case 'b' -> throw new UnsupportedSyntaxException("bytes literal", line, column);
        default -> throw new AssertionError("unexpected string prefix in " + text);
      }
      start++;
    }
    var quoteLength = text.startsWith("'''", start) || text.startsWith("\"\"\"", start) ? 3 : 1;
    var body = text.substring(start + quoteLength, text.length() - quoteLength);
    // CPython normalizes source newlines to '\n'
    body = body.replace("\r\n", "\n").replace('\r', '\n');
    if (raw) {
      return body;
    }
    return unescape(body, line, column);
  }

  private static String unescape(String body, int line, int column) {
    if (body.indexOf('\\') < 0) {
      return body;
    }
    var sb = new StringBuilder(body.length());
    var i = 0;
    var n = body.length();
    while (i < n) {
      var c = body.charAt(i++);
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      var e = body.charAt(i++);  // the lexer guarantees a character after the backslash
      switch (e) {
        case '\n' -> { }  // line continuation
        case '\\' -> sb.append('\\');
        case '\'' -> sb.append('\'');
        case '"' -> sb.append('"');
        case 'a' -> sb.append((char) 7);
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'v' -> sb.append((char) 11);
        case '0', '1', '2', '3', '4', '5', '6', '7' -> {
          var value = e - '0';
          var count = 1;
          while (count < 3 && i < n && body.charAt(i) >= '0' && body.charAt(i) <= '7') {
            value = value * 8 + (body.charAt(i++) - '0');
            count++;
          }
          appendCodePoint(sb, value, line, column);
        }
        case 'x' -> i = hexEscape(sb, body, i, 2, line, column);
        case 'u' -> i = hexEscape(sb, body, i, 4, line, column);
        case 'U' -> i = hexEscape(sb, body, i, 8, line, column);
        case 'N' -> i = namedEscape(sb, body, i, line, column);
        default -> sb.append('\\').append(e);  // unknown escapes are kept verbatim
      }
    }
    return sb.toString();
  }

  private static int hexEscape(StringBuilder sb, String body, int start, int digits, int line, int column) {
    if (start + digits > body.length()) {
      throw new SyntaxErrorException("truncated escape sequence", line, column);
    }
    long value = 0;
    for (var k = 0; k < digits; k++) {
      var c = body.charAt(start + k);
      var d = c < 128 ? Character.digit(c, 16) : -1;
      if (d < 0) {
        throw new SyntaxErrorException("invalid hexadecimal digit in escape sequence", line, column);
      }
      value = value * 16 + d;
    }
    if (value > Character.MAX_CODE_POINT) {
      throw new SyntaxErrorException("illegal Unicode character in escape sequence", line, column);
    }
    appendCodePoint(sb, (int) value, line, column);
    return start + digits;
  }

  private static int namedEscape(StringBuilder sb, String body, int start, int line, int column) {
    if (start >= body.length() || body.charAt(start) != '{') {
      throw new SyntaxErrorException("malformed named escape", line, column);
    }
    var end = body.indexOf('}', start);
    if (end < 0) {
      throw new SyntaxErrorException("malformed named escape", line, column);
    }
    var name = body.substring(start + 1, end);
    int codePoint;
    try {
      codePoint = Character.codePointOf(name);
    } catch (IllegalArgumentException _) {
      throw new UnsupportedSyntaxException("unknown unicode character name", line, column);
    }
    // Java also accepts the "<block> <hex>" fallback names of unnamed characters, CPython does not
    var type = Character.getType(codePoint);
    if (!name.equalsIgnoreCase(Character.getName(codePoint))
        || type == Character.UNASSIGNED || type == Character.CONTROL
        || type == Character.SURROGATE || type == Character.PRIVATE_USE) {
      throw new UnsupportedSyntaxException("unknown unicode character name", line, column);
    }
    appendCodePoint(sb, codePoint, line, column);
    return end + 1;
  }

  private static void appendCodePoint(StringBuilder sb, int codePoint, int line, int column) {
    if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
      // a Java String can not hold a lone surrogate without it merging with its neighbor
      throw new UnsupportedSyntaxException("lone surrogate escape", line, column);
    }
    sb.appendCodePoint(codePoint);
  }
}