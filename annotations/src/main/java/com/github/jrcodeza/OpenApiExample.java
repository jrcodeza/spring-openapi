package com.github.jrcodeza;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenApiExample {

	String name() default "";
	String description() default "";
	/**
	 * If not filled in then key has to be filled in. CustomExampleResolver will be invoked with key
	 * value as parameter.
	 * @return
	 */
	String value() default "";
	String key() default "";

}
