package com.github.forax.ula4j.rt.intrinsic;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a static runtime method that is part of the compiler/runtime ABI
/// If the method name starts and ends with a '$', the method is specializable
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Intrinsic {
}
