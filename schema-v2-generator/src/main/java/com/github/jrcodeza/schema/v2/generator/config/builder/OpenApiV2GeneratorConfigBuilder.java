package com.github.jrcodeza.schema.v2.generator.config.builder;

import org.springframework.core.env.Environment;

import com.github.jrcodeza.schema.v2.generator.config.CompatibilityMode;
import com.github.jrcodeza.schema.v2.generator.config.OpenApiV2GeneratorConfig;

public final class OpenApiV2GeneratorConfigBuilder {

	private OpenApiV2GeneratorConfig openApiV2GeneratorConfig;

	private OpenApiV2GeneratorConfigBuilder() {
		openApiV2GeneratorConfig = new OpenApiV2GeneratorConfig();
	}

	public static OpenApiV2GeneratorConfigBuilder empty() {
		return new OpenApiV2GeneratorConfigBuilder();
	}

	public OpenApiV2GeneratorConfigBuilder withCompatibilityMode(CompatibilityMode compatibilityMode) {
		openApiV2GeneratorConfig.setCompatibilityMode(compatibilityMode);
		return this;
	}

	public OpenApiV2GeneratorConfigBuilder withBasePath(String basePath) {
		openApiV2GeneratorConfig.setBasePath(basePath);
		return this;
	}

	public OpenApiV2GeneratorConfigBuilder withHost(String host) {
		openApiV2GeneratorConfig.setHost(host);
		return this;
	}

	public OpenApiV2GeneratorConfigBuilder withEnvironment(Environment environment) {
		openApiV2GeneratorConfig.setEnvironment(environment);
		return this;
	}

	public OpenApiV2GeneratorConfig build() {
		return openApiV2GeneratorConfig;
	}
}
