package com.github.jrcodeza.schema.v2.generator.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public interface OperationParameterInterceptor {

	void intercept(Method method, Parameter parameter, String parameterName,
				   io.swagger.models.parameters.Parameter transformedParameter);

}
