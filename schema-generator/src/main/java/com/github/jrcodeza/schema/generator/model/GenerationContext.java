package com.github.jrcodeza.schema.generator.model;

import java.util.List;
import java.util.Map;

public class GenerationContext {

	private final List<String> modelPackages;
	private final Map<String, InheritanceInfo> inheritanceMap;

	public GenerationContext(Map<String, InheritanceInfo> inheritanceMap, List<String> modelPackages) {
		this.modelPackages = modelPackages;
		this.inheritanceMap = inheritanceMap;
	}

	public List<String> getModelPackages() {
		return modelPackages;
	}

	public Map<String, InheritanceInfo> getInheritanceMap() {
		return inheritanceMap;
	}
}
