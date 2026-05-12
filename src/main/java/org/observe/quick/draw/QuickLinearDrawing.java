package org.observe.quick.draw;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.quick.draw.QuickLinearShape.QuickLineShapeStyle;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;

public interface QuickLinearDrawing extends QuickWithStroke {
	public static final String LINEAR_SHAPE = "linear-drawing";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = LINEAR_SHAPE,
		interpretation = Interpreted.class,
		instance = QuickLinearDrawing.class)
	public interface Def<E extends QuickLinearDrawing> extends QuickWithStroke.Def<E> {
		@Override
		QuickLineDrawingStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		@Override
		QuickLineDrawingStyle.Def getStyle();

		@Override
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<E extends QuickLinearDrawing> extends QuickWithStroke.Interpreted<E> {
		@Override
		QuickLinearDrawing.Def<? super E> getDefinition();

		@Override
		public QuickLineDrawingStyle.Interpreted getStyle();

		@Override
		public abstract E create();
	}

	@Override
	public QuickLineDrawingStyle getStyle();

	@Override
	public QuickLinearDrawing copy(ExElement parent);

	public interface QuickLineDrawingStyle extends QuickStrokeStyle {
		public static interface Def extends QuickStrokeStyle.Def {
			QuickStyleAttributeDef getThickness();

			public static class Default extends QuickInstanceStyle.Def.Abstract implements Def {
				private final QuickStyleAttributeDef theStrokeDash;
				private final QuickStyleAttributeDef theThickness;

				protected Default(QuickStyled.QuickInstanceStyle.Def parent, QuickLinearDrawing.Def<?> styledElement,
					QuickCompiledStyle wrapped) {
					super(parent, styledElement.getAddOn(QuickStyled.Def.class), wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, LINEAR_SHAPE);
					theStrokeDash = addApplicableAttribute(typeStyle.getAttribute("stroke-dash"));
					theThickness = addApplicableAttribute(typeStyle.getAttribute("thickness"));
				}

				@Override
				public QuickStyleAttributeDef getStrokeDash() {
					return theStrokeDash;
				}

				@Override
				public QuickStyleAttributeDef getThickness() {
					return theThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new Interpreted.Default(this, (QuickLinearDrawing.Interpreted<?>) parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
				}
			}
		}

		public static interface Interpreted extends QuickStrokeStyle.Interpreted {
			QuickElementStyleAttribute<Double> getThickness();

			public static class Default extends QuickInstanceStyle.Interpreted.Abstract implements Interpreted {
				private QuickElementStyleAttribute<StrokeDashing> theStrokeDash;
				private QuickElementStyleAttribute<Double> theThickness;

				protected Default(QuickLineDrawingStyle.Def definition, QuickLinearDrawing.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement.getAddOn(QuickStyled.Interpreted.class), parent, wrapped);
				}

				@Override
				public QuickLineDrawingStyle.Def getDefinition() {
					return (QuickLineDrawingStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<StrokeDashing> getStrokeDash() {
					return theStrokeDash;
				}

				@Override
				public QuickElementStyleAttribute<Double> getThickness() {
					return theThickness;
				}

				@Override
				public void update(ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theStrokeDash = get(cache.getAttribute(getDefinition().getStrokeDash(), StrokeDashing.class, env));
					theThickness = get(cache.getAttribute(getDefinition().getThickness(), Double.class, env));
				}

				@Override
				public QuickLineShapeStyle create(QuickStyled styled) {
					return new QuickLineShapeStyle.Default();
				}
			}
		}

		public ObservableValue<Double> getThickness();

		public static class Default extends QuickInstanceStyle.Abstract implements QuickLineDrawingStyle {
			private QuickStyleAttribute<StrokeDashing> theStrokeDashAttr;
			private QuickStyleAttribute<Double> theThicknessAttr;

			private ObservableValue<StrokeDashing> theStrokeDash;
			private ObservableValue<Double> theThickness;

			protected Default() {
			}

			@Override
			public ObservableValue<StrokeDashing> getStrokeDash() {
				return theStrokeDash;
			}

			@Override
			public ObservableValue<Double> getThickness() {
				return theThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickLineDrawingStyle.Interpreted myInterpreted = (QuickLineDrawingStyle.Interpreted) interpreted;

				theStrokeDashAttr = myInterpreted.getStrokeDash().getAttribute();
				theThicknessAttr = myInterpreted.getThickness().getAttribute();

				theStrokeDash = getApplicableAttribute(theStrokeDashAttr);
				theThickness = getApplicableAttribute(theThicknessAttr);
			}

			@Override
			public QuickLineDrawingStyle.Default copy(QuickStyled styled) {
				QuickLineDrawingStyle.Default copy = (QuickLineDrawingStyle.Default) super.copy(styled);

				copy.theStrokeDash = copy.getApplicableAttribute(theStrokeDashAttr);
				copy.theThickness = copy.getApplicableAttribute(theThicknessAttr);

				return copy;
			}
		}
	}
}
