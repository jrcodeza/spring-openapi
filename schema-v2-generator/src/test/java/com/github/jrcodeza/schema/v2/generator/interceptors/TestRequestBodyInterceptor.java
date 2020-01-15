package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.parameters.BodyParameter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.github.jrcodeza.schema.v2.generator.util.TestUtils;

public class TestRequestBodyInterceptor implements RequestBodyInterceptor {

	@Override
	public void intercept(Method method, Parameter parameter, String parameterName, BodyParameter transformedRequestBody) {
		transformedRequestBody.setDescription(TestUtils.emptyIfNull(transformedRequestBody.getDescription()) + ". Test requestBody interceptor");
	}

}
