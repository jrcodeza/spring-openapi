package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.Operation;

import java.lang.reflect.Method;

public class TestOperationInterceptor implements OperationInterceptor {

	@Override
	public void intercept(Method method, Operation transformedOperation) {
		transformedOperation.setSummary("Interceptor summary");
	}

}
