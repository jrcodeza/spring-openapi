package org.spring.openapi.schema.generator.util;

import java.lang.reflect.AnnotatedElement;

import org.spring.openapi.annotations.OpenApiIgnore;

public final class GeneratorUtils {

	private GeneratorUtils() {
		throw new AssertionError();
	}

	public static boolean shouldBeIgnored(AnnotatedElement annotatedElement) {
		return annotatedElement.getAnnotation(OpenApiIgnore.class) != null;
	}

}
