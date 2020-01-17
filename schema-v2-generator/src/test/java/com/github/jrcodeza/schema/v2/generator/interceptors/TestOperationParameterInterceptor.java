package com.github.jrcodeza.schema.v2.generator.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.github.jrcodeza.schema.v2.generator.util.TestUtils;

public class TestOperationParameterInterceptor implements OperationParameterInterceptor {

	@Override
	public void intercept(Method method, Parameter parameter, String parameterName,
						  io.swagger.models.parameters.Parameter transformedParameter) {
		transformedParameter.setDescription(TestUtils.emptyIfNull(transformedParameter.getDescription()) + ". Interceptor OperationParameter test");
	}

}
