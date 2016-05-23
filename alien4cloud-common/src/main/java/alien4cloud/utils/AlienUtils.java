package alien4cloud.utils;

import com.google.common.collect.Maps;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public final class AlienUtils {

    public static final String DEFAULT_PREFIX_SEPARATOR = "_";
    public static final String COLON_SEPARATOR = ":";

    private AlienUtils() {

    }

    /**
     * Shortcurt to create an array of elements.
     *
     * @param elements The elements for which to create an array.
     * @param <T> The type of the elements (and array)
     * @return An array of the given elements.
     */
    public static <T> T[] array(T... elements) {
        return elements;
    }

    /**
     * Shortcurt to create an array of array of elements.
     *
     * @param elements The elements for which to create an array.
     * @param <T> The type of the elements (and array)
     * @return An array of the given elements.
     */
    public static <T> T[][] arOfArray(T[]... elements) {
        return elements;
    }

    /**
     * Fill an array with data from an untyped collection. Both must have the same size.
     *
     * @param array The array in which to insert elements.
     * @param collection The untyped collection that contains elements to put in the array.
     * @param <T> Type of elements in the array (should be the same in collection)
     * @return The filled array.
     */
    public static <T> T[] fill(T[] array, Collection collection) {
        if (array.length != collection.size()) {
            throw new IllegalArgumentException("Size of array and collection must be the same.");
        }
        int i = 0;
        for (Object element : collection) {
            array[i] = (T) element;
            i++;
        }
        return array;
    }

    public static String putValueCommaSeparatedInPosition(String values, String valueToPut, int position) {
        String[] valuesArray;
        String separator = ",";
        int positionInArray = position - 1;
        if (StringUtils.isBlank(values)) {
            valuesArray = new String[0];
        } else {
            valuesArray = values.split(separator);
        }
        StringBuilder toReturnBuilder = new StringBuilder("");
        if (valuesArray.length >= position) {
            valuesArray[positionInArray] = valueToPut;
            String separatorToAppend;
            for (int i = 0; i < valuesArray.length; i++) {
                separatorToAppend = i + 1 == valuesArray.length ? "" : ",";
                toReturnBuilder.append(valuesArray[i]).append(separatorToAppend);
            }
        } else {
            int nbSeparatorToAdd = 0;
            if (StringUtils.isBlank(values)) {
                toReturnBuilder = new StringBuilder("");
                nbSeparatorToAdd = position - 1;
            } else {
                toReturnBuilder = new StringBuilder(values);
                nbSeparatorToAdd = position - valuesArray.length;
            }
            for (int i = 0; i < nbSeparatorToAdd; i++) {
                toReturnBuilder.append(separator);
            }
            toReturnBuilder.append(valueToPut);
        }
        return toReturnBuilder.toString();
    }

    /**
     * prefix a string with another
     *
     * @param separator
     * @param toPrefix
     * @param prefixes
     * @return
     */
    public static String prefixWith(String separator, String toPrefix, String... prefixes) {
        if (toPrefix == null) {
            return null;
        }
        if (ArrayUtils.isEmpty(prefixes)) {
            return toPrefix;
        }
        String finalSeparator = separator == null ? DEFAULT_PREFIX_SEPARATOR : separator;
        StringBuilder builder = new StringBuilder();
        for (String prefix : prefixes) {
            builder.append(prefix).append(finalSeparator);
        }
        return builder.append(toPrefix).toString();
    }

    /**
     * prefix a string with another using the default separator "_"
     *
     * @param toPrefix
     * @param prefixes
     * @return
     */
    public static String prefixWithDefaultSeparator(String toPrefix, String... prefixes) {
        return prefixWith(DEFAULT_PREFIX_SEPARATOR, toPrefix, prefixes);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows({ IllegalAccessException.class, InvocationTargetException.class })
    public static <K, V> Map<K, V> fromListToMap(List<V> list, String keyProperty, boolean useGetter) {
        if (list == null || StringUtils.isBlank(keyProperty)) {
            return null;
        }

        Map<K, V> map = Maps.newHashMap();

        if (useGetter) {
            for (V item : list) {
                Method[] methods = item.getClass().getMethods();
                for (Method method : methods) {
                    String getterName = "get" + keyProperty.toLowerCase();
                    if (method.getName().toLowerCase().equals(getterName)) {
                        K key = (K) method.invoke(item);
                        if (key != null) {
                            map.put(key, item);
                        }
                        break;
                    }
                }
            }

        } else {
            for (V item : list) {
                Field[] fields = item.getClass().getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.getName().equals(keyProperty)) {
                        K key = (K) field.get(item);
                        if (key != null) {
                            map.put(key, item);
                        }
                        break;
                    }
                }
            }
        }

        return map;
    }
}
