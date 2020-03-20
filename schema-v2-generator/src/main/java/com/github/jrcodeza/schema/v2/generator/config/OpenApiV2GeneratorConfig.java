package com.github.jrcodeza.schema.v2.generator.config;

public class OpenApiV2GeneratorConfig {

	private CompatibilityMode compatibilityMode;

	private String basePath;

	public CompatibilityMode getCompatibilityMode() {
		return compatibilityMode;
	}

	public void setCompatibilityMode(CompatibilityMode compatibilityMode) {
		this.compatibilityMode = compatibilityMode;
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
}
