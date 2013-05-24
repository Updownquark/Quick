package org.muis.util;

/**
 * A very simple-to-use cache based on {@link prisms.util.DemandCache} that just stores values for compound keys. This class is ideal for
 * smaller data sets
 * 
 * @param <T> The type of value to store in the cache
 */
public class SimpleCache<T> {
	private prisms.util.DemandCache<CompoundKey, T> theCache = new prisms.util.DemandCache<>(null, -1, -1, 2);

	/**
	 * @param key The compound key to get the value for
	 * @return The value stored for the given key, or null if no value has been stored for the key
	 */
	public T get(Object... key) {
		return theCache.get(new CompoundKey(key));
	}

	/**
	 * @param value The value to store
	 * @param key The key to store the value for
	 */
	public void set(T value, Object... key) {
		theCache.put(new CompoundKey(key), value);
	}

	private static class CompoundKey {
		private final Object [] theKey;

		CompoundKey(Object... key) {
			theKey = key;
		}

		@Override
		public int hashCode() {
			return java.util.Arrays.deepHashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof CompoundKey && java.util.Arrays.deepEquals(theKey, ((CompoundKey) obj).theKey);
		}

		@Override
		public String toString() {
			return java.util.Arrays.deepToString(theKey);
		}
	}
}
