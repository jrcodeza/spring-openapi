package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.Operation;

import java.lang.reflect.Method;

public interface OperationInterceptor {

	void intercept(Method method, Operation transformedOperation);

}
