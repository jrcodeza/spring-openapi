package com.github.jrcodeza.schema.v2.generator.model;

import java.util.Map;

import com.github.jrcodeza.OpenApiIgnore;

@OpenApiIgnore
public class InheritanceInfo {

	private String discriminatorFieldName;
	private Map<String, String> discriminatorClassMap;

	public String getDiscriminatorFieldName() {
		return discriminatorFieldName;
	}

	public void setDiscriminatorFieldName(String discriminatorName) {
		this.discriminatorFieldName = discriminatorName;
	}

	public Map<String, String> getDiscriminatorClassMap() {
		return discriminatorClassMap;
	}

	public void setDiscriminatorClassMap(Map<String, String> discriminatorClassMap) {
		this.discriminatorClassMap = discriminatorClassMap;
	}
}
