package com.github.jrcodeza.schema.generator.config;

import org.springframework.core.env.Environment;

import com.github.jrcodeza.schema.generator.interceptors.examples.OpenApiExampleResolver;

public class OpenApiGeneratorConfig {

	private boolean generateExamples;

	private OpenApiExampleResolver openApiExampleResolver;

	private Environment environment;

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

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}
}
