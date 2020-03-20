package com.github.jrcodeza.schema.generator.filters;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class TestOperationParameterFilter implements OperationParameterFilter {
    @Override
    public boolean shouldIgnore(Method method, Parameter parameter, String parameterName) {
        return parameterName.equals("requestParamC");
    }
}
