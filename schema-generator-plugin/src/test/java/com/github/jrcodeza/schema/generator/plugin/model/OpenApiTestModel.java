package com.github.jrcodeza.schema.generator.plugin.model;

import com.github.jrcodeza.OpenApiExample;

public class OpenApiTestModel {

	@OpenApiExample(name = "StandardExample", value = "standardExampleValue")
	private String attribute;

	@OpenApiExample(name = "KeyExample", key = "KEY")
	private String keyExample;

	public String getAttribute() {
		return attribute;
	}

	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}

	public String getKeyExample() {
		return keyExample;
	}

	public void setKeyExample(String keyExample) {
		this.keyExample = keyExample;
	}
}
