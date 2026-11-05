package com.github.forax.ula4j.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PyNoneTest {
  @Test
  public void toStringIsThePythonSpelling() {
    assertEquals("None", PyNone.INSTANCE.toString());
    assertEquals("None", String.valueOf(PyNone.INSTANCE));
  }

  @Test
  public void isNotEqualToItsStringForm() {
    assertNotEquals("None", PyNone.INSTANCE);
    assertNotEquals(PyNone.INSTANCE, "None");
  }

  @Test
  public void isNeverJavaNull() {
    assertFalse(PyNone.INSTANCE == null);
    assertFalse(PyNone.INSTANCE.equals(null));
  }

  @Test
  public void equalsAndHashCodeAreConsistent() {
    assertEquals(PyNone.INSTANCE, PyNone.INSTANCE);
    assertEquals(PyNone.INSTANCE.hashCode(), PyNone.INSTANCE.hashCode());
  }
}