package com.github.jrcodeza.schema.generator.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.github.jrcodeza.schema.generator.interceptors.RequestBodyInterceptor;

import io.swagger.v3.oas.models.parameters.RequestBody;

import static com.github.jrcodeza.schema.generator.util.TestUtils.emptyIfNull;

public class TestRequestBodyInterceptor implements RequestBodyInterceptor {

	@Override
	public void intercept(Method method, Parameter parameter, String parameterName, RequestBody transformedRequestBody) {
		transformedRequestBody.setDescription(emptyIfNull(transformedRequestBody.getDescription()) + ". Test requestBody interceptor");
	}

}
