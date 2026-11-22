package com.auditlog.starter.mask;

import java.lang.reflect.Field;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditObjectInspector {

    private AuditObjectInspector() {
    }

    public static Object sanitize(Object key, Object value) throws IllegalAccessException {

        if (value == null) {
            return null;
        }

        if (isSimpleType(value.getClass())) {
            return AuditMaskEngine.mask(key, value);
        }

        if (value instanceof Collection<?> collection) {

            var list = new java.util.ArrayList<>();

            for (Object item : collection) {
                list.add(sanitize(null, item));
            }

            return list;
        }

        if (value instanceof Map<?, ?> map) {

            Map<Object, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {

                result.put(entry.getKey(), sanitize(entry.getKey(), entry.getValue()));
            }

            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();

        Field[] fields = value.getClass().getDeclaredFields();

        for (Field field : fields) {

            field.setAccessible(true);

            Object fieldValue = field.get(value);

            result.put(field.getName(), sanitize(field.getName(), fieldValue));
        }

        return result;
    }

    private static boolean isSimpleType(Class<?> clazz) {

        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.isAssignableFrom(clazz)
                || Temporal.class.isAssignableFrom(clazz)
                || clazz.isEnum();
    }
}