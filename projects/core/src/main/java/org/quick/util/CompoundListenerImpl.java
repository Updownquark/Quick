package org.quick.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.observe.*;
import org.quick.core.QuickElement;
import org.quick.core.QuickException;
import org.quick.core.prop.QuickAttribute;
import org.quick.core.style.StyleAttribute;
import org.quick.util.CompoundListener.CompoundListenerBuilder;
import org.quick.util.CompoundListener.ElementMock;
import org.quick.util.CompoundListener.EventListener;

class CompoundListenerImpl {
	private static class AttributeAccept<T> {
		final QuickAttribute<T> attr;
		final boolean required;
		final T init;

		AttributeAccept(QuickAttribute<T> attr, boolean required, T init) {
			this.attr = attr;
			this.required = required;
			this.init = init;
		}
	}

	static class ElementListenerBuilder implements CompoundListenerBuilder {
		private final List<AttributeAccept<?>> theAttributes;
		private final List<StyleAttribute<?>> theStyleAttributes;
		private final List<EventListener> theEventListeners;
		private final List<CompoundListener> theSubListeners;

		ElementListenerBuilder() {
			theAttributes = new ArrayList<>();
			theStyleAttributes = new ArrayList<>();
			theEventListeners = new ArrayList<>();
			theSubListeners = new ArrayList<>();
		}

		@Override
		public <A, V extends A> CompoundListenerBuilder accept(QuickAttribute<A> attr, boolean required, V value)
			throws IllegalArgumentException {
			theAttributes.add(new AttributeAccept<>(attr, required, value));
			return this;
		}

		@Override
		public CompoundListenerBuilder watch(StyleAttribute<?> attr) {
			theStyleAttributes.add(attr);
			return this;
		}

		@Override
		public CompoundListenerBuilder child(Consumer<CompoundListenerBuilder> builder) {
			ElementListenerBuilder childBuilder = new ElementListenerBuilder();
			builder.accept(childBuilder);
			theSubListeners.add(new ChildListener(childBuilder.build()));
			return this;
		}

		@Override
		public CompoundListenerBuilder when(Predicate<ElementMock> test, Consumer<CompoundListenerBuilder> builder) {
			ElementListenerBuilder condBuilder = new ElementListenerBuilder();
			builder.accept(condBuilder);
			theSubListeners.add(new ConditionalListener(test, condBuilder.build()));
			return this;
		}

		@Override
		public CompoundListenerBuilder onEvent(EventListener listener) {
			theEventListeners.add(listener);
			return this;
		}

		@Override
		public CompoundListener build() {
			return new ElementListener(theAttributes, theStyleAttributes, theEventListeners, theSubListeners);
		}
	}

	private static class ElementListener implements CompoundListener {
		private final List<AttributeAccept<?>> theAttributes;
		private final List<StyleAttribute<?>> theStyleAttributes;
		private final List<EventListener> theEventListeners;
		private final List<CompoundListener> theSubListeners;

		ElementListener(List<AttributeAccept<?>> attrs, List<StyleAttribute<?>> styles, List<EventListener> listeners,
			List<CompoundListener> subs) {
			theAttributes = Collections.unmodifiableList(new ArrayList<>(attrs));
			theStyleAttributes = Collections.unmodifiableList(new ArrayList<>(styles));
			theEventListeners = Collections.unmodifiableList(new ArrayList<>(listeners));
			theSubListeners = Collections.unmodifiableList(new ArrayList<>(subs));
		}

		@Override
		public void listen(QuickElement element, QuickElement root, Observable<?> until) {
			boolean[] accepted = new boolean[theAttributes.size()];
			Observer<ObservableValueEvent<?>> events = new Observer<ObservableValueEvent<?>>() {
				@Override
				public <E extends ObservableValueEvent<?>> void onNext(E event) {
					for (EventListener listener : theEventListeners)
						listener.eventOccurred(element, root, event);
				}
			};
			for (int i = 0; i < accepted.length; i++) {
				try {
					accept(element, theAttributes.get(i)).noInit().takeUntil(until).subscribe(events);
					accepted[i] = true;
				} catch (QuickException e) {
					element.msg().error("Could not accept " + theAttributes.get(i), e);
					accepted[i] = false;
				}
			}
			for (StyleAttribute<?> attr : theStyleAttributes)
				element.getStyle().get(attr).noInit().takeUntil(until).subscribe(events);
			until.act(evt -> {
				for (int i = 0; i < accepted.length; i++) {
					if (accepted[i])
						element.atts().reject(ElementListener.this, theAttributes.get(i).attr);
				}
			});
			for (CompoundListener sub : theSubListeners)
				sub.listen(element, root, until);
		}

		private <T> ObservableValue<T> accept(QuickElement element, AttributeAccept<T> attr) throws QuickException {
			return element.atts().accept(this, attr.required, attr.attr, attr.init);
		}
	}

	private static class ChildListener implements CompoundListener {
		private final CompoundListener theElementListener;

		ChildListener(CompoundListener elListener) {
			theElementListener = elListener;
		}

		@Override
		public void listen(QuickElement element, QuickElement root, Observable<?> until) {
			element.ch().onElement(child -> {
				theElementListener.listen(child.get(), root, Observable.or(until, child.noInit()));
			});
		}
	}

	/** A listener that applies another listener when a condition is met */
	private static class ConditionalListener implements CompoundListener {
		private final Predicate<ElementMock> theCondition;
		private final CompoundListener theElementListener;

		ConditionalListener(Predicate<ElementMock> condition, CompoundListener elListener) {
			theCondition = condition;
			theElementListener = elListener;
		}

		@Override
		public void listen(QuickElement element, QuickElement root, Observable<?> until) {
			ElementMockImpl mock = new ElementMockImpl(element, root, until, theElementListener, theCondition);
			mock.start();
		}
	}

	private static class ElementMockImpl implements ElementMock {
		private final QuickElement theElement;
		private final QuickElement theRoot;
		private final SimpleObservable<Void> theChangeObservable;
		private final Observable<?> theUntil;
		private final CompoundListener theListener;
		private final Predicate<ElementMock> theCondition;

		ElementMockImpl(QuickElement element, QuickElement root, Observable<?> until, CompoundListener listener,
			Predicate<ElementMock> condition) {
			theElement = element;
			theRoot = root;
			theChangeObservable = new SimpleObservable<>();
			theUntil = Observable.or(until, theChangeObservable);
			theListener = listener;
			theCondition = condition;
		}

		@Override
		public <T> T getAttribute(QuickAttribute<T> attr) {
			return getAttribute(attr, false);
		}

		<T> T getAttribute(QuickAttribute<T> attr, boolean skipOne) {
			ObservableValue<T> value = theElement.atts().getHolder(attr);
			Observable<?> onChange = value.noInit().takeUntil(theUntil);
			if (skipOne)
				onChange = onChange.skip(1);
			onChange.act(evt -> {
				changed();
			});
			return value.get();
		}

		@Override
		public <T> T getStyle(StyleAttribute<T> attr) {
			return getStyle(attr, false);
		}

		<T> T getStyle(StyleAttribute<T> attr, boolean skipOne) {
			ObservableValue<T> value = theElement.getStyle().get(attr);
			Observable<?> onChange = value.noInit().takeUntil(theUntil);
			if (skipOne)
				onChange = onChange.skip(1);
			onChange.act(evt -> {
				changed();
			});
			return value.get();
		}

		private synchronized void changed() {
			theChangeObservable.onNext(null);
			/* If we just call the condition on this, the listeners added by the get methods will be notified at the end of the current
			 * event and we'll get an infinite loop.  Need to skip this first notification. */
			boolean active = theCondition.test(new SkippingElementMock(this));
			if (active)
				theListener.listen(theElement, theRoot, theUntil);
		}

		void start() {
			boolean active = theCondition.test(this);
			if (active)
				theListener.listen(theElement, theRoot, theUntil);
		}
	}

	private static class SkippingElementMock implements ElementMock {
		private final ElementMockImpl theImpl;

		SkippingElementMock(ElementMockImpl impl) {
			theImpl = impl;
		}

		@Override
		public <T> T getAttribute(QuickAttribute<T> attr) {
			return theImpl.getAttribute(attr, true);
		}

		@Override
		public <T> T getStyle(StyleAttribute<T> attr) {
			return theImpl.getStyle(attr, true);
		}
	}
}
