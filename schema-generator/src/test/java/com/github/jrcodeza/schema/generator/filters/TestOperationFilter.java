package com.github.jrcodeza.schema.generator.filters;

import java.lang.reflect.Method;

public class TestOperationFilter implements OperationFilter {
    @Override
    public boolean shouldIgnore(Method method) {
        return method.getName().equals("uploadCarDocuments");
    }
}
