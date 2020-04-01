package com.github.jrcodeza.schema.v2.generator.config;

import org.springframework.core.env.Environment;

public class OpenApiV2GeneratorConfig {

	private CompatibilityMode compatibilityMode;
	private String basePath;
	private String host;
	private Environment environment;

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

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}
}
