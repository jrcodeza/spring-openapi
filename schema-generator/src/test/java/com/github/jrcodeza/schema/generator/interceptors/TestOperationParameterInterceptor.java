package com.github.jrcodeza.schema.generator.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.github.jrcodeza.schema.generator.interceptors.OperationParameterInterceptor;

import static com.github.jrcodeza.schema.generator.util.TestUtils.emptyIfNull;


public class TestOperationParameterInterceptor implements OperationParameterInterceptor {

	@Override
	public void intercept(Method method, Parameter parameter, String parameterName,
						  io.swagger.v3.oas.models.parameters.Parameter transformedParameter) {
			transformedParameter.setDescription(emptyIfNull(transformedParameter.getDescription()) + ". Interceptor OperationParameter test");
	}

}
