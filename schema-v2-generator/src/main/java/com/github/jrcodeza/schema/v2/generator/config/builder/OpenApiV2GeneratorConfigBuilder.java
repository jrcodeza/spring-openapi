package com.github.jrcodeza.schema.v2.generator.config.builder;

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

	public OpenApiV2GeneratorConfigBuilder withBaseUrl(String baseUrl) {
		openApiV2GeneratorConfig.setBaseUrl(baseUrl);
		return this;
	}

	public OpenApiV2GeneratorConfig build() {
		return openApiV2GeneratorConfig;
	}
}
