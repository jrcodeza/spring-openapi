package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.parameters.BodyParameter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public interface RequestBodyInterceptor {

	void intercept(Method method, Parameter parameter, String parameterName, BodyParameter transformedRequestBody);

}
