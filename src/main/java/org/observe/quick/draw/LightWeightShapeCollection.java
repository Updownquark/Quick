package org.observe.quick.draw;

import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.config.QonfigElementOrAddOn;

import com.google.common.reflect.TypeToken;

public class LightWeightShapeCollection<T> extends AbstractShapeCollection<T> {
	public static final String LIGHT_WEIGHT_SHAPE_COLLECTION = "light-weight-shape-collection";

	public static class Def extends AbstractShapeCollection.Def<LightWeightShapeCollection<?>> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends AbstractShapeCollection.Interpreted<T, LightWeightShapeCollection<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<List<T>>> theValues;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<T> getShapeType() throws ExpressoInterpretationException {
			if (theValues == null)
				theValues = interpret(getDefinition().getValues(),
					ModelTypes.Value.forType(TypeTokens.get().keyFor(List.class).wildCard()));
			return (TypeToken<T>) theValues.getType().getType(0).resolveType(List.class.getTypeParameters()[0]);
		}

		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<List<T>>> getValues() {
			return theValues;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			theValues = null;
			super.doUpdate();
			if (theValues == null)
				theValues = interpret(getDefinition().getValues(),
					ModelTypes.Value.forType(TypeTokens.get().keyFor(List.class).wildCard()));
		}

		@Override
		public LightWeightShapeCollection<T> create() {
			return new LightWeightShapeCollection<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<List<T>>> theValuesInstantiator;

	private SettableValue<List<T>> theValues;

	LightWeightShapeCollection(Object id) {
		super(id);
	}

	@Override
	public List<T> getValues() {
		List<T> wrapped = theValues.get();
		return wrapped == null ? Collections.emptyList() : Collections.unmodifiableList(wrapped);
	}

	public Observable<? extends Causable> getValueChanges() {
		return theValues.noInitChanges();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;

		theValuesInstantiator = myInterpreted.getValues().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theValuesInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theValues = theValuesInstantiator.get(myModels);

		return myModels;
	}

	@Override
	public LightWeightShapeCollection<T> copy(ExElement parent) {
		LightWeightShapeCollection<T> copy = (LightWeightShapeCollection<T>) super.copy(parent);

		return copy;
	}
}
