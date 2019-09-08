package com.github.jrcodeza.schema.generator.interceptors;

import java.lang.reflect.Method;

import io.swagger.v3.oas.models.Operation;

public interface OperationInterceptor {

	void intercept(Method method, Operation transformedOperation);

}
