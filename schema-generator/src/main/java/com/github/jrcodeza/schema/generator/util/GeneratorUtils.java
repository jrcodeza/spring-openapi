package com.github.jrcodeza.schema.generator.util;

import java.lang.reflect.AnnotatedElement;

import com.github.jrcodeza.OpenApiIgnore;

public final class GeneratorUtils {

	private GeneratorUtils() {
		throw new AssertionError();
	}

	public static boolean shouldBeIgnored(AnnotatedElement annotatedElement) {
		return annotatedElement.getAnnotation(OpenApiIgnore.class) != null;
	}

}
