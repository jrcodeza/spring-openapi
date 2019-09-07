package org.spring.openapi.schema.generator.plugin.util;

public final class TestUtils {

	private TestUtils() {
		throw new AssertionError();
	}

	public static String emptyIfNull(String string) {
		return string == null ? "" : string;
	}

}
