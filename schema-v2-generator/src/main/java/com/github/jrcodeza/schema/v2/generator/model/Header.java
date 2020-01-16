package com.github.jrcodeza.schema.v2.generator.model;

import com.github.jrcodeza.OpenApiIgnore;

@OpenApiIgnore
public class Header {

	private String name;
	private String description;
	private boolean required = false;

	public Header(String name) {
		this.name = name;
	}

	public Header(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public Header(String name, String description, boolean required) {
		this.name = name;
		this.description = description;
		this.required = required;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}
}
