package org.quick.core.prop;

import java.util.*;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.qommons.ex.ExFunction;
import org.quick.core.QuickException;

import com.google.common.reflect.TypeToken;

public class DefaultExpressionContext implements ExpressionContext {
	private final List<ExpressionContext> theParents;
	private final Map<String, ObservableValue<?>> theValues;
	private final List<Function<String, ObservableValue<?>>> theValueGetters;
	private final Map<String, List<ExpressionFunction<?>>> theFunctions;
	private final Map<String, Unit<?, ?>> theUnits;

	private DefaultExpressionContext(List<ExpressionContext> parents, Map<String, ObservableValue<?>> values,
		List<Function<String, ObservableValue<?>>> valueGetters, Map<String, List<ExpressionFunction<?>>> functions,
		Map<String, Unit<?, ?>> units) {
		theParents = Collections.unmodifiableList(new ArrayList<>(parents));
		theValues = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		theValueGetters = Collections.unmodifiableList(valueGetters);
		Map<String, List<ExpressionFunction<?>>> fns = new LinkedHashMap<>();
		for (Map.Entry<String, List<ExpressionFunction<?>>> fn : functions.entrySet()) {
			fns.put(fn.getKey(), Collections.unmodifiableList(new ArrayList<>(fn.getValue())));
		}
		theFunctions = Collections.unmodifiableMap(fns);
		theUnits = Collections.unmodifiableMap(units);
	}

	@Override
	public ObservableValue<?> getVariable(String name) {
		ObservableValue<?> value = theValues.get(name);
		if (value != null)
			return value;
		for (Function<String, ObservableValue<?>> getter : theValueGetters) {
			value = getter.apply(name);
			if (value != null)
				return value;
		}
		for (ExpressionContext parent : theParents) {
			value = parent.getVariable(name);
			if (value != null)
				return value;
		}
		return null;
	}

	@Override
	public void getFunctions(String name, List<ExpressionFunction<?>> functions) {
		List<ExpressionFunction<?>> fns = theFunctions.get(name);
		if (fns != null) {
			for (ExpressionFunction<?> fn : fns)
				functions.add(fn);
		}
		for (ExpressionContext ctx : theParents)
			ctx.getFunctions(name, functions);
	}

	@Override
	public Unit<?, ?> getUnit(String name) {
		return theUnits.get(name);
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder {
		private final List<ExpressionContext> theParents;
		private final Map<String, ObservableValue<?>> theValues;
		private final List<Function<String, ObservableValue<?>>> theValueGetters;
		private final Map<String, List<ExpressionFunction<?>>> theFunctions;
		private final Map<String, Unit<?, ?>> theUnits;

		private Builder() {
			theParents = new ArrayList<>();
			theValues = new LinkedHashMap<>();
			theValueGetters = new ArrayList<>();
			theFunctions = new LinkedHashMap<>();
			theUnits = new LinkedHashMap<>();
		}

		public Builder withParent(ExpressionContext ctx) {
			theParents.add(ctx);
			return this;
		}

		public Builder withValue(String name, ObservableValue<?> value) {
			theValues.put(name, value);
			return this;
		}

		public Builder withValueGetter(Function<String, ObservableValue<?>> getter) {
			theValueGetters.add(getter);
			return this;
		}

		public Builder withFunction(String name, ExpressionFunction<?> fn) {
			List<ExpressionFunction<?>> fns = theFunctions.get(name);
			if (fns == null) {
				fns = new ArrayList<>(1);
				theFunctions.put(name, fns);
			}
			fns.add(fn);
			return this;
		}

		public <F, T2> Builder withUnit(String name, TypeToken<F> from, TypeToken<T2> to,
			ExFunction<? super F, ? extends T2, QuickException> operator) {
			theUnits.put(name, new Unit<>(name, from, to, operator));
			return this;
		}

		public Builder copy(){
			Builder newBuilder=new Builder();
			newBuilder.theParents.addAll(theParents);
			newBuilder.theValues.putAll(theValues);
			newBuilder.theValueGetters.addAll(theValueGetters);
			newBuilder.theFunctions.putAll(theFunctions);
			return newBuilder;
		}

		public DefaultExpressionContext build() {
			return new DefaultExpressionContext(theParents, theValues, theValueGetters, theFunctions, theUnits);
		}
	}
}
