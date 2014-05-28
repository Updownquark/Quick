package org.muis.core.rx;

/**
 * A subscription to an observable. This extends observable to facilitate chaining of subscriptions.
 * 
 * @param <T> The type of observable that this subscription is for
 */
public interface Subscription<T> extends Observable<T> {
	/** Unsubscribes this subscription from the observable */
	void unsubscribe();
}
