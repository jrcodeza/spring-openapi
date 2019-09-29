package org.spring.openapi.client.generator;

import io.swagger.v3.oas.models.Operation;

public class OperationData {

	private final Operation operation;
	private final String url;

	public OperationData(Operation operation, String url) {
		this.operation = operation;
		this.url = url;
	}

	public Operation getOperation() {
		return operation;
	}

	public String getUrl() {
		return url;
	}
}
