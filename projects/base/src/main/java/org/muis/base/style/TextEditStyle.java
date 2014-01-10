package org.muis.base.style;

import org.muis.core.MuisProperty;
import org.muis.core.style.StyleAttribute;

/** Style attributes relevant to text editors */
public class TextEditStyle implements org.muis.core.style.StyleDomain {
	private StyleAttribute<?> [] theAttributes;

	private TextEditStyle() {
		theAttributes = new StyleAttribute[0];
	}

	private void register(StyleAttribute<?> attr) {
		theAttributes = prisms.util.ArrayUtils.add(theAttributes, attr);
	}

	private static final TextEditStyle instance;

	/** The amount of time between cursor blinks */
	public static final StyleAttribute<Long> cursorBlink;

	static {
		instance = new TextEditStyle();
		cursorBlink = new StyleAttribute<>(instance, "cursor-blink", MuisProperty.timeAttr, 500L, new MuisProperty.ComparableValidator<>(
			0L, 60000L));
		instance.register(cursorBlink);
	}

	/** @return The style domain for all text editor styles */
	public static TextEditStyle getDomainInstance() {
		return instance;
	}

	@Override
	public String getName() {
		return "text-edit-style";
	}

	@Override
	public java.util.Iterator<StyleAttribute<?>> iterator() {
		return prisms.util.ArrayUtils.iterator(theAttributes, true);
	}
}
