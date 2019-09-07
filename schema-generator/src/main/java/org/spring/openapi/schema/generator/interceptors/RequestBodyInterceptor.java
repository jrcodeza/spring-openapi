package org.spring.openapi.schema.generator.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.swagger.v3.oas.models.parameters.RequestBody;

public interface RequestBodyInterceptor {

	void intercept(Method method, Parameter parameter, String parameterName, RequestBody transformedRequestBody);

}
