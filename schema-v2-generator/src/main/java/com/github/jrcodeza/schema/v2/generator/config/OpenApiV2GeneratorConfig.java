package com.github.jrcodeza.schema.v2.generator.config;

public class OpenApiV2GeneratorConfig {

	private CompatibilityMode compatibilityMode;
	private String baseUrl;

	public CompatibilityMode getCompatibilityMode() {
		return compatibilityMode;
	}

	public void setCompatibilityMode(CompatibilityMode compatibilityMode) {
		this.compatibilityMode = compatibilityMode;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}
}
