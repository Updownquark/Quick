package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExElementType.AbstractElement;
import org.observe.expresso.qonfig.ExTyped;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.TraceabilityConfiguration;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickChart extends QuickRectangle {
	public static final String CHART = "chart";
	public static final ExElementType.ExElementChild<ChartAxis<?>, ChartAxis.Interpreted<?>, ChartAxis.Def> H_AXIS //
	= ExElementType.<ChartAxis<?>, ChartAxis.Interpreted<?>, ChartAxis.Def> child("h-axis", ChartAxis.Def.class,
		ChartAxis.Def::interpret, ChartAxis.Interpreted::updateElement, ChartAxis.Interpreted::create);
	public static final ExElementType.ExElementChild<ChartAxis<?>, ChartAxis.Interpreted<?>, ChartAxis.Def> V_AXIS //
	= ExElementType.<ChartAxis<?>, ChartAxis.Interpreted<?>, ChartAxis.Def> child("v-axis", ChartAxis.Def.class,
		ChartAxis.Def::interpret, ChartAxis.Interpreted::updateElement, ChartAxis.Interpreted::create);
	public static final ExElementType CHART_TYPE = ExElementType.build(QuickDrawInterpretation.NAME, QuickDrawInterpretation.VERSION, CHART)//
		.withChild(H_AXIS)//
		.withChild(V_AXIS)//
		.build(null);

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = CHART,
		interpretation = Interpreted.class,
		instance = QuickChart.class)
	public static class Def<C extends QuickChart> extends QuickRectangle.Def<C> {
		@TraceabilityConfiguration
		public static void configureTraceability(
			ElementTypeTraceability.SingleTypeTraceabilityBuilder<QuickChart, QuickChart.Interpreted<QuickChart>, Def<QuickChart>> traceability) {
			CHART_TYPE.configureElementTraceability(Def::getTypeData, Interpreted::getTypeData, QuickChart::getTypeData, traceability);
		}

		private final ExElementType.DefTypeData theTypeData = new ExElementType.DefTypeData(CHART_TYPE);

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		protected ExElementType.DefTypeData getTypeData() {
			return theTypeData;
		}

		public ChartAxis.Def getHAxis() {
			return theTypeData.getChild(H_AXIS);
		}

		public ChartAxis.Def getVAxis() {
			return theTypeData.getChild(V_AXIS);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theTypeData.update(this, session);
		}

		@Override
		public Interpreted<? extends C> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<C extends QuickChart> extends QuickRectangle.Interpreted<C> {
		private final ExElementType.InterpretedTypeData theTypeData;

		Interpreted(QuickChart.Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTypeData = definition.getTypeData().interpret();
		}

		@Override
		public QuickChart.Def<? super C> getDefinition() {
			return (QuickChart.Def<? super C>) super.getDefinition();
		}

		protected ExElementType.InterpretedTypeData getTypeData() {
			return theTypeData;
		}

		public ChartAxis.Interpreted<?> getHAxis() {
			return theTypeData.getChild(H_AXIS);
		}

		public ChartAxis.Interpreted<?> getVAxis() {
			return theTypeData.getChild(V_AXIS);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theTypeData.update(this);
		}

		@Override
		public C create() {
			return (C) new QuickChart(getIdentity());
		}
	}

	public static class ChartAxis<T> extends ExElementType.AbstractElement {
		public static final String CHART_AXIS = "chart-axis";

		public static final ExElementType.ExElementValue<?, ?, ?, ?, SettableValue<Boolean>> LEADING = ExElementType
			.valueExpression("leading", boolean.class, null);
		public static final ExElementType.ExElementValue<?, //
			? extends InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>>, ?, ?, //
				? extends SettableValue<?>> MIN = ExElementType.valueExpression("min", Object.class, null);
		public static final ExElementType.ExElementValue<?, ?, ?, ?, ? extends SettableValue<?>> MAX = ExElementType.valueExpression("max",
			interpreted -> (ModelInstanceType<SettableValue<?>, SettableValue<Object>>) ((Interpreted<?>) interpreted)
			.getOrInterpretValue(MIN).getType(),
			null);
		public static final ExElementType.ExElementValue<?, ?, ?, ?, SettableValue<Float>> LENGTH = ExElementType.valueExpression("length",
			float.class, null);
		// public static final ExElementType.ExElementChild<ChartTickScheme<?>, ?, ?> SCHEME//
		// = ExElementType.child("scheme", ChartTickScheme.Def.class)//
		// .interpret((d, p) -> d.interpret(p), ChartTickScheme.Interpreted::updateElement)//
		// .instantiate(ChartTickScheme.Interpreted::create);
		public static final ExElementType.ExElementChild<ChartTickScheme<?>, ?, ?> SCHEME = ExElementType.child(//
			"scheme", ChartTickScheme.Def.class, ChartTickScheme.Def::interpret, ChartTickScheme.Interpreted::updateElement, //
			ChartTickScheme.Interpreted::create);
		public static final ExElementType.ExElementChild<QuickDrawText, QuickDrawText.Interpreted, QuickDrawText.Def> LABEL = ExElementType
			.child("label", QuickDrawText.Def.class, QuickDrawText.Def::interpret, QuickDrawText.Interpreted::updateElement,
				QuickDrawText.Interpreted::create);
		public static final ExElementType.ExElementChild<GridLine, GridLine.Interpreted, GridLine.Def> GRID_LINE = ExElementType.child(
			"grid-line", GridLine.Def.class, GridLine.Def::interpret, GridLine.Interpreted::updateElement, GridLine.Interpreted::create);
		public static final ExElementType CHART_AXIS_TYPE = ExElementType
			.build(QuickDrawInterpretation.DRAW, QuickDrawInterpretation.VERSION, CHART_AXIS)//
			.withValue(LEADING)//
			.withValue(MIN)//
			.withValue(MAX)//
			.withValue(LENGTH)//
			.withChild(SCHEME)//
			.withChild(LABEL)//
			.withChild(GRID_LINE)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = CHART_AXIS,
			interpretation = Interpreted.class,
			instance = ChartAxis.class)
		public static class Def extends ExElementType.AbstractElement.Def<ChartAxis<?>> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<ChartAxis<?>, Interpreted<?>, Def> traceability) {
				AbstractElement.configureTraceability(traceability, CHART_AXIS_TYPE);
			}

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType, CHART_AXIS_TYPE);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<T> extends ExElementType.AbstractElement.Interpreted<ChartAxis<T>> {
			Interpreted(ChartAxis.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			void updateElement() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				getDefaultEnv().put(ExTyped.VALUE_TYPE_KEY, getValue(MIN).getType().getType(0));
			}

			@Override
			public ChartAxis<T> create() {
				return new ChartAxis<>(getIdentity());
			}
		}

		ChartAxis(Object id) {
			super(id);
		}

		public SettableValue<Boolean> isLeading() {
			return getValue(LEADING);
		}

		public SettableValue<T> getMin() {
			return (SettableValue<T>) getValue(MIN);
		}

		public SettableValue<T> getMax() {
			return (SettableValue<T>) getValue(MAX);
		}

		public SettableValue<Float> getLength() {
			return getValue(LENGTH);
		}

		public ChartTickScheme<T> getScheme() {
			return (ChartTickScheme<T>) getChild(SCHEME);
		}

		public QuickDrawText getLabel() {
			return getChild(LABEL);
		}

		public GridLine getGridLine() {
			return getChild(GRID_LINE);
		}

		@Override
		public ChartAxis<T> copy(ExElement parent) {
			return (ChartAxis<T>) super.copy(parent);
		}
	}

	public interface ChartTickScheme<T> extends ExElement {
		public interface Def<E extends ChartTickScheme<?>> extends ExElement.Def<E> {
			Interpreted<?, ? extends E> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<T, E extends ChartTickScheme<T>> extends ExElement.Interpreted<E> {
			void updateElement() throws ExpressoInterpretationException;

			ChartTickScheme<T> create();
		}

		Iterable<T> getTicks();
	}

	public static class ExplicitTicks<T> extends ExElementType.AbstractElement implements ChartTickScheme<T> {
		public static final String EXPLICIT_TICKS = "explicit-ticks";
		public static final ExElementType.ExElementValue<?, ?, ?, ?, ? extends ObservableCollection<?>> TICKS = ExElementType
			.collectionExpression("ticks", Object.class);
		public static final ExElementType EXPLICIT_TICKS_TYPE = ExElementType
			.build(QuickDrawInterpretation.DRAW, QuickDrawInterpretation.VERSION, EXPLICIT_TICKS)//
			.withValue(TICKS)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = EXPLICIT_TICKS,
			interpretation = Interpreted.class,
			instance = ExplicitTicks.class)
		public static class Def extends ExElementType.AbstractElement.Def<ExplicitTicks<?>>
		implements ChartTickScheme.Def<ExplicitTicks<?>> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<ExplicitTicks<?>, Interpreted<?>, Def> traceability) {
				AbstractElement.configureTraceability(traceability, EXPLICIT_TICKS_TYPE);
			}

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType, EXPLICIT_TICKS_TYPE);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<T> extends ExElementType.AbstractElement.Interpreted<ExplicitTicks<T>>
		implements ChartTickScheme.Interpreted<T, ExplicitTicks<T>> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public void updateElement() throws ExpressoInterpretationException {
				update();
			}

			@Override
			public ExplicitTicks<T> create() {
				return new ExplicitTicks<>(getIdentity());
			}
		}

		ExplicitTicks(Object id) {
			super(id);
		}

		@Override
		public ObservableCollection<T> getTicks() {
			return (ObservableCollection<T>) getValue(TICKS);
		}
	}

	public static class GridLine extends QuickShape.Abstract implements QuickLinearShape {
		public static final String GRID_LINE = "grid-line";
		public static final ExElementType.ValueExpression<Boolean, ?> ON_TOP = ExElementType.valueExpression("on-top", boolean.class, null);
		public static final ExElementType GRID_LINE_TYPE = ExElementType
			.build(QuickDrawInterpretation.NAME, QuickDrawInterpretation.VERSION, GRID_LINE)//
			.withValue(ON_TOP)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = GRID_LINE,
			interpretation = Interpreted.class,
			instance = GridLine.class)
		public static class Def extends QuickLinearShape.Def.Abstract<GridLine> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<GridLine, Interpreted, Def> traceability) {
				GRID_LINE_TYPE.configureElementTraceability(Def::getTypeData, Interpreted::getTypeData, GridLine::getTypeData,
					traceability);
			}

			private final ExElementType.DefTypeData theTypeData;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theTypeData = new ExElementType.DefTypeData(GRID_LINE_TYPE);
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

		public static class Interpreted extends QuickLinearShape.Interpreted.Abstract<GridLine> {
			private final ExElementType.InterpretedTypeData theTypeData;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theTypeData = definition.getTypeData().interpret();
			}

			protected ExElementType.InterpretedTypeData getTypeData() {
				return theTypeData;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				theTypeData.update(this);
			}

			@Override
			public GridLine create() {
				return new GridLine(getIdentity());
			}
		}

		private ExElementType.InstanceTypeData theTypeData;

		GridLine(Object id) {
			super(id);
		}

		protected ExElementType.InstanceTypeData getTypeData() {
			return theTypeData;
		}

		@Override
		public QuickLinearShape.QuickLineShapeStyle getStyle() {
			return (QuickLinearShape.QuickLineShapeStyle) super.getStyle();
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			theTypeData = ((Interpreted) interpreted).getTypeData().instantiate(this);
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
		public GridLine copy(ExElement parent) {
			GridLine copy = (GridLine) super.copy(parent);

			copy.theTypeData = theTypeData.copy(copy);

			return copy;
		}

		@Override
		public void destroy() {
			super.destroy();

			theTypeData.destroy();
		}
	}

	private ExElementType.InstanceTypeData theTypeData;

	QuickChart(Object id) {
		super(id);
	}

	protected ExElementType.InstanceTypeData getTypeData() {
		return theTypeData;
	}

	public ChartAxis<?> getHAxis() {
		return theTypeData.getChild(H_AXIS);
	}

	public ChartAxis<?> getVAxis() {
		return theTypeData.getChild(V_AXIS);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		theTypeData = ((Interpreted<?>) interpreted).getTypeData().instantiate(this);
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
	public QuickChart copy(ExElement parent) {
		QuickChart copy = (QuickChart) super.copy(parent);

		copy.theTypeData = theTypeData.copy(copy);

		return copy;
	}

	@Override
	public void destroy() {
		super.destroy();

		theTypeData.destroy();
	}
}
