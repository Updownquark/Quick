package org.observe.quick.draw;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
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

public class QuickPolygon<V> extends QuickShape.Abstract implements QuickBorderedShape {
	public static final String POLYGON = "polygon";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = POLYGON,
		interpretation = Interpreted.class,
		instance = QuickPolygon.class)
	public static class Def extends QuickBorderedShape.Def.Abstract<QuickPolygon<?>> {
		private CompiledExpression theVertices;
		private ModelComponentId theActiveVertexAs;
		private CompiledExpression theVertexX;
		private CompiledExpression theVertexY;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("vertices")
		public CompiledExpression getVertices() {
			return theVertices;
		}

		@QonfigAttributeGetter("active-vertex-as")
		public ModelComponentId getActiveVertexAs() {
			return theActiveVertexAs;
		}

		@QonfigAttributeGetter("vertex-x")
		public CompiledExpression getVertexX() {
			return theVertexX;
		}

		@QonfigAttributeGetter("vertex-y")
		public CompiledExpression getVertexY() {
			return theVertexY;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theVertices = getAttributeExpression("vertices", session);
			String vertAsName = session.getAttributeText("active-vertex-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theActiveVertexAs = elModels.getElementValueModelId(vertAsName);
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theActiveVertexAs, ModelTypes.Value,
				Interpreted::getVertexType);
			theVertexX = getAttributeExpression("vertex-x", session);
			theVertexY = getAttributeExpression("vertex-y", session);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<V> extends QuickBorderedShape.Interpreted.Abstract<QuickPolygon<V>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<V>> theVertices;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theVertexX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theVertexY;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<V>> getVertices() {
			return theVertices;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getVertexX() {
			return theVertexX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getVertexY() {
			return theVertexY;
		}

		public TypeToken<V> getVertexType() throws ExpressoInterpretationException {
			if (theVertices == null)
				theVertices = interpret(getDefinition().getVertices(), ModelTypes.Collection.anyAsV());
			return (TypeToken<V>) theVertices.getType().getType(0);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			theVertices = null;
			super.doUpdate();
			getVertexType();
			theVertexX = interpret(getDefinition().getVertexX(), ModelTypes.Value.FLOAT);
			theVertexY = interpret(getDefinition().getVertexY(), ModelTypes.Value.FLOAT);
		}

		@Override
		public QuickPolygon<V> create() {
			return new QuickPolygon<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<V>> theVerticesInstantiator;
	private ModelComponentId theActiveVertexAs;
	private ModelValueInstantiator<SettableValue<Float>> theVertexXInstantiator;
	private ModelValueInstantiator<SettableValue<Float>> theVertexYInstantiator;

	private SettableValue<ObservableCollection<V>> theVertices;
	private SettableValue<V> theActiveVertex;
	private SettableValue<SettableValue<Float>> theVertexX;
	private SettableValue<SettableValue<Float>> theVertexY;

	QuickPolygon(Object id) {
		super(id);

		theVertices = SettableValue.create();
		theActiveVertex = SettableValue.create();
		theVertexX = SettableValue.create();
		theVertexY = SettableValue.create();
	}

	public ObservableCollection<V> getVertices() {
		return ObservableCollection.flattenValue(theVertices);
	}

	public SettableValue<V> getActiveVertex() {
		return theActiveVertex;
	}

	public ObservableValue<Float> getVertexX() {
		return ObservableValue.flatten(theVertexX);
	}

	public ObservableValue<Float> getVertexY() {
		return ObservableValue.flatten(theVertexY);
	}

	@Override
	public QuickBorderedShape.QuickBorderedShapeStyle getStyle() {
		return (QuickBorderedShape.QuickBorderedShapeStyle) super.getStyle();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<V> myInterpreted = (Interpreted<V>) interpreted;
		theVerticesInstantiator = myInterpreted.getVertices().instantiate();
		theActiveVertexAs = myInterpreted.getDefinition().getActiveVertexAs();
		theVertexXInstantiator = myInterpreted.getVertexX().instantiate();
		theVertexYInstantiator = myInterpreted.getVertexY().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theVerticesInstantiator.instantiate();
		theVertexXInstantiator.instantiate();
		theVertexYInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveVertexAs, myModels, theActiveVertex);

		theVertices.set(theVerticesInstantiator.get(myModels));
		theVertexX.set(theVertexXInstantiator.get(myModels));
		theVertexY.set(theVertexYInstantiator.get(myModels));

		return myModels;
	}

	@Override
	public QuickPolygon<V> copy(ExElement parent) {
		QuickPolygon<V> copy = (QuickPolygon<V>) super.copy(parent);

		copy.theVertices = SettableValue.create();
		copy.theActiveVertex = SettableValue.create();
		copy.theVertexX = SettableValue.create();
		copy.theVertexY = SettableValue.create();

		return copy;
	}
}
