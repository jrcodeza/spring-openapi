package com.github.jrcodeza.schema.generator.interceptors;

import java.lang.reflect.Field;

import com.github.jrcodeza.schema.generator.interceptors.SchemaFieldInterceptor;

import io.swagger.v3.oas.models.media.Schema;

import static com.github.jrcodeza.schema.generator.util.TestUtils.emptyIfNull;

public class TestSchemaFieldInterceptor implements SchemaFieldInterceptor {

	@Override
	public void intercept(Class<?> clazz, Field field, Schema<?> transformedFieldSchema) {
		transformedFieldSchema.setDescription(emptyIfNull(transformedFieldSchema.getDescription()) + ". Test schemaField interceptor");
	}

}
