package com.github.jrcodeza.schema.v2.generator.config;

import com.github.jrcodeza.schema.v2.generator.interceptors.examples.OpenApiExampleResolver;

public class OpenApiGeneratorConfig {

	private boolean generateExamples;

	private OpenApiExampleResolver openApiExampleResolver;

	public boolean isGenerateExamples() {
		return generateExamples;
	}

	public void setGenerateExamples(boolean generateExamples) {
		this.generateExamples = generateExamples;
	}

	public OpenApiExampleResolver getOpenApiExampleResolver() {
		return openApiExampleResolver;
	}

	public void setOpenApiExampleResolver(OpenApiExampleResolver openApiExampleResolver) {
		this.openApiExampleResolver = openApiExampleResolver;
	}
}
