package com.github.jrcodeza.schema.v2.generator.interceptors;

import io.swagger.models.Model;

import com.github.jrcodeza.schema.v2.generator.util.TestUtils;

public class TestSchemaInterceptor implements SchemaInterceptor {

	@Override
	public void intercept(Class<?> clazz, Model transformedSchema) {
		transformedSchema.setDescription(TestUtils.emptyIfNull(transformedSchema.getDescription()) + ". Test schema interceptors");
	}

}
