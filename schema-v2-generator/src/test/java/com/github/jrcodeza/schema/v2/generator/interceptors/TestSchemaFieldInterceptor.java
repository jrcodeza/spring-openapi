package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.properties.Property;

import java.lang.reflect.Field;

import com.github.jrcodeza.schema.v2.generator.util.TestUtils;

public class TestSchemaFieldInterceptor implements SchemaFieldInterceptor {

	@Override
	public void intercept(Class<?> clazz, Field field, Property transformedFieldSchema) {
		transformedFieldSchema.setDescription(TestUtils.emptyIfNull(transformedFieldSchema.getDescription()) + ". Test schemaField interceptor");
	}

}
