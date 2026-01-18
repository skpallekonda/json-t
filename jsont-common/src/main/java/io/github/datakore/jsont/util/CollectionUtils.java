package io.github.datakore.jsont.util;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Stream;

public class CollectionUtils {

    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    public static <T> List<T> asList(T... elements) {
        return Arrays.asList(elements);
    }

    public static <T> Set<T> asSet(T... elements) {
        return Stream.of(elements).collect(java.util.stream.Collectors.toSet());
    }

    public static <T> Set<T> toSet(Object arrayObject, Class<T> componentType) {
        if (arrayObject == null) {
            return Collections.emptySet();
        }
        Set<T> list = new HashSet<>();
        for (Object o : toObjectList(arrayObject)) {
            list.add(componentType.cast(o));
        }
        return list;
    }

    public static <T> List<T> toList(Object arrayObject, Class<T> componentType) {
        if (arrayObject == null) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>();
        for (Object o : toObjectList(arrayObject)) {
            list.add(componentType.cast(o));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Object arrayObject, Class<T> componentType) {
        if (arrayObject == null) {
            return null;
        }
        List<T> list = toList(arrayObject, componentType);
        // Create a new array of the specific component type
        T[] array = (T[]) Array.newInstance(componentType, list.size());
        return list.toArray(array);
    }

    public static List<?> toObjectList(Object arrayObject) {
        if (arrayObject == null) {
            return null;
        }
        if (arrayObject instanceof List) {
            return (List<?>) arrayObject;
        }
        if (arrayObject instanceof Collection) {
            return new ArrayList<>((Collection<?>) arrayObject);
        }
        if (arrayObject.getClass().isArray()) {
            if (arrayObject instanceof Object[]) {
                return Arrays.asList((Object[]) arrayObject);
            }
            // Handle primitive arrays manually to avoid reflection
            if (arrayObject instanceof int[]) {
                int[] arr = (int[]) arrayObject;
                List<Integer> list = new ArrayList<>(arr.length);
                for (int i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof long[]) {
                long[] arr = (long[]) arrayObject;
                List<Long> list = new ArrayList<>(arr.length);
                for (long i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof double[]) {
                double[] arr = (double[]) arrayObject;
                List<Double> list = new ArrayList<>(arr.length);
                for (double i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof float[]) {
                float[] arr = (float[]) arrayObject;
                List<Float> list = new ArrayList<>(arr.length);
                for (float i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof boolean[]) {
                boolean[] arr = (boolean[]) arrayObject;
                List<Boolean> list = new ArrayList<>(arr.length);
                for (boolean i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof byte[]) {
                byte[] arr = (byte[]) arrayObject;
                List<Byte> list = new ArrayList<>(arr.length);
                for (byte i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof char[]) {
                char[] arr = (char[]) arrayObject;
                List<Character> list = new ArrayList<>(arr.length);
                for (char i : arr) list.add(i);
                return list;
            }
            if (arrayObject instanceof short[]) {
                short[] arr = (short[]) arrayObject;
                List<Short> list = new ArrayList<>(arr.length);
                for (short i : arr) list.add(i);
                return list;
            }
        }
        throw new IllegalArgumentException("Expected an array or collection for an array type field, but got: " + arrayObject.getClass().getName());
    }
}
