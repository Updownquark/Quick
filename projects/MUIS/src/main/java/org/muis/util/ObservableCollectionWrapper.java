package org.muis.util;

import java.util.Collection;
import java.util.Iterator;

import org.muis.rx.Observer;
import org.muis.rx.collect.ObservableCollection;
import org.muis.rx.collect.ObservableElement;

import prisms.lang.Type;

/**
 * Wraps an observable set
 *
 * @param <T> The type of the set
 */
public class ObservableCollectionWrapper<T> implements ObservableCollection<T> {
	private final ObservableCollection<T> theWrapped;

	/** @param wrap The set to wrap */
	public ObservableCollectionWrapper(ObservableCollection<T> wrap) {
		theWrapped = wrap;
	}

	@Override
	public Type getType() {
		return theWrapped.getType();
	}

	@Override
	public Runnable internalSubscribe(Observer<? super ObservableElement<T>> observer) {
		return theWrapped.internalSubscribe(observer);
	}

	@Override
	public int size() {
		return theWrapped.size();
	}

	@Override
	public boolean isEmpty() {
		return theWrapped.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return theWrapped.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		return theWrapped.iterator();
	}

	@Override
	public Object [] toArray() {
		return theWrapped.toArray();
	}

	@Override
	public <T2> T2 [] toArray(T2 [] a) {
		return theWrapped.toArray(a);
	}

	@Override
	public boolean add(T e) {
		return theWrapped.add(e);
	}

	@Override
	public boolean remove(Object o) {
		return theWrapped.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theWrapped.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		return theWrapped.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return theWrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return theWrapped.retainAll(c);
	}

	@Override
	public void clear() {
		theWrapped.clear();
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
