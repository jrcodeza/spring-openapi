package com.github.jrcodeza.schema.generator.filters;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public interface OperationParameterFilter {
    boolean shouldIgnore(Method method, Parameter parameter, String parameterName);
}
