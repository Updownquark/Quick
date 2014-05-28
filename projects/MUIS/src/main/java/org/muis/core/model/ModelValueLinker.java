package org.muis.core.model;

import org.muis.core.mgr.MuisMessageCenter;
import org.muis.core.rx.Action;
import org.muis.core.rx.ObservableValueEvent;
import org.muis.core.rx.Subscription;

/**
 * Links 2 values via converters
 *
 * @param <T1> The type of the left value
 * @param <T2> The type of the right value
 */
public class ModelValueLinker<T1, T2> {
	private final MuisMessageCenter theMessaging;
	private final MuisModelValue<T1> theLeft;
	private final MuisModelValue<T2> theRight;
	private ValueConverter<T1, T2> theLeftToRight;
	private ValueConverter<T2, T1> theRightToLeft;
	private final Action<ObservableValueEvent<T1>> theLeftListener;
	private Subscription<?> theLeftSubscription;
	private final Action<ObservableValueEvent<T2>> theRightListener;
	private Subscription<?> theRightSubscription;
	private boolean theEventLock;

	/**
	 * @param msg The messaging center to report problems to
	 * @param left The left value
	 * @param right The right value
	 */
	public ModelValueLinker(MuisMessageCenter msg, MuisModelValue<T1> left, MuisModelValue<T2> right) {
		theMessaging = msg;
		theLeft = left;
		theRight = right;
		theLeftListener = evt -> {
			if(theLeftToRight == null) {
				theMessaging.warn("Model value linker for " + theLeft + "<-->" + theRight + " is missing the converter from left to right",
					"left", theLeft, "right", theRight);
				return;
			}
			if(theEventLock)
				return;
			theEventLock = true;
			try {
				theRight.set(theLeftToRight.convert(evt.getValue()), MuisModelValueEvent.getUserEvent(evt));
			} finally {
				theEventLock = false;
			}
		};
		theRightListener = evt -> {
			if(theLeftToRight == null) {
				theMessaging.warn("Model value linker for " + theLeft + "<-->" + theRight + " is missing the converter from right to left",
					"left", theLeft, "right", theRight);
				return;
			}
			if(theEventLock)
				return;
			theEventLock = true;
			try {
				theLeft.set(theRightToLeft.convert(evt.getValue()), MuisModelValueEvent.getUserEvent(evt));
			} finally {
				theEventLock = false;
			}
		};
	}

	/** @return The left value */
	public MuisModelValue<T1> getLeft() {
		return theLeft;
	}

	/** @return The right value */
	public MuisModelValue<T2> getRight() {
		return theRight;
	}

	/**
	 * @param ltr The converter from the left type to the right type
	 * @return this
	 */
	public ModelValueLinker<T1, T2> setLeftToRight(ValueConverter<T1, T2> ltr) {
		theLeftToRight = ltr;
		return this;
	}

	/**
	 * @param rtl The converter from the right type to the left type
	 * @return this
	 */
	public ModelValueLinker<T1, T2> setRightToLeft(ValueConverter<T2, T1> rtl) {
		theRightToLeft = rtl;
		return this;
	}

	/**
	 * Links the two values
	 *
	 * @return this
	 */
	public ModelValueLinker<T1, T2> link() {
		if(theLeftToRight == null || theRightToLeft == null) {
			theMessaging.warn("Model value linker for " + theLeft + "<-->" + theRight + " is missing one or both of its converters",
				"left", theLeft, "right", theRight);
			// Should we throw an exception here or just link in the hope that they set the converters later?
		}
		theLeftSubscription = theLeft.act(theLeftListener);
		theRightSubscription = theRight.act(theRightListener);
		return this;
	}

	/** Unlinks the two values */
	public void unlink() {
		if(theLeftSubscription != null) {
			theLeftSubscription.unsubscribe();
			theLeftSubscription = null;
		}
		if(theRightSubscription != null) {
			theRightSubscription.unsubscribe();
			theRightSubscription = null;
		}
	}

	@Override
	public String toString() {
		return theLeft + "<-->" + theRight;
	}
}
