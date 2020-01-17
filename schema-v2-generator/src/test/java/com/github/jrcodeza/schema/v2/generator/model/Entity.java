package com.github.jrcodeza.schema.v2.generator.model;

import javax.validation.constraints.NotNull;

public class Entity {

	@NotNull
	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
