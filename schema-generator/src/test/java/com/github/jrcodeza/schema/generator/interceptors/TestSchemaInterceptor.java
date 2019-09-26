package com.github.jrcodeza.schema.generator.interceptors;

import com.github.jrcodeza.schema.generator.interceptors.SchemaInterceptor;

import io.swagger.v3.oas.models.media.Schema;

import static com.github.jrcodeza.schema.generator.util.TestUtils.emptyIfNull;

public class TestSchemaInterceptor implements SchemaInterceptor {

	@Override
	public void intercept(Class<?> clazz, Schema<?> transformedSchema) {
		transformedSchema.setDescription(emptyIfNull(transformedSchema.getDescription()) + ". Test schema interceptors");
	}

}
