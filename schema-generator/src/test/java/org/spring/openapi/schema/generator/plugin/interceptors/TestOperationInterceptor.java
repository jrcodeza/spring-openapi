package org.spring.openapi.schema.generator.plugin.interceptors;

import java.lang.reflect.Method;

import org.spring.openapi.schema.generator.interceptors.OperationInterceptor;

import io.swagger.v3.oas.models.Operation;

public class TestOperationInterceptor implements OperationInterceptor {

	@Override
	public void intercept(Method method, Operation transformedOperation) {
		transformedOperation.setSummary("Interceptor summary");
	}

}
