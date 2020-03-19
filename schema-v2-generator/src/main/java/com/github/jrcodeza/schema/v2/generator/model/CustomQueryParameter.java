package com.github.jrcodeza.schema.v2.generator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.models.parameters.QueryParameter;

public class CustomQueryParameter extends QueryParameter {

	@JsonProperty("$ref")
	private String ref;

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}
}
