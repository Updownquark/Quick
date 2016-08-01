package org.quick.core.style2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSet;
import org.quick.core.mgr.QuickMessageCenter;
import org.quick.core.style.StyleAttribute;

import com.google.common.reflect.TypeToken;

public class ImmutableStyle implements QuickStyle {
	private final Map<StyleAttribute<?>, StyleValue<?>> theValues;

	private ImmutableStyle(Map<StyleAttribute<?>, StyleValue<?>> values) {
		theValues=Collections.unmodifiableMap(values);
	}

	@Override
	public boolean isSet(StyleAttribute<?> attr) {
		return theValues.containsKey(attr);
	}

	@Override
	public ObservableSet<StyleAttribute<?>> attributes() {
		return ObservableSet.constant(new TypeToken<StyleAttribute<?>>() {}, theValues.keySet());
	}

	@Override
	public <T> ObservableValue<T> get(StyleAttribute<T> attr, boolean withDefault) {
		if (theValues.containsKey(attr))
			return (StyleValue<T>) theValues.get(attr);
		else
			return ObservableValue.constant(attr.getType().getType(), withDefault ? attr.getDefault() : null);
	}

	@Override
	public int hashCode() {
		return theValues.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ImmutableStyle && ((ImmutableStyle) obj).theValues.equals(theValues);
	}

	@Override
	public String toString() {
		return theValues.toString();
	}

	public static Builder build(QuickMessageCenter msg) {
		return new Builder(msg);
	}

	public static class Builder implements StyleSetter {
		private final QuickMessageCenter theMessageCenter;
		private final Map<StyleAttribute<?>, StyleValue<?>> theValues;

		private Builder(QuickMessageCenter msg) {
			theMessageCenter = msg;
			theValues = new LinkedHashMap<>();
		}

		@Override
		public <T> Builder set(StyleAttribute<T> attr, ObservableValue<? extends T> value) {
			if (!attr.getType().getType().isAssignableFrom(value.getType()))
				throw new IllegalArgumentException(
					"Type of value " + value + " (" + value.getType() + ") is not valid for attribute " + attr);
			theValues.put(attr, new StyleValue<>(attr, value, theMessageCenter));
			return this;
		}

		public ImmutableStyle build() {
			return new ImmutableStyle(theValues);
		}
	}
}
