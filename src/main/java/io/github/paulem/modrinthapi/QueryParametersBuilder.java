package io.github.paulem.modrinthapi;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryParametersBuilder {
    public static Map<String, Object> parameters = new HashMap<>();

    public<T> QueryParametersBuilder withParameter(Map.Entry<String, T> entry) {
        return withParameter(entry.getKey(), entry.getValue(), true);
    }

    public<T> QueryParametersBuilder withParameter(Map.Entry<String, T> entry, boolean ignoreNull) {
        return withParameter(entry.getKey(), entry.getValue(), ignoreNull);
    }

    public<T> QueryParametersBuilder withParameter(String parameterName, @Nullable T parameter) {
        return withParameter(parameterName, parameter, true);
    }

    public<T> QueryParametersBuilder withParameter(String parameterName, @Nullable T parameter, boolean ignoreNull) {
        return withParameter(parameterName, parameter, ignoreNull, null, null);
    }

    public<T> QueryParametersBuilder withParameter(String parameterName, @Nullable T parameter, @Nullable String replaceThis, @Nullable String byThis) {
        return withParameter(parameterName, parameter, true, replaceThis, byThis);
    }

    public<T> QueryParametersBuilder withParameter(String parameterName, @Nullable T parameter, boolean ignoreNull, @Nullable String replaceThis, @Nullable String byThis) {
        if(parameter == null && ignoreNull) return this;

        if(replaceThis != null && byThis != null && parameter instanceof String parameterReplace) {
            parameters.put(parameterName, parameterReplace.replace(replaceThis, byThis));
            return this;
        }

        parameters.put(parameterName, parameter);
        return this;
    }

    public QueryParametersBuilder withParameters(Map<String, Object> parameters) {
        return withParameters(parameters, true);
    }

    public QueryParametersBuilder withParameters(Map<String, Object> parameters, boolean ignoreNull) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            withParameter(entry, ignoreNull);
        }

        return this;
    }

    public String build() {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String parameterName = entry.getKey();
            Object parameter = entry.getValue();

            result.append(parameterName).append("=").append(parameter);

            List<String> keys = parameters.keySet().stream().toList();

            if(keys.lastIndexOf(parameterName) < keys.size()-1) {
                result.append("&");
            }
        }

        return result.toString();
    }
}
