package org.quick.base.style;

import org.quick.core.QuickProperty;
import org.quick.core.style.StyleAttribute;
import org.quick.core.style.StyleDomain;

/** Styles relating specifically to buttons */
public class ButtonStyle implements StyleDomain {
	private StyleAttribute<?> [] theAttributes;

	private ButtonStyle() {
		theAttributes = new StyleAttribute[0];
	}

	private void register(StyleAttribute<?> attr) {
		theAttributes = org.qommons.ArrayUtils.add(theAttributes, attr);
	}

	private static final ButtonStyle instance;

	/** The number of pixels that a "click" can move between its start and end and still be an actionable event */
	public static final StyleAttribute<Double> clickTolerance;

	static {
		instance = new ButtonStyle();
		clickTolerance = new StyleAttribute<>(instance, "transparency", QuickProperty.floatAttr, 5d, new QuickProperty.ComparableValidator<>(
			0d, Double.MAX_VALUE));
		instance.register(clickTolerance);
	}

	/** @return The style domain for all button styles */
	public static ButtonStyle getDomainInstance() {
		return instance;
	}

	@Override
	public String getName() {
		return "bg";
	}

	@Override
	public java.util.Iterator<StyleAttribute<?>> iterator() {
		return org.qommons.ArrayUtils.iterator(theAttributes, true);
	}
}
