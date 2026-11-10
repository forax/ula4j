package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.error.PyValueError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class StringIntrinsicsTest {

  @Nested
  public final class Str {

    @Test
    public void strString() {
      // `str(str)` is the identity: no quoting, no escaping.
      assertEquals("", StringIntrinsics.$str$(""));
      assertEquals("hello", StringIntrinsics.$str$("hello"));
      assertEquals("it's", StringIntrinsics.$str$("it's"));
      assertEquals("say \"hi\"", StringIntrinsics.$str$("say \"hi\""));
      assertEquals("a\\b", StringIntrinsics.$str$("a\\b"));
      assertEquals("line1\nline2", StringIntrinsics.$str$("line1\nline2"));
    }

    @Test
    public void strBoolean() {
      assertEquals("True", StringIntrinsics.$str$(true));
      assertEquals("False", StringIntrinsics.$str$(false));
    }

    @Test
    public void strLong() {
      assertEquals("0", StringIntrinsics.$str$(0L));
      assertEquals("42", StringIntrinsics.$str$(42L));
      assertEquals("-42", StringIntrinsics.$str$(-42L));
      // Well below MAX_INT_STR_DIGITS, so always accepted.
      assertEquals(Long.toString(Long.MAX_VALUE), StringIntrinsics.$str$(Long.MAX_VALUE));
      assertEquals(Long.toString(Long.MIN_VALUE), StringIntrinsics.$str$(Long.MIN_VALUE));
    }

    @Test
    public void strNone() {
      assertEquals("None", StringIntrinsics.$str$(PyNone.INSTANCE));
    }

    @Test
    public void strTupleUsesRepr() {
      // `str(tuple)` == `repr(tuple)`, and elements use repr.
      assertEquals("()", StringIntrinsics.$str$(new PyTupleN(new Object[0])));
      assertEquals("(1,)", StringIntrinsics.$str$(new PyTuple1(1L)));
      assertEquals("(1, 2)", StringIntrinsics.$str$(new PyTuple2(1L, 2L)));
      // Strings are quoted inside tuples.
      assertEquals("('hi',)", StringIntrinsics.$str$(new PyTuple1("hi")));
      // Nested tuples.
      var inner = new PyTuple1("a");
      var outer = new PyTuple2(inner, 1L);
      assertEquals("(('a',), 1)", StringIntrinsics.$str$(outer));
      // Mixed scalar types.
      assertEquals("(1, 'two', True, None)", StringIntrinsics.$str$(
          new PyTupleN(new Object[] {1L, "two", true, PyNone.INSTANCE})));
    }

    @Test
    public void strUnsupported() {
      var ex = assertThrows(UnimplementedFeatureError.class,
          () -> StringIntrinsics.$str$(new Object()));
      assertEquals("str of java.lang.Object", ex.getMessage());
    }
  }


  @Nested
  public final class Repr {

    @Test
    public void reprString() {
      // `repr(str)` quotes and escapes — see the `Quote` nested class for details.
      assertEquals("'hello'", StringIntrinsics.$repr$("hello"));
      assertEquals("''", StringIntrinsics.$repr$(""));
    }

    @Test
    public void reprBoolean() {
      assertEquals("True", StringIntrinsics.$repr$(true));
      assertEquals("False", StringIntrinsics.$repr$(false));
    }

    @Test
    public void reprLong() {
      assertEquals("0", StringIntrinsics.$repr$(0L));
      assertEquals("42", StringIntrinsics.$repr$(42L));
      assertEquals("-42", StringIntrinsics.$repr$(-42L));
    }

    @Test
    public void reprNone() {
      assertEquals("None", StringIntrinsics.$repr$(PyNone.INSTANCE));
    }

    @Test
    public void reprTuple() {
      assertEquals("()", StringIntrinsics.$repr$(new PyTupleN(new Object[0])));
      assertEquals("(1,)", StringIntrinsics.$repr$(new PyTuple1(1L)));
      assertEquals("(1, 2)", StringIntrinsics.$repr$(new PyTuple2(1L, 2L)));
      // Elements go through repr, so strings are quoted.
      assertEquals("('a', 'b')", StringIntrinsics.$repr$(new PyTuple2("a", "b")));
      assertEquals("(1, 'two', True)",
          StringIntrinsics.$repr$(new PyTupleN(new Object[] {1L, "two", true})));
    }

    @Test
    public void reprUnsupported() {
      var ex = assertThrows(UnimplementedFeatureError.class,
          () -> StringIntrinsics.$repr$(new Object()));
      assertEquals("str of java.lang.Object", ex.getMessage());
    }
  }


  @Nested
  public final class Float {

    @Test
    public void floatSpecialValues() {
      assertEquals("nan", StringIntrinsics.$str$(Double.NaN));
      assertEquals("inf", StringIntrinsics.$str$(Double.POSITIVE_INFINITY));
      assertEquals("-inf", StringIntrinsics.$str$(Double.NEGATIVE_INFINITY));
      // `repr` uses the same formatting for floats.
      assertEquals("nan", StringIntrinsics.$repr$(Double.NaN));
      assertEquals("inf", StringIntrinsics.$repr$(Double.POSITIVE_INFINITY));
      assertEquals("-inf", StringIntrinsics.$repr$(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void floatSignedZero() {
      assertEquals("0.0", StringIntrinsics.$str$(0.0));
      assertEquals("-0.0", StringIntrinsics.$str$(-0.0));
      assertEquals("0.0", StringIntrinsics.$repr$(0.0));
      assertEquals("-0.0", StringIntrinsics.$repr$(-0.0));
    }

    @Test
    public void floatIntegralValuesKeepDotZero() {
      // A float must remain distinguishable from the int of the same value.
      assertEquals("1.0", StringIntrinsics.$str$(1.0));
      assertEquals("2.0", StringIntrinsics.$str$(2.0));
      assertEquals("-3.0", StringIntrinsics.$str$(-3.0));
      assertEquals("100.0", StringIntrinsics.$str$(100.0));
      assertEquals("-100.0", StringIntrinsics.$str$(-100.0));
    }

    @Test
    public void floatSmallDecimals() {
      assertEquals("0.1", StringIntrinsics.$str$(0.1));
      assertEquals("0.5", StringIntrinsics.$str$(0.5));
      assertEquals("1.5", StringIntrinsics.$str$(1.5));
      assertEquals("-1.5", StringIntrinsics.$str$(-1.5));
      assertEquals("3.14", StringIntrinsics.$str$(3.14));
    }

    @Test
    public void floatScientificThresholds() {
      // CPython uses scientific for exponent < -4 or >= 16.
      // Just inside the plain range:
      assertEquals("0.0001", StringIntrinsics.$str$(1e-4));         // exponent -4
      assertEquals("1000000000000000.0", StringIntrinsics.$str$(1e15)); // exponent 15
      // Just outside:
      assertEquals("1e-05", StringIntrinsics.$str$(1e-5));          // exponent -5
      assertEquals("1e+16", StringIntrinsics.$str$(1e16));          // exponent 16
    }

    @Test
    public void floatScientificExponentFormat() {
      // Two-digit zero-padded minimum.
      assertEquals("1e+16", StringIntrinsics.$str$(1e16));
      assertEquals("1e-16", StringIntrinsics.$str$(1e-16));
      // Three-digit stays as-is.
      assertEquals("1e+100", StringIntrinsics.$str$(1e100));
      assertEquals("1e-100", StringIntrinsics.$str$(1e-100));
    }

    @Test
    public void floatExtremes() {
      // MAX_VALUE has exponent 308 → scientific.
      assertEquals("1.7976931348623157e+308",
          StringIntrinsics.$str$(Double.MAX_VALUE));
      assertEquals("-1.7976931348623157e+308",
          StringIntrinsics.$str$(-Double.MAX_VALUE));
      // Smallest denormal is "5e-324" — the shortest round-trip,
      // not "4.9e-324".
      assertEquals("5e-324", StringIntrinsics.$str$(Double.MIN_VALUE));
      assertEquals("-5e-324", StringIntrinsics.$str$(-Double.MIN_VALUE));
    }

    @Test
    public void floatRoundTrip() {
      // The shortest decimal representation must parse back to the same double.
      var values = new double[] {
          0.1, 1.0 / 3.0, Math.PI, Math.E, 1.1, 2.2, 3.3,
          Double.MIN_NORMAL, Double.MIN_VALUE, Double.MAX_VALUE,
          -1.0 / 7.0, 1e-300, 1e300
      };
      for (var v : values) {
        var s = StringIntrinsics.$str$(v);
        assertEquals(0.0, Double.compare(v, Double.parseDouble(s)),
            "round-trip failed for " + v + " (\"str\" gave \"" + s + "\")");
      }
    }
  }


  @Nested
  public final class Quote {

    @Test
    public void emptyString() {
      assertEquals("''", StringIntrinsics.$repr$(""));
    }

    @Test
    public void simpleString() {
      assertEquals("'hello'", StringIntrinsics.$repr$("hello"));
      assertEquals("'abc'", StringIntrinsics.$repr$("abc"));
    }

    @Test
    public void quoteSelection() {
      // Only single quotes → prefer double quotes.
      assertEquals("\"it's\"", StringIntrinsics.$repr$("it's"));
      // Only double quotes → prefer single quotes.
      assertEquals("'say \"hi\"'", StringIntrinsics.$repr$("say \"hi\""));
      // Both present → fall back to single quotes and escape them.
      assertEquals("'a\\'b\"c'", StringIntrinsics.$repr$("a'b\"c"));
    }

    @Test
    public void backslashAndWhitespace() {
      assertEquals("'\\\\'", StringIntrinsics.$repr$("\\"));
      assertEquals("'\\t'", StringIntrinsics.$repr$("\t"));
      assertEquals("'\\n'", StringIntrinsics.$repr$("\n"));
      assertEquals("'\\r'", StringIntrinsics.$repr$("\r"));
      assertEquals("'a\\tb\\nc'", StringIntrinsics.$repr$("a\tb\nc"));
    }

    @Test
    public void controlCharacters() {
      assertEquals("'\\x00'", StringIntrinsics.$repr$("\u0000"));
      assertEquals("'\\x1f'", StringIntrinsics.$repr$("\u001f"));
      assertEquals("'\\x7f'", StringIntrinsics.$repr$("\u007f"));
    }

    @Test
    public void nonPrintableUnicodeUsesEscapes() {
      // Non-breaking space (category Zs → space separator).
      assertEquals("'\\xa0'", StringIntrinsics.$repr$("\u00a0"));
      // Zero-width space (category Cf → format).
      assertEquals("'\\u200b'", StringIntrinsics.$repr$("\u200b"));
      // Private-use area.
      assertEquals("'\\ue000'", StringIntrinsics.$repr$("\ue000"));
      // Line separator.
      assertEquals("'\\u2028'", StringIntrinsics.$repr$("\u2028"));
      // Paragraph separator.
      assertEquals("'\\u2029'", StringIntrinsics.$repr$("\u2029"));
    }

    @Test
    public void printableUnicodeIsNotEscaped() {
      // Letter (Lu).
      assertEquals("'\u0100'", StringIntrinsics.$repr$("\u0100"));
      // Combining mark (Mn).
      assertEquals("'\u0300'", StringIntrinsics.$repr$("\u0300"));
    }

    @Test
    public void supplementaryCharacters() {
      // Supplementary letter (Deseret capital long I, U+10400) — printable.
      assertEquals("'\uD801\uDC00'",
          StringIntrinsics.$repr$("\uD801\uDC00"));
      // Emoji (So) — printable.
      assertEquals("'😀'", StringIntrinsics.$repr$("😀"));
      // Supplementary private-use is not printable → \U form.
      assertEquals("'\\U000f0000'",
          StringIntrinsics.$repr$("\uDB80\uDC00")); // U+F0000
    }
  }


  @Nested
  public final class IntLimit {

    // Mirrors StringIntrinsics.MAX_INT_STR_DIGITS (CPython 3.14 default).
    private static final int LIMIT = 4_300;

    @Test
    public void exactlyAtLimitIsAccepted() {
      var atLimit = BigInteger.TEN.pow(LIMIT - 1);       // 1 followed by 4299 zeros
      assertEquals(atLimit.toString(), StringIntrinsics.$str$(atLimit));
      assertEquals(atLimit.toString(), StringIntrinsics.$repr$(atLimit));
      // Sign is not counted.
      assertEquals(atLimit.negate().toString(), StringIntrinsics.$str$(atLimit.negate()));
    }

    @Test
    public void oneBeyondLimitIsRejected() {
      var beyond = BigInteger.TEN.pow(LIMIT);            // 1 followed by 4300 zeros
      assertThrows(PyValueError.class, () -> StringIntrinsics.$str$(beyond));
      assertThrows(PyValueError.class, () -> StringIntrinsics.$str$(beyond.negate()));
      assertThrows(PyValueError.class, () -> StringIntrinsics.$repr$(beyond));
    }

    @Test
    public void errorMessageMatchesCpython() {
      var beyond = BigInteger.TEN.pow(LIMIT);
      var ex = assertThrows(PyValueError.class,
          () -> StringIntrinsics.$str$(beyond));
      assertEquals(
          "Exceeds the limit (4300 digits) for integer string conversion; "
              + "use sys.set_int_max_str_digits() to increase the limit",
          ex.getMessage());
    }
  }


  @Nested
  public final class ErrorMessage {

    @Test
    public void unsupportedStrMessages() {
      // Message includes the runtime class name.
      assertEquals("str of java.lang.Object",
          assertThrows(UnimplementedFeatureError.class,
              () -> StringIntrinsics.$str$(new Object())).getMessage());
      assertEquals("str of java.lang.Character",
          assertThrows(UnimplementedFeatureError.class,
              () -> StringIntrinsics.$str$('c')).getMessage());
    }

    @Test
    public void unsupportedReprMessages() {
      assertEquals("str of java.lang.Object",
          assertThrows(UnimplementedFeatureError.class,
              () -> StringIntrinsics.$repr$(new Object())).getMessage());
    }
  }
}