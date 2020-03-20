package com.github.jrcodeza.schema.v2.generator.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.models.parameters.AbstractParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;

public class CustomBodyParameter extends AbstractParameter implements Parameter {

	private CustomSchema schema;
	private Map<String, String> examples;
	private String type;
	private Property items;

	public CustomBodyParameter() {
		super.setIn("body");
	}

	public CustomBodyParameter schema(CustomSchema schema) {
		this.setSchema(schema);
		return this;
	}

	public CustomBodyParameter example(String mediaType, String value) {
		this.addExample(mediaType, value);
		return this;
	}

	public CustomBodyParameter description(String description) {
		this.setDescription(description);
		return this;
	}

	public CustomBodyParameter name(String name) {
		this.setName(name);
		return this;
	}

	public CustomSchema getSchema() {
		return schema;
	}

	public void setSchema(CustomSchema schema) {
		this.schema = schema;
	}

	@JsonProperty("x-examples")
	public Map<String, String> getExamples() {
		return this.examples;
	}

	public void setExamples(Map<String, String> examples) {
		this.examples = examples;
	}

	private void addExample(String mediaType, String value) {
		if (this.examples == null) {
			this.examples = new LinkedHashMap<>();
		}

		this.examples.put(mediaType, value);
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Property getItems() {
		return items;
	}

	public void setItems(Property items) {
		this.items = items;
	}
}
