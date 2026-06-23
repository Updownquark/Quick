package org.observe.quick;

import java.io.File;

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

public class WidgetFileExport extends ExElement.Abstract implements QuickWidgetExport {
	public static final String WIDGET_FILE_EXPORT = "widget-file-export";

	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = WIDGET_FILE_EXPORT,
		interpretation = Interpreted.class,
		instance = WidgetFileExport.class)
	public static class Def<E extends WidgetFileExport> extends ExElement.Def.Abstract<E> implements QuickWidgetExport.Def<E> {
		private CompiledExpression theFile;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("file")
		public CompiledExpression getFile() {
			return theFile;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theFile = getAttributeExpression("file", session);
		}

		@Override
		public Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<E extends WidgetFileExport> extends ExElement.Interpreted.Abstract<E>
		implements QuickWidgetExport.Interpreted<E> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<File>> theFile;

		protected Interpreted(Def<? super E> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super E> getDefinition() {
			return (Def<? super E>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<File>> getFile() {
			return theFile;
		}

		@Override
		public void updateExport() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theFile = interpret(getDefinition().getFile(), ModelTypes.Value.forType(File.class));
		}

		@Override
		public E create() {
			return (E) new WidgetFileExport(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<File>> theFileInstantiator;

	private SettableValue<SettableValue<File>> theFile;

	protected WidgetFileExport(Object id) {
		super(id);
		theFile = SettableValue.create();
	}

	public SettableValue<File> getFile() {
		return SettableValue.flatten(theFile);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		theFileInstantiator = myInterpreted.getFile().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theFileInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theFile.set(theFileInstantiator.get(myModels));

		return myModels;
	}

	@Override
	public WidgetFileExport copy(ExElement parent) {
		WidgetFileExport copy = (WidgetFileExport) super.copy(parent);

		copy.theFile = SettableValue.create();

		return copy;
	}
}
