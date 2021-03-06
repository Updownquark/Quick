package org.quick.core.model;

import org.quick.core.model.QuickDocumentModel.StyledSequence;
import org.quick.core.style.QuickStyle;

/** A simple styled sequence */
public class SimpleStyledSequence implements StyledSequence {
	private final String theValue;

	private final QuickStyle theStyle;

	/**
	 * @param value The content for the sequence
	 * @param style The style for the sequence
	 */
	public SimpleStyledSequence(String value, QuickStyle style) {
		theValue = value;
		theStyle = style;
	}

	@Override
	public int length() {
		return theValue.length();
	}

	@Override
	public char charAt(int index) {
		return theValue.charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return theValue.subSequence(start, end);
	}

	@Override
	public String toString() {
		return theValue;
	}

	@Override
	public QuickStyle getStyle() {
		return theStyle;
	}
}
