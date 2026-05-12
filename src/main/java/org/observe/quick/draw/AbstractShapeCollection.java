package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public abstract class AbstractShapeCollection<T> extends ExElement.Abstract implements QuickShapePublisher {
	public static final String SHAPE_COLLECTION = "shape-collection";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE_COLLECTION,
		interpretation = Interpreted.class,
		instance = AbstractShapeCollection.class)
	public static abstract class Def<C extends AbstractShapeCollection<?>> extends ExElement.Def.Abstract<C>
		implements QuickShapePublisher.Def<C> {
		private CompiledExpression theValues;
		private ModelComponentId theValueName;
		private ModelComponentId theValueIndex;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("for-each")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter("active-shape-as")
		public ModelComponentId getValueName() {
			return theValueName;
		}

		public ModelComponentId getValueIndex() {
			return theValueIndex;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValues = getAttributeExpression("for-each", session);
			String valueNameAttr = session.getAttributeText("active-shape-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theValueName = elModels.getElementValueModelId(valueNameAttr);
			theValueIndex = elModels.getElementValueModelId("shapeIndex");
			elModels.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theValueName, ModelTypes.Value,
				Interpreted::getShapeType);
		}

		@Override
		public abstract Interpreted<?, ? extends C> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<T, C extends AbstractShapeCollection<T>> extends ExElement.Interpreted.Abstract<C>
		implements QuickShapePublisher.Interpreted<C> {
		protected Interpreted(Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super C> getDefinition() {
			return (Def<? super C>) super.getDefinition();
		}

		public abstract TypeToken<T> getShapeType() throws ExpressoInterpretationException;

		public abstract InterpretedValueSynth<?, ?> getValues();

		@Override
		public void updateElement() throws ExpressoInterpretationException {
			update();
		}

		@Override
		public abstract C create();
	}

	private ModelComponentId theValueName;
	private ModelComponentId theValueIndex;

	private SettableValue<T> theActiveValue;
	private SettableValue<Integer> theActiveValueIndex;

	protected AbstractShapeCollection(Object id) {
		super(id);
		theActiveValue = SettableValue.create();
		theActiveValueIndex = SettableValue.create(0);
	}

	public abstract Object getValues();

	public SettableValue<T> getActiveValue() {
		return theActiveValue;
	}

	public SettableValue<Integer> getActiveValueIndex() {
		return theActiveValueIndex;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;

		theValueName = myInterpreted.getDefinition().getValueName();
		theValueIndex = myInterpreted.getDefinition().getValueIndex();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theValueName, myModels, theActiveValue);
		ExFlexibleElementModelAddOn.satisfyElementValue(theValueIndex, myModels, theActiveValueIndex);

		return myModels;
	}

	@Override
	public AbstractShapeCollection<T> copy(ExElement parent) {
		AbstractShapeCollection<T> copy = (AbstractShapeCollection<T>) super.copy(parent);

		copy.theActiveValue = SettableValue.create();
		copy.theActiveValueIndex = SettableValue.create();

		return copy;
	}
}
