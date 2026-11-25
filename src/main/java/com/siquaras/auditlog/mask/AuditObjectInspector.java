package com.siquaras.auditlog.mask;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reflectively sanitizes object graphs with cycle, depth, and item-count
 * bounds.
 */
public final class AuditObjectInspector {
	
	private static final int MAX_DEPTH = 32;
	private static final int MAX_ITEMS = 1000;
	private static final String TRUNCATED = "[AUDIT_TRUNCATED]";
	private static final String CIRCULAR = "[CIRCULAR_REFERENCE]";

	private AuditObjectInspector() {
	}

	public static Object sanitize(Object key, Object value) throws IllegalAccessException {
		return sanitize(key, value, new IdentityHashMap<>(), 0);
	}

	private static Object sanitize(Object key, Object value, IdentityHashMap<Object, Boolean> visiting, int depth) throws IllegalAccessException {
		if (value == null) {
			return null;
		}

		if (isSimpleType(value.getClass())) {
			return AuditMaskEngine.mask(key, value);
		}

		if (depth >= MAX_DEPTH) {
			return TRUNCATED;
		}

		if (visiting.put(value, Boolean.TRUE) != null) {
			return CIRCULAR;
		}
		try {
			
			if (value instanceof Collection<?> collection) {
				
				ArrayList<Object> result = new ArrayList<>(Math.min(collection.size(), MAX_ITEMS));
				int count = 0;
				
				for (Object item : collection) {
				
					if (count++ >= MAX_ITEMS) {
						result.add(TRUNCATED);
						break;
					}
					result.add(sanitize(null, item, visiting, depth + 1));
				}
				
				return result;
			}
			
			if (value.getClass().isArray()) {
				
				ArrayList<Object> result = new ArrayList<>(Math.min(Array.getLength(value), MAX_ITEMS));
				int length = Array.getLength(value);
				
				for (int i = 0; i < Math.min(length, MAX_ITEMS); i++) {
					result.add(sanitize(null, Array.get(value, i), visiting, depth + 1));
				}
				
				if (length > MAX_ITEMS) {
					result.add(TRUNCATED);
				}

				return result;
			}
			
			if (value instanceof Map<?, ?> map) {
				Map<Object, Object> result = new LinkedHashMap<>();
				int count = 0;
			
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					
					if (count++ >= MAX_ITEMS) {
						result.put(TRUNCATED, TRUNCATED);
						break;
					}
				
					result.put(entry.getKey(), sanitize(entry.getKey(), entry.getValue(), visiting, depth + 1));
				}
				
				return result;
			}

			Map<String, Object> result = new LinkedHashMap<>();
			Field[] fields = value.getClass().getDeclaredFields();
			
			for (int i = 0; i < Math.min(fields.length, MAX_ITEMS); i++) {
				Field field = fields[i];
				field.setAccessible(true);
				result.put(field.getName(), sanitize(field.getName(), field.get(value), visiting, depth + 1));
			}
			
			if (fields.length > MAX_ITEMS) {
				result.put(TRUNCATED, TRUNCATED);
			}

			return result;
		
		} finally {
			
			visiting.remove(value);
		}
	}

	private static boolean isSimpleType(Class<?> clazz) {
		return clazz.equals(String.class) || Number.class.isAssignableFrom(clazz) || clazz.equals(Boolean.class)
				|| clazz.equals(Character.class) || Temporal.class.isAssignableFrom(clazz) || clazz.isEnum();
	}
}
