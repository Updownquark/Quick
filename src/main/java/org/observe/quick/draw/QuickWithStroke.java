package org.observe.quick.draw;

import org.observe.ObservableValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickStyledElement;

public interface QuickWithStroke extends QuickStyledElement {
	public static final String WITH_STROKE = "with-stroke";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = WITH_STROKE,
		interpretation = Interpreted.class,
		instance = QuickWithStroke.class)
	public interface Def<E extends QuickWithStroke> extends QuickStyledElement.Def<E> {
		@Override
		QuickStrokeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		@Override
		QuickStrokeStyle.Def getStyle();

		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<E extends QuickWithStroke> extends QuickStyledElement.Interpreted<E> {
		@Override
		QuickWithStroke.Def<? super E> getDefinition();

		@Override
		public QuickStrokeStyle.Interpreted getStyle();

		public abstract E create();
	}

	@Override
	public QuickStrokeStyle getStyle();

	@Override
	public QuickWithStroke copy(ExElement parent);

	public interface QuickStrokeStyle extends QuickInstanceStyle {
		public static interface Def extends QuickInstanceStyle.Def {
			QuickStyleAttributeDef getStrokeDash();
		}

		public static interface Interpreted extends QuickInstanceStyle.Interpreted {
			QuickElementStyleAttribute<StrokeDashing> getStrokeDash();
		}

		public ObservableValue<StrokeDashing> getStrokeDash();
	}
}
