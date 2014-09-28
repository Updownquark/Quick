package org.muis.core.eval.impl;

import org.muis.core.rx.ObservableValue;
import org.muis.core.rx.ObservableValueEvent;
import org.muis.core.rx.Observer;

import prisms.lang.*;
import prisms.lang.eval.PrismsEvaluator;
import prisms.lang.eval.PrismsItemEvaluator;

/** An evaluator that allows evaluating observable values instead of static ones */
public class ObservableEvaluator extends PrismsEvaluator {
	private prisms.util.SubClassMap<ParsedItem, ObservableItemEvaluator<?>> theObservableEvaluators;

	/** Creates the evaluator */
	public ObservableEvaluator() {
		theObservableEvaluators = new prisms.util.SubClassMap<>();
	}

	/**
	 * @param <T> The subtype of parsed item to add the evaluator for
	 * @param type The type of item to support observable evaluation for
	 * @param evaluator The observable evaluator for the given type
	 * @throws SealedException If this evaluator has been {@link #seal() sealed}
	 */
	public <T extends ParsedItem> void addEvaluator(Class<T> type, ObservableItemEvaluator<? super T> evaluator) throws SealedException {
		if(isSealed())
			throw new SealedException(this);
		theObservableEvaluators.put(type, evaluator);
	}

	/**
	 * @param <T> The subtype of parsed item to get the evaluator for
	 * @param type The type to get observable evaluation support for
	 * @return The observable evaluator for the given type
	 */
	public <T extends ParsedItem> ObservableItemEvaluator<? super T> getObservableEvaluatorFor(Class<T> type) {
		return (ObservableItemEvaluator<? super T>) theObservableEvaluators.get(type);
	}

	/**
	 * @param item The item to evaluate
	 * @param env The evaluation environment in which to perform the evaluation
	 * @return An observable value that observes the evaluated result
	 * @throws EvaluationException If evaluation fails
	 */
	public ObservableValue<?> evaluateObservable(ParsedItem item, EvaluationEnvironment env) throws EvaluationException {
		ObservableItemEvaluator<?> evaluator = getObservableEvaluatorFor(item.getClass());
		if(evaluator != null)
			return ((ObservableItemEvaluator<ParsedItem>) evaluator).evaluateObservable(item, this, env);

		Type type = evaluate(item, env, false, false).getType();
		org.muis.core.rx.DefaultObservableValue<Object> ret = new org.muis.core.rx.DefaultObservableValue<Object>() {
			@Override
			public Type getType() {
				if(type.canAssignTo(ObservableValue.class)) {
					if(type.getParamTypes().length > 0)
						return type.getParamTypes()[0];
					else
						return new Type(Object.class);
				} else
					return type;
			}

			@Override
			public Object get() {
				Object value;
				try {
					value = evaluate(item, env, false, true).getValue();
				} catch(EvaluationException e) {
					throw new IllegalStateException("Value evaluation failed", e);
				}
				if(type.canAssignTo(ObservableValue.class))
					return ((ObservableValue<?>) value).get();
				else
					return value;
			}
		};
		Observer<ObservableValueEvent<Object>> controller = ret.control(null);
		ParsedItem [] deps = item.getDependents();
		ObservableValue<?> [] depObs = new ObservableValue[deps.length];
		for(int i = 0; i < deps.length; i++) {
			depObs[i] = evaluateObservable(deps[i], env);
			ObservableValue<?> depOb = depObs[i];
			ParsedItem dep = deps[i];
			depOb.subscribe(new Observer<ObservableValueEvent<?>>() {
				@Override
				public <V extends ObservableValueEvent<?>> void onNext(V value) {
					CachingSpoofingEvaluator spoof = new CachingSpoofingEvaluator();
					for(int j = 0; j < depObs.length; j++) {
						if(depObs[j] != depOb)
							spoof.inject(deps[j], new EvaluationResult(depObs[j].getType(), depObs[j].get()));
					}
					Object oldValue;
					Object newValue;
					try {
						spoof.inject(dep, new EvaluationResult(type, value.getOldValue()));
						oldValue = evaluate(item, env, false, true).getValue();
						spoof.inject(dep, new EvaluationResult(type, value.getValue()));
						newValue = evaluate(item, env, false, true).getValue();
					} catch(EvaluationException e) {
						throw new IllegalStateException("Value evaluation failed", e);
					}
					ObservableValueEvent<Object> event = new ObservableValueEvent<>(ret, oldValue, newValue, value);
					controller.onNext(event);
				}

				@Override
				public void onCompleted() {
					controller.onCompleted();
				}

				@Override
				public void onError(Throwable e) {
					controller.onError(e);
				}
			});
		}
		return ret;
	}

	private class CachingSpoofingEvaluator extends PrismsEvaluator {
		private java.util.Map<ParsedItem, EvaluationResult> theCachedValues;

		{
			theCachedValues = new java.util.HashMap<>();
		}

		void inject(ParsedItem item, EvaluationResult value) {
			theCachedValues.put(item, value);
		}

		@Override
		public <T extends ParsedItem> void addEvaluator(Class<T> type, PrismsItemEvaluator<? super T> evaluator) {
			throw new UnsupportedOperationException("Evaluator addition not supported");
		}

		@Override
		public <T extends ParsedItem> PrismsItemEvaluator<? super T> getEvaluatorFor(Class<T> type) {
			return ObservableEvaluator.this.getEvaluatorFor(type);
		}

		@Override
		public EvaluationResult evaluate(ParsedItem item, EvaluationEnvironment env, boolean asType, boolean withValues)
			throws EvaluationException {
			return theCachedValues.get(item);
		}
	}
}
