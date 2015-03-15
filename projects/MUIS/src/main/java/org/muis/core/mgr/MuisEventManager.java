package org.muis.core.mgr;

import org.muis.core.MuisElement;
import org.muis.core.event.MuisEvent;

/** Manages events for an element */
public class MuisEventManager extends org.muis.rx.DefaultObservable<MuisEvent> {
	private final MuisElement theElement;
	private org.muis.rx.Observer<MuisEvent> theController;

	/** @param element The element that events are being managed for */
	public MuisEventManager(MuisElement element) {
		theElement = element;
		theController = control(null);
	}

	/**
	 * @param event The event to fire for the element
	 * @return This manager, for chaining
	 */
	public MuisEventManager fire(MuisEvent event) {
		if(event.getElement() != theElement) {
			theElement.msg().error("The event[" + event + "] does not apply to this element");
			return this;
		}
		theController.onNext(event);
		return this;
	}
}
