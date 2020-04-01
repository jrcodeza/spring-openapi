package com.github.jrcodeza.schema.generator.config.builder;

import org.springframework.core.env.Environment;

import com.github.jrcodeza.schema.generator.config.OpenApiGeneratorConfig;
import com.github.jrcodeza.schema.generator.interceptors.examples.OpenApiExampleResolver;

public final class OpenApiGeneratorConfigBuilder {

	private OpenApiGeneratorConfig openApiGeneratorConfig;

	private OpenApiGeneratorConfigBuilder() {
		openApiGeneratorConfig = new OpenApiGeneratorConfig();
	}

	public static OpenApiGeneratorConfigBuilder defaultConfig() {
		return new OpenApiGeneratorConfigBuilder();
	}

	public OpenApiGeneratorConfigBuilder withGenerateExamples(boolean generateExamples) {
		openApiGeneratorConfig.setGenerateExamples(generateExamples);
		return this;
	}

	public OpenApiGeneratorConfigBuilder withOpenApiExampleResolver(OpenApiExampleResolver openApiExampleResolver) {
		openApiGeneratorConfig.setOpenApiExampleResolver(openApiExampleResolver);
		return this;
	}

	public OpenApiGeneratorConfigBuilder withEnvironment(Environment environment) {
		openApiGeneratorConfig.setEnvironment(environment);
		return this;
	}

	public OpenApiGeneratorConfig build() {
		return openApiGeneratorConfig;
	}
}
