package org.muis.core.model;

/**
 * Represents a single, simple value that can be changed by the user
 *
 * @param <T> The (compile-time) type of the value
 */
public interface MuisModelValue<T> {
	/** @return The (run-time) type of the value */
	Class<T> getType();

	/** @return The current value */
	T get();

	/** @return Whether this value can be modified */
	boolean isMutable();

	/**
	 * @param value The value to set
	 * @param event The user event that caused the change. May be null.
	 * @throws IllegalStateException If this value is {@link #isMutable() immutable}
	 */
	void set(T value, org.muis.core.event.UserEvent event) throws IllegalStateException;

	/** @param listener The listener to be notified when this value changes. This will be ignored if unsupported. */
	void addListener(MuisModelValueListener<? super T> listener);

	/** @param listener The listener to remove from notification */
	void removeListener(MuisModelValueListener<?> listener);
}