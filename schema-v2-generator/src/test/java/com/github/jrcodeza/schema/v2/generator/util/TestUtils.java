package com.github.jrcodeza.schema.v2.generator.util;

public final class TestUtils {

	private TestUtils() {
		throw new AssertionError();
	}

	public static String emptyIfNull(String string) {
		return string == null ? "" : string;
	}

}
