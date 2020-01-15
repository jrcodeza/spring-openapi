package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.properties.Property;

import java.lang.reflect.Field;

public interface SchemaFieldInterceptor {

	void intercept(Class<?> clazz, Field field, Property transformedFieldSchema);

}
