package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ToCoords extends ExElement.Abstract implements TransformOp {
	public static final String TO_COORDS = "to-coords";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = TO_COORDS,
		interpretation = Interpreted.class,
		instance = ToCoords.class)
	public static class Def extends ExElement.Def.Abstract<ToCoords> implements TransformOp.Def<ToCoords> {
		private CompiledExpression isActive;
		private CompiledExpression theSourceWidth;
		private CompiledExpression theSourceHeight;
		private boolean isFlipY;
		private CompiledExpression theTargetMinX;
		private CompiledExpression theTargetMinY;
		private CompiledExpression theTargetWidth;
		private CompiledExpression theTargetHeight;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("active")
		public CompiledExpression isActive() {
			return isActive;
		}

		@QonfigAttributeGetter("source-width")
		public CompiledExpression getSourceWidth() {
			return theSourceWidth;
		}

		@QonfigAttributeGetter("source-height")
		public CompiledExpression getSourceHeight() {
			return theSourceHeight;
		}

		@QonfigAttributeGetter("flip-y")
		public boolean isFlipY() {
			return isFlipY;
		}

		@QonfigAttributeGetter("target-min-x")
		public CompiledExpression getTargetMinX() {
			return theTargetMinX;
		}

		@QonfigAttributeGetter("target-min-y")
		public CompiledExpression getTargetMinY() {
			return theTargetMinY;
		}

		@QonfigAttributeGetter("target-width")
		public CompiledExpression getTargetWidth() {
			return theTargetWidth;
		}

		@QonfigAttributeGetter("target-height")
		public CompiledExpression getTargetHeight() {
			return theTargetHeight;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isActive = getAttributeExpression("active", session);
			theSourceWidth = getAttributeExpression("source-width", session);
			theSourceHeight = getAttributeExpression("source-height", session);
			isFlipY = session.getAttribute("flip-y", boolean.class);
			theTargetMinX = getAttributeExpression("target-min-x", session);
			theTargetMinY = getAttributeExpression("target-min-y", session);
			theTargetWidth = getAttributeExpression("target-width", session);
			theTargetHeight = getAttributeExpression("target-height", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<ToCoords> implements TransformOp.Interpreted<ToCoords> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theSourceWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theSourceHeight;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theTargetMinX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theTargetMinY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theTargetWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theTargetHeight;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive() {
			return isActive;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getSourceWidth() {
			return theSourceWidth;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getSourceHeight() {
			return theSourceHeight;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getTargetMinX() {
			return theTargetMinX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getTargetMinY() {
			return theTargetMinY;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getTargetWidth() {
			return theTargetWidth;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getTargetHeight() {
			return theTargetHeight;
		}

		@Override
		public void updateOperation() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			isActive = interpret(getDefinition().isActive(), ModelTypes.Value.forType(boolean.class));
			theSourceWidth = interpret(getDefinition().getSourceWidth(), ModelTypes.Value.forType(double.class));
			theSourceHeight = interpret(getDefinition().getSourceHeight(), ModelTypes.Value.forType(double.class));
			theTargetMinX = interpret(getDefinition().getTargetMinX(), ModelTypes.Value.forType(double.class));
			theTargetMinY = interpret(getDefinition().getTargetMinY(), ModelTypes.Value.forType(double.class));
			theTargetWidth = interpret(getDefinition().getTargetWidth(), ModelTypes.Value.forType(double.class));
			theTargetHeight = interpret(getDefinition().getTargetHeight(), ModelTypes.Value.forType(double.class));
		}

		@Override
		public ToCoords create() {
			return new ToCoords(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> theActiveInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theSourceWidthInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theSourceHeightInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theTargetMinXInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theTargetMinYInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theTargetWidthInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theTargetHeightInstantiator;

	private SettableValue<SettableValue<Boolean>> isActive;
	private SettableValue<SettableValue<Double>> theSourceWidth;
	private SettableValue<SettableValue<Double>> theSourceHeight;
	private SettableValue<SettableValue<Double>> theTargetMinX;
	private SettableValue<SettableValue<Double>> theTargetMinY;
	private SettableValue<SettableValue<Double>> theTargetWidth;
	private SettableValue<SettableValue<Double>> theTargetHeight;

	private boolean isFlipY;

	ToCoords(Object id) {
		super(id);

		isActive = SettableValue.create();
		theSourceWidth = SettableValue.create();
		theSourceHeight = SettableValue.create();
		theTargetMinX = SettableValue.create();
		theTargetMinY = SettableValue.create();
		theTargetWidth = SettableValue.create();
		theTargetHeight = SettableValue.create();
	}

	public SettableValue<Boolean> isActive() {
		return SettableValue.flatten(isActive);
	}

	public SettableValue<Double> getSourceWidth() {
		return SettableValue.flatten(theSourceWidth);
	}

	public SettableValue<Double> getSourceHeight() {
		return SettableValue.flatten(theSourceHeight);
	}

	public SettableValue<Double> getTargetMinX() {
		return SettableValue.flatten(theTargetMinX);
	}

	public SettableValue<Double> getTargetMinY() {
		return SettableValue.flatten(theTargetMinY);
	}

	public SettableValue<Double> getTargetWidth() {
		return SettableValue.flatten(theTargetWidth);
	}

	public SettableValue<Double> getTargetHeight() {
		return SettableValue.flatten(theTargetHeight);
	}

	public boolean isFlipY() {
		return isFlipY;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theActiveInstantiator = ExElement.instantiate(myInterpreted.isActive());
		theSourceWidthInstantiator = ExElement.instantiate(myInterpreted.getSourceWidth());
		theSourceHeightInstantiator = ExElement.instantiate(myInterpreted.getSourceHeight());
		theTargetMinXInstantiator = ExElement.instantiate(myInterpreted.getTargetMinX());
		theTargetMinYInstantiator = ExElement.instantiate(myInterpreted.getTargetMinY());
		theTargetWidthInstantiator = ExElement.instantiate(myInterpreted.getTargetWidth());
		theTargetHeightInstantiator = ExElement.instantiate(myInterpreted.getTargetHeight());
		isFlipY = myInterpreted.getDefinition().isFlipY();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theActiveInstantiator.instantiate();
		theSourceWidthInstantiator.instantiate();
		theSourceHeightInstantiator.instantiate();
		theTargetMinXInstantiator.instantiate();
		theTargetMinYInstantiator.instantiate();
		theTargetWidthInstantiator.instantiate();
		theTargetHeightInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		isActive.set(ExElement.get(theActiveInstantiator, myModels));
		theSourceWidth.set(ExElement.get(theSourceWidthInstantiator, myModels));
		theSourceHeight.set(ExElement.get(theSourceHeightInstantiator, myModels));
		theTargetMinX.set(ExElement.get(theTargetMinXInstantiator, myModels));
		theTargetMinY.set(ExElement.get(theTargetMinYInstantiator, myModels));
		theTargetWidth.set(ExElement.get(theTargetWidthInstantiator, myModels));
		theTargetHeight.set(ExElement.get(theTargetHeightInstantiator, myModels));

		return myModels;
	}

	@Override
	public ToCoords copy(ExElement parent) {
		ToCoords copy = (ToCoords) super.copy(parent);

		copy.isActive = SettableValue.create();
		copy.theSourceWidth = SettableValue.create();
		copy.theSourceHeight = SettableValue.create();
		copy.theTargetMinX = SettableValue.create();
		copy.theTargetMinY = SettableValue.create();
		copy.theTargetWidth = SettableValue.create();
		copy.theTargetHeight = SettableValue.create();

		return copy;
	}
}
