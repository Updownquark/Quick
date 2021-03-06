package org.quick.core.tags;

/** Tags a QuickElement as supporting a particular state */
public @interface State {
	/** @return The name of the state */
	String name();

	/** @return The priority for the state */
	int priority();
}
