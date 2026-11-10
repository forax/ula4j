package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTuple3;
import com.github.forax.ula4j.rt.PyTuple.PyTuple4;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.RT;
import com.github.forax.ula4j.rt.error.PyValueError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class StringIntrinsicsTest {

  @Nested
  public final class Str {

    @Test
    public void strScalars() {
      assertEquals("None", StringIntrinsics.$str$(PyNone.INSTANCE));
      assertEquals("True", StringIntrinsics.$str$(true));
      assertEquals("False", StringIntrinsics.$str$(false));
      assertEquals("0", StringIntrinsics.$str$(0L));
      assertEquals("-1", StringIntrinsics.$str$(-1L));
      assertEquals("1", StringIntrinsics.$str$(BigInteger.ONE));
      assertEquals("123456789012345678901234567890",
          StringIntrinsics.$str$(new BigInteger("123456789012345678901234567890")));
      assertEquals("hello", StringIntrinsics.$str$("hello"));
    }

    @Test
    public void strTupleUsesElementRepr() {
      assertEquals("()", StringIntrinsics.$str$(new PyTupleN(new Object[0])));
      assertEquals("(1,)", StringIntrinsics.$str$(new PyTuple1(1L)));
      assertEquals("(1, 'hello')", StringIntrinsics.$str$(new PyTuple2(1L, "hello")));
      assertEquals("(1, 'hello', True)",
          StringIntrinsics.$str$(new PyTuple3(1L, "hello", true)));
      assertEquals("(1, 'hello', True, None)",
          StringIntrinsics.$str$(new PyTuple4(1L, "hello", true,
              PyNone.INSTANCE)));
    }

    @Test
    public void strNestedTuple() {
      var inner = new PyTuple2(1L, "hello");
      var outer = new PyTuple2(inner, new PyTuple1("world"));
      assertEquals("((1, 'hello'), ('world',))", StringIntrinsics.$str$(outer));
    }

    @Test
    public void strNonCanonicalTupleN() {
      // PyTupleN is expected to work for any arity, not only the canonical 0 and 5+ shapes.
      assertEquals("(1,)", StringIntrinsics.$str$(new PyTupleN(new Object[]{1L})));
      assertEquals("(1, 'a')", StringIntrinsics.$str$(new PyTupleN(new Object[]{1L, "a"})));
    }
  }

  @Nested
  public final class Repr {

    @Test
    public void reprScalars() {
      assertEquals("None", StringIntrinsics.$repr$(PyNone.INSTANCE));
      assertEquals("True", StringIntrinsics.$repr$(true));
      assertEquals("False", StringIntrinsics.$repr$(false));
      assertEquals("123", StringIntrinsics.$repr$(123L));
      assertEquals("-123", StringIntrinsics.$repr$(-123L));
      assertEquals("123456789012345678901234567890",
          StringIntrinsics.$repr$(new BigInteger("123456789012345678901234567890")));
    }

    @Test
    public void reprChoosesQuoteToMinimizeEscaping() {
      assertEquals("'hello'", StringIntrinsics.$repr$("hello"));
      assertEquals("\"don't\"", StringIntrinsics.$repr$("don't"));
      assertEquals("'say \"hello\"'", StringIntrinsics.$repr$("say \"hello\""));
      assertEquals("'both \\' and \"'", StringIntrinsics.$repr$("both ' and \""));
      assertEquals("''", StringIntrinsics.$repr$(""));
    }

    @Test
    public void reprEscapesBackslashesAndControlCharacters() {
      assertEquals("'\\\\'", StringIntrinsics.$repr$("\\"));
      assertEquals("'a\\nb\\tc\\r'", StringIntrinsics.$repr$("a\nb\tc\r"));
      assertEquals("'\\x00\\x0b\\x1f'",
          StringIntrinsics.$repr$(new String(new char[]{0, 0x0b, 0x1f})));
      assertEquals("'\\x7f'", StringIntrinsics.$repr$(String.valueOf((char) 0x7f)));
    }

    @Test
    public void reprKeepsPrintableUnicode() {
      assertEquals("'café 😀'", StringIntrinsics.$repr$("café 😀"));
      assertEquals("'\\u200b'", StringIntrinsics.$repr$(String.valueOf('\u200b')));
    }

    @Test
    public void reprTupleUsesReprForItsElements() {
      assertEquals("()", StringIntrinsics.$repr$(new PyTupleN(new Object[0])));
      assertEquals("('hello',)", StringIntrinsics.$repr$(new PyTuple1("hello")));
      assertEquals("('hello', 123)", StringIntrinsics.$repr$(new PyTuple2("hello", 123L)));
      assertEquals("(('hello',), ('world', 123))",
          StringIntrinsics.$repr$(new PyTuple2(
              new PyTuple1("hello"), new PyTuple2("world", 123L))));
    }
  }

  @Nested
  public final class Float {

    @Test
    public void floatSpecialValues() {
      assertEquals("inf", StringIntrinsics.$str$(Double.POSITIVE_INFINITY));
      assertEquals("-inf", StringIntrinsics.$str$(Double.NEGATIVE_INFINITY));
      assertEquals("nan", StringIntrinsics.$str$(Double.NaN));
      assertEquals("0.0", StringIntrinsics.$str$(0.0d));
      assertEquals("-0.0", StringIntrinsics.$str$(-0.0d));
    }

    @Test
    public void floatUsesPythonFixedAndScientificNotationThresholds() {
      assertEquals("0.0001", StringIntrinsics.$str$(1e-4));
      assertEquals("1e-05", StringIntrinsics.$str$(1e-5));
      assertEquals("0.001", StringIntrinsics.$str$(1e-3));
      assertEquals("1000000000000000.0", StringIntrinsics.$str$(1e15));
      assertEquals("1e+16", StringIntrinsics.$str$(1e16));
    }

    @Test
    public void floatUsesShortestRoundTripRepresentation() {
      assertEquals("1.0", StringIntrinsics.$str$(1.0d));
      assertEquals("1.5", StringIntrinsics.$str$(1.5d));
      assertEquals("1.2345678901234567", StringIntrinsics.$str$(1.2345678901234567d));
      assertEquals("5e-324", StringIntrinsics.$str$(Double.MIN_VALUE));
      assertEquals("2.2250738585072014e-308", StringIntrinsics.$str$(Double.MIN_NORMAL));
      assertEquals("1.7976931348623157e+308", StringIntrinsics.$str$(Double.MAX_VALUE));
    }

    @Test
    public void reprFloatMatchesStrFloat() {
      for (var value : new double[]{0.0, -0.0, 1.0, -1.25, 1e-5,
          1e16, Double.MIN_VALUE, Double.MAX_VALUE,
          Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN}) {
        assertEquals(StringIntrinsics.$str$(value), StringIntrinsics.$repr$(value));
      }
    }
  }

  @Nested
  public final class IntStringLimit {

    @Test
    public void integerAtDefaultDigitLimitIsAllowed() {
      var value = BigInteger.TEN.pow(4299);  // 4,300 decimal digits
      var result = StringIntrinsics.$str$(value);
      assertEquals(4300, result.length());
      assertEquals(value.toString(), result);
    }

    @Test
    public void integerAboveDefaultDigitLimitRaisesValueError() {
      var value = BigInteger.TEN.pow(4300);  // 4,301 decimal digits
      var error = assertThrows(PyValueError.class, () -> StringIntrinsics.$str$(value));
      assertEquals("Exceeds the limit (4300 digits) for integer string conversion; "
          + "use sys.set_int_max_str_digits() to increase the limit", error.getMessage());
    }

    @Test
    public void reprAlsoAppliesIntegerDigitLimit() {
      var value = BigInteger.TEN.pow(4300);
      assertThrows(PyValueError.class, () -> StringIntrinsics.$repr$(value));
    }
  }

  @Nested
  public final class Errors {

    @Test
    public void unsupportedRuntimeTypeRaisesUnimplementedFeatureError() {
      assertThrows(UnimplementedFeatureError.class,
          () -> StringIntrinsics.$str$(new Object()));
      assertThrows(UnimplementedFeatureError.class,
          () -> StringIntrinsics.$repr$(new Object()));
    }

    @Test
    public void javaNullIsRejected() {
      assertThrows(NullPointerException.class,
          () -> StringIntrinsics.$str$(null));
      assertThrows(NullPointerException.class,
          () -> StringIntrinsics.$repr$(null));
    }
  }

  @Test
  public void specializedIntrinsicsMatchTheGenericOnSupportedValues() throws Throwable {
    var lookup = MethodHandles.lookup();
    MethodHandle str = RT.bsmSpecialize(lookup, "$str$",
        MethodType.methodType(String.class, Object.class), StringIntrinsics.class).getTarget();
    MethodHandle repr = RT.bsmSpecialize(lookup, "$repr$",
        MethodType.methodType(String.class, Object.class), StringIntrinsics.class).getTarget();

    var values = List.<Object>of(
        PyNone.INSTANCE,
        true, false,
        0L, Long.MIN_VALUE, Long.MAX_VALUE,
        BigInteger.ONE.shiftLeft(100),
        0.0d, -0.0d, 1.0d, 1.2345678901234567d,
        1e-5, 1e16, Double.MIN_VALUE, Double.MAX_VALUE,
        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
        "", "hello", "don't", "line\nbreak", "café 😀",
        new PyTupleN(new Object[0]),
        new PyTuple1("hello"),
        new PyTuple2(1L, "hello"),
        new PyTuple3(true, 2.5d, PyNone.INSTANCE),
        new PyTuple4(1L, "a", true, 2.5d),
        new PyTuple2(new PyTuple1("nested"), new PyTuple2(2L, "tuple")));

    for (Object value : values) {
      assertEquals(StringIntrinsics.$str$(value), (String) str.invokeExact(value),
          () -> "specialized str for " + value.getClass().getName());
      assertEquals(StringIntrinsics.$repr$(value), (String) repr.invokeExact(value),
          () -> "specialized repr for " + value.getClass().getName());
    }
  }
}
