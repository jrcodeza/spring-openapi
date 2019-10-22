package com.github.jrcodeza.schema.generator.plugin.interceptor;

import java.lang.reflect.Field;

import com.github.jrcodeza.schema.generator.interceptors.SchemaFieldInterceptor;

import io.swagger.v3.oas.models.media.Schema;

public class TestSchemaFieldInterceptor implements SchemaFieldInterceptor {

	@Override
	public void intercept(Class<?> clazz, Field field, Schema<?> transformedFieldSchema) {
		transformedFieldSchema.setDescription("Schema field description");
	}

}
