package org.observe.quick.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExElementType.AbstractElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.TraceabilityConfiguration;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

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
			CHART_TYPE.configureElementTraceability(traceability).configure(Def::getTypeData, Interpreted::getTypeData,
				QuickChart::getTypeData);
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

	public static class ChartAxis<T extends Number> extends QuickShape.Abstract implements QuickLinearShape {
		public static final String CHART_AXIS = "chart-axis";

		public static final ExElementType.ValueExpression<Boolean, ?> LEADING = ExElementType
			.valueExpression("leading", boolean.class, null);
		public static final ExElementType.ValueExpression<? extends Number, ?> MIN = ExElementType.valueExpression("min", Number.class,
			null);
		public static final ExElementType.ValueExpression<? extends Number, ?> MAX = ExElementType
			.valueExpression("max", interpreted -> (TypeToken<Number>) ((Interpreted<?>) interpreted).getTypeData()
				.getOrInterpretValue(MIN, interpreted).getType().getType(0), null);
		public static final ExElementType.ExElementChild<ChartTickScheme, ?, ?> SCHEME = ExElementType.child(//
			"scheme", ChartTickScheme.Def.class, ChartTickScheme.Def::interpret, ChartTickScheme.Interpreted::updateElement, //
			ChartTickScheme.Interpreted::create);
		public static final ExElementType.ExElementChild<QuickDrawText, QuickDrawText.Interpreted, QuickDrawText.Def> LABEL = ExElementType
			.child("label", QuickDrawText.Def.class, QuickDrawText.Def::interpret, QuickDrawText.Interpreted::updateElement,
				QuickDrawText.Interpreted::create);
		public static final ExElementType.ExElementChild<TickLine, TickLine.Interpreted, TickLine.Def> TICK_LINE = ExElementType.child(
			"tick-line", TickLine.Def.class, TickLine.Def::interpret, TickLine.Interpreted::updateElement, TickLine.Interpreted::create);
		public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Double>> TICK_VALUE_AS = ExElementType//
			.modelAttributeValue("tick-value-as", double.class);
		public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> TICK_INDEX_AS = ExElementType//
			.modelAttributeValue("tick-index-as", int.class);
		public static final ExElementType CHART_AXIS_TYPE = ExElementType
			.build(QuickDrawInterpretation.DRAW, QuickDrawInterpretation.VERSION, CHART_AXIS)//
			.withValue(LEADING)//
			.withValue(MIN)//
			.withValue(MAX)//
			.withChild(SCHEME)//
			.withChild(LABEL)//
			.withChild(TICK_LINE)//
			.withModelValue(TICK_VALUE_AS)//
			.withModelValue(TICK_INDEX_AS)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = CHART_AXIS,
			interpretation = Interpreted.class,
			instance = ChartAxis.class)
		public static class Def extends QuickShape.Def.Abstract<ChartAxis<?>> implements QuickLinearShape.Def<ChartAxis<?>> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<ChartAxis<?>, Interpreted<?>, Def> traceability) {
				CHART_AXIS_TYPE.configureElementTraceability(traceability).configure(Def::getTypeData, Interpreted::getTypeData,
					ChartAxis::getTypeData);
			}

			private final ExElementType.DefTypeData theTypeData;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theTypeData = new ExElementType.DefTypeData(CHART_AXIS_TYPE);
			}

			public ExElementType.DefTypeData getTypeData() {
				return theTypeData;
			}

			@Override
			public QuickLineShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickLineShapeStyle.Def.Default(parentStyle, this, style);
			}

			@Override
			public QuickLineShapeStyle.Def getStyle() {
				return (QuickLineShapeStyle.Def) super.getStyle();
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theTypeData.update(this, session);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<T extends Number> extends QuickShape.Interpreted.Abstract<ChartAxis<T>>
		implements QuickLinearShape.Interpreted<ChartAxis<T>> {
			private final ExElementType.InterpretedTypeData theTypeData;

			Interpreted(ChartAxis.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theTypeData = definition.getTypeData().interpret();
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickLineShapeStyle.Interpreted getStyle() {
				return (QuickLineShapeStyle.Interpreted) super.getStyle();
			}

			public ExElementType.InterpretedTypeData getTypeData() {
				return theTypeData;
			}

			public TypeToken<T> getTickValueType() throws ExpressoInterpretationException {
				return (TypeToken<T>) theTypeData.getOrInterpretValue(MIN, this).getType().getType(0);
			}

			public QuickDrawText.Interpreted getLabel() {
				return theTypeData.getChild(LABEL);
			}

			public TickLine.Interpreted getTickLine() {
				return theTypeData.getChild(TICK_LINE);
			}

			@Override
			public void updateElement() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				theTypeData.update(this);
			}

			@Override
			public ChartAxis<T> create() {
				return new ChartAxis<>(getIdentity());
			}
		}

		private ExElementType.InstanceTypeData theTypeData;

		ChartAxis(Object id) {
			super(id);
		}

		@Override
		public QuickLineShapeStyle getStyle() {
			return (QuickLineShapeStyle) super.getStyle();
		}

		public ExElementType.InstanceTypeData getTypeData() {
			return theTypeData;
		}

		public SettableValue<Double> getTickValue() {
			return theTypeData.satisfyModelValue(TICK_VALUE_AS, () -> SettableValue.create(0.0));
		}

		public SettableValue<Integer> getTickIndex() {
			return theTypeData.satisfyModelValue(TICK_INDEX_AS, () -> SettableValue.create(0));
		}

		public SettableValue<Boolean> isLeading() {
			return theTypeData.getValue(LEADING);
		}

		public SettableValue<T> getMin() {
			return (SettableValue<T>) theTypeData.getValue(MIN);
		}

		public SettableValue<T> getMax() {
			return (SettableValue<T>) theTypeData.getValue(MAX);
		}

		public ChartTickScheme getScheme() {
			return theTypeData.getChild(SCHEME);
		}

		public QuickDrawText getLabel() {
			return theTypeData.getChild(LABEL);
		}

		public TickLine getTickLine() {
			return theTypeData.getChild(TICK_LINE);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			theTypeData = ((Interpreted<T>) interpreted).getTypeData().instantiate(this);
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
		public ChartAxis<T> copy(ExElement parent) {
			ChartAxis<T> copy = (ChartAxis<T>) super.copy(parent);

			copy.theTypeData = theTypeData.copy(copy);

			return copy;
		}

		@Override
		public void destroy() {
			super.destroy();

			theTypeData.destroy();
		}
	}

	public interface ChartTickScheme extends ExElement {
		public interface Def<E extends ChartTickScheme> extends ExElement.Def<E> {
			Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<E extends ChartTickScheme> extends ExElement.Interpreted<E> {
			void updateElement() throws ExpressoInterpretationException;

			ChartTickScheme create();
		}

		Iterable<Double> getTicks(double min, double max);
	}

	public static class ExplicitTicks extends ExElementType.AbstractElement implements ChartTickScheme {
		public static final String EXPLICIT_TICKS = "explicit-ticks";
		public static final ExElementType.ExElementValue<?, ?, ?, ?, ? extends ObservableCollection<Double>> TICKS = ExElementType
			.collectionExpression("ticks", double.class);
		public static final ExElementType EXPLICIT_TICKS_TYPE = ExElementType
			.build(QuickDrawInterpretation.DRAW, QuickDrawInterpretation.VERSION, EXPLICIT_TICKS)//
			.withValue(TICKS)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = EXPLICIT_TICKS,
			interpretation = Interpreted.class,
			instance = ExplicitTicks.class)
		public static class Def extends ExElementType.AbstractElement.Def<ExplicitTicks> implements ChartTickScheme.Def<ExplicitTicks> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<ExplicitTicks, Interpreted, Def> traceability) {
				AbstractElement.configureTraceability(traceability, EXPLICIT_TICKS_TYPE);
			}

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType, EXPLICIT_TICKS_TYPE);
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends ExElementType.AbstractElement.Interpreted<ExplicitTicks>
		implements ChartTickScheme.Interpreted<ExplicitTicks> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public void updateElement() throws ExpressoInterpretationException {
				update();
			}

			@Override
			public ExplicitTicks create() {
				return new ExplicitTicks(getIdentity());
			}
		}

		ExplicitTicks(Object id) {
			super(id);
		}

		@Override
		public ObservableCollection<Double> getTicks(double min, double max) {
			return getValue(TICKS);
		}
	}

	public static class DefaultTicks extends ExElement.Abstract implements ChartTickScheme {
		public static final String DEFAULT_TICKS = "default-ticks";

		public static class Def extends ExElement.Def.Abstract<DefaultTicks> implements ChartTickScheme.Def<DefaultTicks> {
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends ExElement.Interpreted.Abstract<DefaultTicks>
		implements ChartTickScheme.Interpreted<DefaultTicks> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public void updateElement() throws ExpressoInterpretationException {
				update();
			}

			@Override
			public ChartTickScheme create() {
				return new DefaultTicks(getIdentity());
			}
		}

		DefaultTicks(Object id) {
			super(id);
		}

		@Override
		public Iterable<Double> getTicks(double min, double max) {
			if (min == max)
				return Collections.emptyList();
			boolean reversed = min > max;
			if (reversed) {
				double temp = min;
				min = max;
				max = temp;
			}
			double range = max - min;
			double interval = Math.pow(10, Math.round(Math.log10(range) - 1));
			if (interval * 4 < range)
				interval *= 2.5;
			double tick = interval * (int) (min / interval);
			if (tick < min)
				tick += interval;
			List<Double> ticks = new ArrayList<>();
			while (tick < max) {
				ticks.add(tick);
				tick += interval;
			}
			if (reversed)
				Collections.reverse(ticks);
			return ticks;
		}
	}

	public static class TickLine extends QuickShape.Abstract implements QuickLinearShape {
		public static final String TICK_LINE = "tick-line";
		public static final ExElementType.ExElementValue<?, ?, ?, ?, SettableValue<Integer>> LENGTH = ExElementType
			.valueExpression("length", int.class, null);
		public static final ExElementType TICK_LINE_TYPE = ExElementType
			.build(QuickDrawInterpretation.NAME, QuickDrawInterpretation.VERSION, TICK_LINE)//
			.withValue(LENGTH)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = TICK_LINE,
			interpretation = Interpreted.class,
			instance = GridLines.class)
		public static class Def extends QuickLinearShape.Def.Abstract<TickLine> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<TickLine, Interpreted, Def> traceability) {
				TICK_LINE_TYPE.configureElementTraceability(traceability).configure(Def::getTypeData, Interpreted::getTypeData,
					TickLine::getTypeData);
			}

			private final ExElementType.DefTypeData theTypeData;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theTypeData = new ExElementType.DefTypeData(TICK_LINE_TYPE);
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

		public static class Interpreted extends QuickLinearShape.Interpreted.Abstract<TickLine> {
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
			public TickLine create() {
				return new TickLine(getIdentity());
			}
		}

		private ExElementType.InstanceTypeData theTypeData;

		TickLine(Object id) {
			super(id);
		}

		protected ExElementType.InstanceTypeData getTypeData() {
			return theTypeData;
		}

		@Override
		public QuickLinearShape.QuickLineShapeStyle getStyle() {
			return (QuickLinearShape.QuickLineShapeStyle) super.getStyle();
		}

		public SettableValue<Integer> getLength() {
			return theTypeData.getValue(LENGTH);
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
		public GridLines copy(ExElement parent) {
			GridLines copy = (GridLines) super.copy(parent);

			copy.theTypeData = theTypeData.copy(copy);

			return copy;
		}

		@Override
		public void destroy() {
			super.destroy();

			theTypeData.destroy();
		}
	}

	public static class GridLines extends QuickShape.Abstract implements QuickLinearShape {
		public static final String GRID_LINES = "grid-lines";
		public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Double>> TICK_VALUE_AS = ExElementType
			.modelAttributeValue("tick-value-as", double.class);
		public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> TICK_INDEX_AS = ExElementType
			.modelAttributeValue("tick-index-as", int.class);
		public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Boolean>> VERTICAL_LINE_AS = ExElementType
			.modelAttributeValue("vertical-line-as", boolean.class);
		public static final ExElementType GRID_LINES_TYPE = ExElementType
			.build(QuickDrawInterpretation.NAME, QuickDrawInterpretation.VERSION, GRID_LINES)//
			.withModelValue(TICK_VALUE_AS)//
			.withModelValue(TICK_INDEX_AS)//
			.withModelValue(VERTICAL_LINE_AS)//
			.build(null);

		@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
			qonfigType = GRID_LINES,
			interpretation = Interpreted.class,
			instance = GridLines.class)
		public static class Def extends QuickLinearShape.Def.Abstract<GridLines> {
			@TraceabilityConfiguration
			public static void configureTraceability(
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<GridLines, Interpreted, Def> traceability) {
				GRID_LINES_TYPE.configureElementTraceability(traceability).configure(Def::getTypeData, Interpreted::getTypeData,
					GridLines::getTypeData);
			}

			private final ExElementType.DefTypeData theTypeData;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theTypeData = new ExElementType.DefTypeData(GRID_LINES_TYPE);
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

		public static class Interpreted extends QuickLinearShape.Interpreted.Abstract<GridLines> {
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
			public GridLines create() {
				return new GridLines(getIdentity());
			}
		}

		private ExElementType.InstanceTypeData theTypeData;

		GridLines(Object id) {
			super(id);
		}

		protected ExElementType.InstanceTypeData getTypeData() {
			return theTypeData;
		}

		@Override
		public QuickLinearShape.QuickLineShapeStyle getStyle() {
			return (QuickLinearShape.QuickLineShapeStyle) super.getStyle();
		}

		public SettableValue<Double> getTickValueAs() {
			return getTypeData().satisfyModelValue(TICK_VALUE_AS, () -> SettableValue.create(0.0));
		}

		public SettableValue<Integer> getTickIndexAs() {
			return getTypeData().satisfyModelValue(TICK_INDEX_AS, () -> SettableValue.create(0));
		}

		public SettableValue<Boolean> getVerticalLine() {
			return getTypeData().satisfyModelValue(VERTICAL_LINE_AS, () -> SettableValue.create(false));
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
		public GridLines copy(ExElement parent) {
			GridLines copy = (GridLines) super.copy(parent);

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
