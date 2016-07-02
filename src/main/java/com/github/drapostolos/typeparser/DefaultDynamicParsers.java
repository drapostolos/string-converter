package com.github.drapostolos.typeparser;

import static java.lang.reflect.Modifier.isStatic;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingDeque;

class DefaultDynamicParsers {
    private static final List<DynamicParser> REGULAR_TYPES;
    private static final List<DynamicParser> CONTAINER_TYPES;

    private DefaultDynamicParsers() {
        throw new AssertionError("Not meant for instantiation");
    }
    
    static {
        REGULAR_TYPES = asList(RegularType.values());
        CONTAINER_TYPES = asList(ContainerType.values());
    }

    static List<DynamicParser> asList(DynamicParser[] parsers) {
        /*
         * Not possible to use Arrays.asList(). Can't cast Enum type to
         * DynamicParser interface (for some reason???).
         */
        List<DynamicParser> result = new ArrayList<DynamicParser>();
        for (DynamicParser dynamicParser : parsers) {
            result.add(dynamicParser);
        }
        return result;
    }

    /*
     * Container types are types that holds other types. Example Collections, Maps Arrays etc.
     */
    static List<DynamicParser> forContainerTypes() {
        return CONTAINER_TYPES;
    }

    /*
     * Regular types are non-container types.
     */
    static List<DynamicParser> forRegularTypes() {
        return REGULAR_TYPES;
    }

    static private Collection<Object> populateCollection(
            Collection<Object> collection, Class<?> elementType, String input, ParserHelper helper) {
        for (String value : helper.split(input)) {
            collection.add(helper.parseType(value, elementType));
        }
        return collection;
    }

    static private Class<?> extractElementType(ParserHelper helper) {
        if (helper.isTargetTypeParameterized()) {
            return helper.getParameterizedClassArgumentByIndex(0);
        }
        // Use String as the default element type for raw Collections.
        return String.class;
    }

    /*
     * This enum represents container types (i.e. types containing other types)
     * such as Collections, arrays, Maps etc.
     */
    enum ContainerType implements DynamicParser {
        /**
         * Must be called before COLLECTION enum constant.
         */
        ENUMSET {

            @Override
            public Object parse(String input, ParserHelper helper) {
                if (!helper.getRawTargetClass().equals(EnumSet.class)) {
                    return TRY_NEXT;
                }

                Class<?> elementType = extractElementType(helper);
                @SuppressWarnings("unchecked")
                Collection<Object> collection = EnumSet.noneOf(elementType.asSubclass(Enum.class));
                return populateCollection(collection, elementType, input, helper);
            }

        },
        COLLECTION {

            @Override
            public Object parse(String input, ParserHelper helper) {
                if (!helper.isTargetTypeAssignableTo(Collection.class)) {
                    return TRY_NEXT;
                }

                Collection<Object> collection = instantiateCollection(helper.getRawTargetClass());
                Class<?> elementType = extractElementType(helper);
                return populateCollection(collection, elementType, input, helper);
            }

            private <T> Collection<T> instantiateCollection(Class<? extends T> collectionType) {
                if (collectionType.isInterface()) {
                    return instantiateCollectionFromInterface(collectionType);
                }
                return instantiateCollectionFromClass(collectionType);
            }

            private <T> Collection<T> instantiateCollectionFromClass(Class<? extends T> collectionType) {
                try {
                    @SuppressWarnings("unchecked")
                    Collection<T> temp = (Collection<T>) collectionType.newInstance();
                    return temp;
                } catch (Exception e) {
                    String message = "Cannot instantiate collection of type '%s'";
                    throw new UnsupportedOperationException(String.format(message, collectionType), e);
                }
            }

            private <T> Collection<T> instantiateCollectionFromInterface(Class<? extends T> collectionType) {
                if (List.class.isAssignableFrom(collectionType)) {
                    return new ArrayList<T>();
                } else if (SortedSet.class.isAssignableFrom(collectionType)) {
                    return new TreeSet<T>();
                } else if (Set.class.isAssignableFrom(collectionType)) {
                    return new LinkedHashSet<T>();
                } else if (BlockingDeque.class.isAssignableFrom(collectionType)) {
                    return new LinkedBlockingDeque<T>();
                } else if (Deque.class.isAssignableFrom(collectionType)) {
                    return new ArrayDeque<T>();
                } else if (BlockingQueue.class.isAssignableFrom(collectionType)) {
                    return new LinkedBlockingDeque<T>();
                } else if (Queue.class.isAssignableFrom(collectionType)) {
                    return new LinkedList<T>();
                }
                return new ArrayList<T>();
            }
        },
        MAP {

            private static final int KEY = 0;
            private static final int VALUE = 1;

            @Override
            public Object parse(String input, ParserHelper helper) {
                if (!helper.isTargetTypeAssignableTo(Map.class)) {
                    return TRY_NEXT;
                }
                Class<?> keyType = helper.getParameterizedClassArgumentByIndex(KEY);
                Class<?> valueType = helper.getParameterizedClassArgumentByIndex(VALUE);
                Map<Object, Object> map = instantiateMap(helper.getRawTargetClass());
                for (String entryString : helper.split(input)) {
                    List<String> entry = helper.splitKeyValue(entryString);
                    map.put(helper.parse(entry.get(KEY), keyType), helper.parse(entry.get(VALUE), valueType));
                }
                return map;
            }

            private <K, V> Map<K, V> instantiateMap(Class<?> rawType) {
                if (rawType.isInterface()) {
                    return instantiateMapFromInterface(rawType);
                }
                return instantiateMapFromClass(rawType);
            }

            private <K, V> Map<K, V> instantiateMapFromInterface(Class<?> targetType) {
                if (NavigableMap.class.isAssignableFrom(targetType)) {
                    return new ConcurrentSkipListMap<K, V>();
                }
                if (ConcurrentMap.class.isAssignableFrom(targetType)) {
                    return new ConcurrentHashMap<K, V>();
                }
                if (SortedMap.class.isAssignableFrom(targetType)) {
                    return new TreeMap<K, V>();
                }
                return new LinkedHashMap<K, V>();
            }

            private <K, V> Map<K, V> instantiateMapFromClass(Class<?> rawType) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<K, V> temp = (Map<K, V>) rawType.newInstance();
                    return temp;
                } catch (Exception e) {
                    String message = String.format("Cannot instantiate map of type '%s'", rawType);
                    throw new UnsupportedOperationException(message, e);
                }
            }

        },
        ARRAY {

            @Override
            public Object parse(String input, ParserHelper helper) {
                if (!helper.getRawTargetClass().isArray()) {
                    return TRY_NEXT;
                }
                List<String> strList = helper.split(input);
                Class<?> componentType = helper.getComponentClass();
                Object result = Array.newInstance(componentType, strList.size());
                for (int i = 0; i < strList.size(); i++) {
                    Object element = helper.parse(strList.get(i), componentType);
                    Array.set(result, i, element);
                }
                return result;
            }
        },
    }

    /*
     * This enum represents types that can be contained in a container type.
     */
    private enum RegularType implements DynamicParser {
        ENUM {

            @Override
            public Object parse(String input, ParserHelper helper) {
                if (!helper.getRawTargetClass().isEnum()) {
                    return TRY_NEXT;
                }
                Class<Enum<?>> enumType = helper.getRawTargetClass();
                @SuppressWarnings({ "unchecked" })
                Enum<?> temp = Enum.valueOf(enumType.asSubclass(Enum.class), input.trim());
                return temp;
            }

        },
        CLASS {

            @Override
            public Object parse(String input, ParserHelper helper) {
                Class<?> t = helper.getRawTargetClass();
                if (t != Class.class) {
                    return TRY_NEXT;
                }
                try {
                    return Class.forName(input.trim());
                } catch (ClassNotFoundException e) {
                    throw new UnsupportedOperationException("ClassNotFound: " + e.getMessage(), e);
                }
            }
        },
        CLASS_DECLARING_STATIC_FACTORY_METHOD {
            @Override
            public Object parse(final String input, ParserHelper helper) {
                boolean methodFound = false;
                Method method = null;
                Object argument = null;

                // try find a matching method.
                for (Method m : helper.getRawTargetClass().getDeclaredMethods()) {
                    if (isStaticFactoryMethod(m)) {
                        Type argType = m.getGenericParameterTypes()[0];
                        try {
                            argument = helper.parseType(input, argType);
                            method = m;
                            methodFound = true;
                            break;
                        } catch (NoSuchRegisteredParserException e) {
                            continue;
                        } catch (TypeParserException e) {
                            if (e.getCause() instanceof StackOverflowError) {
                                String message = "StackOverflowError: Cyclic argument type "
                                        + "for method '%s' on this type.";
                                throw new StackOverflowError(String.format(message, m));
                            } else {
                                throw e;
                            }
                        }
                    }
                }
                if (!methodFound) {
                    return TRY_NEXT;
                }

                final Object staticMethod = null;
                try {
                    method.setAccessible(true);
                    return method.invoke(staticMethod, argument);
                } catch (Exception e) {
                    String message = "Failed when calling static factory method %s, "
                            + "with error message: %s";
                    message = String.format(message, method, e.getMessage());
                    throw new UnsupportedOperationException(message, e);
                }
            }

            private boolean isStaticFactoryMethod(Method m) {
                return isStatic(m.getModifiers()) &&
                        m.getParameterTypes().length == 1 &&
                        m.getDeclaringClass().isAssignableFrom(m.getReturnType());
            }
        },
        CLASS_DECLARING_SINGLE_ARGUMENT_CONSTRUCTOR {

            @Override
            public Object parse(final String input, ParserHelper helper) {
                Class<?> targetType = helper.getRawTargetClass();

                boolean constructorFound = false;
                Constructor<?> constructor = null;
                Type argType = null;
                Object argument = null;

                // try find a matching constructor.
                for (Constructor<?> c : targetType.getDeclaredConstructors()) {
                    if (c.getGenericParameterTypes().length == 1) {
                        argType = c.getGenericParameterTypes()[0];
                        try {
                            argument = helper.parseType(input, argType);
                            constructor = c;
                            constructorFound = true;
                            break;
                        } catch (NoSuchRegisteredParserException e) {
                            continue;
                        } catch (TypeParserException e) {
                            if (e.getCause() instanceof StackOverflowError) {
                                String message = "StackOverflowError: Cyclic argument type "
                                        + "for constructor '%s' on this type.";
                                throw new StackOverflowError(String.format(message, c));
                            } else {
                                throw e;
                            }
                        }
                    }
                }
                if (!constructorFound) {
                    return TRY_NEXT;
                }

                constructor.setAccessible(true);
                try {
                    return constructor.newInstance(argument);
                } catch (Exception e) {
                    String message = "Failed when calling constructor %s, with error message: %s";
                    message = String.format(message, constructor, e.getMessage());
                    throw new UnsupportedOperationException(message, e);
                }
            }
        };
    }
}
