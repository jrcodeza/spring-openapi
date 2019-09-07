package org.spring.openapi.schema.generator.plugin.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.spring.openapi.schema.generator.interceptors.RequestBodyInterceptor;

import io.swagger.v3.oas.models.parameters.RequestBody;

import static org.spring.openapi.schema.generator.plugin.util.TestUtils.emptyIfNull;

public class TestRequestBodyInterceptor implements RequestBodyInterceptor {

	@Override
	public void intercept(Method method, Parameter parameter, String parameterName, RequestBody transformedRequestBody) {
		transformedRequestBody.setDescription(emptyIfNull(transformedRequestBody.getDescription()) + ". Test requestBody interceptor");
	}

}
