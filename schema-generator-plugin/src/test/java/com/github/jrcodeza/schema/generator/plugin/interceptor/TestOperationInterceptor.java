package com.github.jrcodeza.schema.generator.plugin.interceptor;

import java.lang.reflect.Method;

import com.github.jrcodeza.schema.generator.interceptors.OperationInterceptor;

import io.swagger.v3.oas.models.Operation;

public class TestOperationInterceptor implements OperationInterceptor {

	@Override
	public void intercept(Method method, Operation transformedOperation) {
		transformedOperation.setSummary("Interceptor summary");
	}

}
