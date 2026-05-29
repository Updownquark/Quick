package org.observe.quick.draw;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.TraceabilityConfiguration;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.fn.FunctionUtils;

public class QuickGradientPlot extends QuickRectangle{
	public interface Aggregation {
		public static final Aggregation MIN = Accumulator.Min::new;
		public static final Aggregation MAX = Accumulator.Max::new;
		public static final Aggregation MEAN = Accumulator.Mean::new;

		Accumulator accumulate();
	}

	public interface Accumulator {
		Accumulator acceptValue(float value);

		float get();

		static class Max implements Accumulator {
			private float theMax = Float.NaN;

			@Override
			public Accumulator acceptValue(float value) {
				if (Float.isNaN(theMax) || value > theMax)
					theMax = value;
				return this;
			}

			@Override
			public float get() {
				float v = theMax;
				theMax = Float.NaN;
				return v;
			}
		}

		static class Min implements Accumulator {
			private float theMin = Float.NaN;

			@Override
			public Accumulator acceptValue(float value) {
				if (Float.isNaN(theMin) || value < theMin)
					theMin = value;
				return this;
			}

			@Override
			public float get() {
				float v = theMin;
				theMin = Float.NaN;
				return v;
			}
		}

		static class Mean implements Accumulator {
			private float theSum;
			private int theCount;

			@Override
			public Accumulator acceptValue(float value) {
				if (!Float.isNaN(value)) {
					theSum += value;
					theCount++;
				}
				return this;
			}

			@Override
			public float get() {
				float v = theCount == 0 ? Float.NaN : theSum / theCount;
				theSum = 0;
				theCount = 0;
				return v;
			}
		}
	}

	public interface Renderer extends Transaction {
		float getValue(int minX, int maxX, int minY, int maxY);

		Color getColor(float value);
	}

	public static final Comparator<Float> FLOAT_SORT = FunctionUtils.printableComparator(Float::compareTo, () -> "compareNumbers", null);

	public static final String GRADIENT_PLOT = "gradient-plot";
	public static final ExElementType.ValueExpression<List<Number>, ?> XS = ExElementType.valueExpression("xs",
		TypeTokens.get().keyFor(List.class).<List<Number>> parameterized(Number.class), null);
	public static final ExElementType.ValueExpression<List<Number>, ?> YS = ExElementType.valueExpression("ys",
		TypeTokens.get().keyFor(List.class).<List<Number>> parameterized(Number.class), null);
	public static final ExElementType.ValueExpression<Aggregation, ?> AGGREGATION = ExElementType.valueExpression("aggregation",
		Aggregation.class, null);
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> MIN_X_AS = ExElementType
		.modelAttributeValue("min-x-as", int.class);
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> MAX_X_AS = ExElementType
		.modelAttributeValue("max-x-as", int.class);
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> MIN_Y_AS = ExElementType
		.modelAttributeValue("min-y-as", int.class);
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> MAX_Y_AS = ExElementType
		.modelAttributeValue("max-y-as", int.class);
	public static final ExElementType.ValueExpression<Float, ?> VALUE = ExElementType.valueExpression("value", float.class, null);
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Float>> VALUE_AS = ExElementType
		.modelAttributeValue("value-as", float.class);
	public static final ExElementType.ValueExpression<Color, ?> COLOR = ExElementType.valueExpression("color", Color.class, null);
	public static final ExElementType GRADIENT_PLOT_TYPE = ExElementType
		.build(QuickDrawInterpretation.NAME, QuickDrawInterpretation.VERSION, WITH_STROKE)//
		.withValue(XS)//
		.withValue(YS)//
		.withValue(AGGREGATION)//
		.withModelValue(MIN_X_AS)//
		.withModelValue(MAX_X_AS)//
		.withModelValue(MIN_Y_AS)//
		.withModelValue(MAX_Y_AS)//
		.withValue(VALUE)//
		.withModelValue(VALUE_AS)//
		.withValue(COLOR)//
		.build(null);

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = GRADIENT_PLOT,
		interpretation = Interpreted.class,
		instance = QuickGradientPlot.class)
	public static class Def extends QuickRectangle.Def<QuickGradientPlot> {
		@TraceabilityConfiguration
		public static final void configureTraceability(
			ElementTypeTraceability.SingleTypeTraceabilityBuilder<QuickGradientPlot, Interpreted, Def> traceability) {
			GRADIENT_PLOT_TYPE.configureElementTraceability(traceability)//
				.configure(Def::getTypeData, Interpreted::getTypeData, QuickGradientPlot::getTypeData);
		}

		private final ExElementType.DefTypeData theTypeData;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			this(parent, type, GRADIENT_PLOT_TYPE);
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

	public static class Interpreted extends QuickRectangle.Interpreted<QuickGradientPlot> {
		private final ExElementType.InterpretedTypeData theTypeData;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTypeData = definition.getTypeData().interpret();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
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
		public QuickGradientPlot create() {
			return new QuickGradientPlot(getIdentity());
		}
	}

	private ExElementType.InstanceTypeData theTypeData;

	QuickGradientPlot(Object id) {
		super(id);
	}

	protected ExElementType.InstanceTypeData getTypeData() {
		return theTypeData;
	}

	public List<Number> getXs() {
		return theTypeData.getValue(XS).get();
	}

	public List<Number> getYs() {
		return theTypeData.getValue(YS).get();
	}

	public Aggregation getAggregation() {
		return theTypeData.getValue(AGGREGATION).get();
	}

	public Renderer getRenderer() {
		Causable.CausableInUse cause = Causable.cause();
		SettableValue.Setter<Integer> minXAs = theTypeData.satisfyModelValue(MIN_X_AS, () -> SettableValue.create(0)).lockWrite(false,
			cause);
		SettableValue.Setter<Integer> maxXAs = theTypeData.satisfyModelValue(MAX_X_AS, () -> SettableValue.create(0)).lockWrite(false,
			cause);
		SettableValue.Setter<Integer> minYAs = theTypeData.satisfyModelValue(MIN_Y_AS, () -> SettableValue.create(0)).lockWrite(false,
			cause);
		SettableValue.Setter<Integer> maxYAs = theTypeData.satisfyModelValue(MAX_Y_AS, () -> SettableValue.create(0)).lockWrite(false,
			cause);
		ObservableValue.Getter<Float> value = theTypeData.getValue(VALUE).lock(false);
		SettableValue.Setter<Float> valueAs = theTypeData.satisfyModelValue(VALUE_AS, () -> SettableValue.create(0f)).lockWrite(false,
			cause);
		ObservableValue.Getter<Color> color = theTypeData.getValue(COLOR).lock(false);
		return new Renderer() {
			@Override
			public float getValue(int minX, int maxX, int minY, int maxY) {
				minXAs.set(minX);
				maxXAs.set(maxX);
				minYAs.set(minY);
				maxYAs.set(maxY);
				return value.get();
			}

			@Override
			public Color getColor(float pixelValue) {
				valueAs.set(pixelValue);
				return color.get();
			}

			@Override
			public void close() {
				color.close();
				valueAs.close();
				value.close();
				minXAs.close();
				maxXAs.close();
				minYAs.close();
				maxYAs.close();
				cause.close();
			}
		};
	}

	public Observable<? extends Causable> getChanges() {
		return Observable.or(theTypeData.getValue(XS).noInitChanges(), theTypeData.getValue(YS).noInitChanges(),
			theTypeData.getValue(AGGREGATION).noInitChanges());
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
	public QuickGradientPlot copy(ExElement parent) {
		QuickGradientPlot copy = (QuickGradientPlot) super.copy(parent);

		copy.theTypeData = theTypeData.copy(copy);

		return copy;
	}
}
