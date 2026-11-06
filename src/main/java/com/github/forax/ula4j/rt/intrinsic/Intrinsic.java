package com.github.forax.ula4j.rt.intrinsic;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Document that a method is specializable at runtime
/// The method must be public static and the name must start and end with a '$'
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Documented
public @interface Intrinsic {
}