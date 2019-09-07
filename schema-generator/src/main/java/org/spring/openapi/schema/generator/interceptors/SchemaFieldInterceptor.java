package org.spring.openapi.schema.generator.interceptors;

import java.lang.reflect.Field;

import io.swagger.v3.oas.models.media.Schema;

public interface SchemaFieldInterceptor {

	void intercept(Class<?> clazz, Field field, Schema<?> transformedFieldSchema);

}
