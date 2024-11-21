package com.example.demo.ngram;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NGramField {

    int value() default 2; // n-gram 기본값

    boolean failOnMismatch() default true; // n 값이 다를 경우 기본적으로 예외 발생
}
