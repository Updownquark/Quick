package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;

public interface QuickWidgetExport extends ExElement {
	public interface Def<E extends QuickWidgetExport> extends ExElement.Def<E> {
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<E extends QuickWidgetExport> extends ExElement.Interpreted<E> {
		void updateExport() throws ExpressoInterpretationException;

		E create();
	}

	@Override
	QuickWidgetExport copy(ExElement parent);
}
