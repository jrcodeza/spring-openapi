package org.spring.openapi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Response {

	int responseCode();

	String description();

	Class<?> responseBody() default Void.class;

	Header[] headers() default {};

}
