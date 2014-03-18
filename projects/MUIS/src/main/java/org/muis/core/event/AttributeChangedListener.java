package org.muis.core.event;

import org.muis.core.MuisAttribute;
import org.muis.core.MuisElement;

/**
 * A listener that makes listening for changes to an attribute's value simpler
 *
 * @param <T> The type of the attribute
 */
public abstract class AttributeChangedListener<T> implements MuisEventListener<Object> {
	private final MuisAttribute<T> theAttr;

	/** @param attr The attribute to listen for */
	public AttributeChangedListener(MuisAttribute<T> attr) {
		theAttr = attr;
	}

	@Override
	public void eventOccurred(MuisEvent<Object> event, MuisElement element) {
		if(!(event instanceof AttributeChangedEvent))
			return;
		AttributeChangedEvent<T> ace = (AttributeChangedEvent<T>) event;
		if(theAttr != null && !ace.getAttribute().equals(theAttr))
			return;
		attributeChanged(ace);
	}

	/**
	 * Called when the value for this listener's attribute changes
	 *
	 * @param event The {@link AttributeChangedEvent} containing the value of the attribute
	 */
	public abstract void attributeChanged(AttributeChangedEvent<T> event);
}
