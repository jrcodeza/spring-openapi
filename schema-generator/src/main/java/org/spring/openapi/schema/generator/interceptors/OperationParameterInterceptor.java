package org.spring.openapi.schema.generator.interceptors;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public interface OperationParameterInterceptor {

	void intercept(Method method, Parameter parameter, String parameterName,
				   io.swagger.v3.oas.models.parameters.Parameter transformedParameter);

}
