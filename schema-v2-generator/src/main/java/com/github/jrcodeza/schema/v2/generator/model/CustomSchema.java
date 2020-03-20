package com.github.jrcodeza.schema.v2.generator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CustomSchema {

	@JsonProperty("$ref")
	private String ref;

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}
}
