package org.muis.core.style;

import java.util.AbstractSet;
import java.util.Iterator;

import org.muis.core.rx.*;

/**
 * A style that can be sealed to be immutable. All observables returned by this style are just to satisfy the interface--the implementations
 * assume that the style is sealed and therefore that no changes occur.
 */
public class SealableStyle implements MutableStyle, prisms.util.Sealable {
	private java.util.HashMap<StyleAttribute<?>, ObservableValue<?>> theValues;
	private ObservableSet<StyleAttribute<?>> theObservableAttributes;

	/** Always empty, just here so we can return the same value from the depenencies every time */
	private final ObservableList<MuisStyle> theDepends;

	private boolean isSealed;

	/** Creates a sealable style */
	public SealableStyle() {
		theValues = new java.util.HashMap<>();
		theDepends = new DefaultObservableList<>();
		theObservableAttributes = new ConstantObservableSet();
	}

	@Override
	public ObservableList<MuisStyle> getDependencies() {
		return theDepends;
	}

	@Override
	public boolean isSet(StyleAttribute<?> attr) {
		return theValues.containsKey(attr);
	}

	@Override
	public <T> ObservableValue<T> getLocal(StyleAttribute<T> attr) {
		ObservableValue<T> ret = (ObservableValue<T>) theValues.get(attr);
		if(ret == null)
			return ObservableValue.constant(attr.getType().getType(), null);
		return ret;
	}

	@Override
	public <T> SealableStyle set(StyleAttribute<T> attr, T value) {
		if(isSealed)
			throw new SealedException(this);
		if(value == null) {
			clear(attr);
			return this;
		}
		if(attr == null)
			throw new NullPointerException("Cannot set the value of a null attribute");
		T value2 = attr.getType().cast(value);
		if(value2 == null)
			throw new ClassCastException(value.getClass().getName() + " instance " + value + " cannot be set for attribute " + attr
				+ " of type " + attr.getType());
		value = value2;
		if(attr.getValidator() != null)
			try {
				attr.getValidator().assertValid(value);
			} catch(org.muis.core.MuisException e) {
				throw new IllegalArgumentException(e.getMessage());
			}
		theValues.put(attr, ObservableValue.constant(value));
		return this;
	}

	@Override
	public SealableStyle clear(StyleAttribute<?> attr) {
		if(isSealed)
			throw new SealedException(this);
		theValues.remove(attr);
		return this;
	}

	@Override
	public ObservableSet<StyleAttribute<?>> attributes() {
		return theObservableAttributes;
	}

	@Override
	public ObservableSet<StyleAttribute<?>> localAttributes() {
		return theObservableAttributes;
	}

	@Override
	public boolean isSealed() {
		return isSealed;
	}

	@Override
	public void seal() {
		isSealed = true;
	}

	@Override
	public <T> ObservableValue<T> get(StyleAttribute<T> attr, boolean withDefault) {
		ObservableValue<T> ret = (ObservableValue<T>) theValues.get(attr);
		if(withDefault && ret.get() == null)
			return ObservableValue.constant(attr.getDefault());
		return ret;
	}

	@Override
	public SealableStyle clone() {
		SealableStyle ret;
		try {
			ret = (SealableStyle) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
		// At the moment, no need to copy the dependencies because it's always empty, but if sealable styles ever do have dependencies,
		// we'll need to copy them here
		ret.theValues = new java.util.HashMap<>();
		ret.theValues.putAll(theValues);
		ret.theObservableAttributes = ret.new ConstantObservableSet();
		return ret;
	}

	@Override
	public Subscription<StyleAttributeEvent<?>> subscribe(Observer<? super StyleAttributeEvent<?>> observer) {
		// Assume the style is sealed and immutable
		return new Subscription<StyleAttributeEvent<?>>() {
			@Override
			public Subscription<StyleAttributeEvent<?>> subscribe(Observer<? super StyleAttributeEvent<?>> observer2) {
				return this;
			}

			@Override
			public void unsubscribe() {
			}
		};
	}

	class ConstantObservableSet extends AbstractSet<StyleAttribute<?>> implements ObservableSet<StyleAttribute<?>> {
		@Override
		public Iterator<StyleAttribute<?>> iterator() {
			if(isSealed)
				return theValues.keySet().iterator();
			else
				return prisms.util.ArrayUtils.immutableIterator(theValues.keySet().iterator());
		}

		@Override
		public int size() {
			return theValues.size();
		}

		@Override
		public Subscription<Observable<StyleAttribute<?>>> subscribe(Observer<? super Observable<StyleAttribute<?>>> observer) {
			return new Subscription<Observable<StyleAttribute<?>>>() {
				@Override
				public Subscription<Observable<StyleAttribute<?>>> subscribe(Observer<? super Observable<StyleAttribute<?>>> observer2) {
					return this;
				}

				@Override
				public void unsubscribe() {
				}
			};
		}
	}
}
