package org.quick.core.event;

import java.util.function.Function;

import org.quick.core.QuickElement;
import org.quick.core.prop.QuickAttribute;

/**
 * Fired when a new value is set on an attribute
 *
 * @param <T> The type of the attribute
 */
public class AttributeChangedEvent<T> extends QuickPropertyEvent<T> {
	/** Filters events of this type */
	@SuppressWarnings("hiding")
	public static final Function<QuickEvent, AttributeChangedEvent<?>> base = value -> {
		return value instanceof AttributeChangedEvent ? (AttributeChangedEvent<?>) value : null;
	};

	/**
	 * @param <T> The type of the attribute
	 * @param attr The attribute to listen for
	 * @return A filter for change events to the given attribute
	 */
	public static <T> Function<QuickEvent, AttributeChangedEvent<T>> att(QuickAttribute<T> attr) {
		return event -> {
			AttributeChangedEvent<?> attEvt = base.apply(event);
			if(attEvt == null)
				return null;
			if(attEvt.getAttribute() != attr)
				return null;
			return (AttributeChangedEvent<T>) attEvt;
		};
	}

	private final QuickAttribute<T> theAttr;

	/**
	 * @param element The element whose attribute changed
	 * @param attr The attribute whose value changed
	 * @param initial Whether this represents the population of the initial value of an observable value in response to subscription
	 * @param oldValue The attribute's value before it was changed
	 * @param newValue The attribute's value after it was changed
	 * @param cause The cause of this event
	 */
	public AttributeChangedEvent(QuickElement element, QuickAttribute<T> attr, boolean initial, T oldValue, T newValue, Object cause) {
		super(element, attr.getType().getType(), initial, oldValue, newValue, cause);
		theAttr = attr;
	}

	/** @return The attribute whose value changed */
	public QuickAttribute<T> getAttribute() {
		return theAttr;
	}
}
