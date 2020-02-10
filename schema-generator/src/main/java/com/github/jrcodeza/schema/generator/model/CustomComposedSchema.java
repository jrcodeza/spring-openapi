package com.github.jrcodeza.schema.generator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.models.media.ComposedSchema;

public class CustomComposedSchema extends ComposedSchema {

	public static final String X_DISCRIMINATOR_VALUE = "x-discriminator-value";

	@JsonProperty(X_DISCRIMINATOR_VALUE)
	private String discriminatorValue;

	public String getDiscriminatorValue() {
		return discriminatorValue;
	}

	public void setDiscriminatorValue(String discriminatorValue) {
		this.discriminatorValue = discriminatorValue;
	}
}
