package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.Model;

public interface SchemaInterceptor {

	void intercept(Class<?> clazz, Model transformedSchema);

}
