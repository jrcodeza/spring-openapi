package com.github.jrcodeza.schema.generator.plugin.example;

import com.github.jrcodeza.schema.generator.interceptors.examples.OpenApiExampleResolver;

public class TestExampleResolver implements OpenApiExampleResolver {

	@Override
	public String resolveExample(String exampleKey) {
		return "Resolved example";
	}

}
