package org.observe.quick.draw;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExElementType.ValueExpression;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.TraceabilityConfiguration;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickCustomDraw extends QuickSimpleShape.Abstract {
	public static final String CUSTOM_DRAW = "custom-draw";
	public static final ValueExpression<Object, ExElement.Interpreted<?>> DRAWER = ExElementType.valueExpression(null, Object.class, null);
	public static final ExElementType CUSTOM_DRAW_TYPE = ExElementType
		.build(QuickDrawInterpretation.NAME, QuickDrawInterpretation.VERSION, CUSTOM_DRAW)//
		.withValue(DRAWER)//
		.build(null);

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = CUSTOM_DRAW,
		interpretation = Interpreted.class,
		instance = QuickCustomDraw.class)
	public static class Def extends QuickSimpleShape.Def.Abstract<QuickCustomDraw> {
		@TraceabilityConfiguration
		public static void configureTraceability(
			ElementTypeTraceability.SingleTypeTraceabilityBuilder<QuickCustomDraw, Interpreted, Def> traceability) {
			CUSTOM_DRAW_TYPE.configureElementTraceability(traceability).configure(Def::getTypeData, Interpreted::getTypeData,
				QuickCustomDraw::getTypeData);
		}

		private final ExElementType.DefTypeData theTypeData;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			this(parent, type, CUSTOM_DRAW_TYPE);
		}

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type, ExElementType elementType) {
			super(parent, type);
			theTypeData = elementType.createDefData();
		}

		protected ExElementType.DefTypeData getTypeData() {
			return theTypeData;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theTypeData.update(this, session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickSimpleShape.Interpreted.Abstract<QuickCustomDraw> {
		private final ExElementType.InterpretedTypeData theTypeData;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTypeData = getDefinition().getTypeData().interpret();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		protected ExElementType.InterpretedTypeData getTypeData() {
			return theTypeData;
		}

		public InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> getDrawer() {
			return theTypeData.getValue(DRAWER);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theTypeData.update(this);
		}

		@Override
		public QuickCustomDraw create() {
			return new QuickCustomDraw(getIdentity());
		}
	}

	private ExElementType.InstanceTypeData theTypeData;

	QuickCustomDraw(Object id) {
		super(id);
	}

	protected ExElementType.InstanceTypeData getTypeData() {
		return theTypeData;
	}

	public ObservableValue<?> getDrawer() {
		return theTypeData.getValue(DRAWER);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theTypeData = myInterpreted.getTypeData().instantiate(this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theTypeData.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theTypeData.instantiate(myModels, this);

		return myModels;
	}

	@Override
	public QuickCustomDraw copy(ExElement parent) {
		QuickCustomDraw copy = (QuickCustomDraw) super.copy(parent);

		copy.theTypeData = theTypeData.copy(copy);

		return copy;
	}
}
