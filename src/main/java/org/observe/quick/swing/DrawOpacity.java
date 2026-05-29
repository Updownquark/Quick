package org.observe.quick.swing;

import org.qommons.QommonsUtils;

/** A qualitative representation of opacity */
public enum DrawOpacity {
	/** Completely transparent */
	None,
	/** Partially transparent */
	Partial,
	/** Completely opaque */
	Full;

	/**
	 * @param other Another opacity
	 * @return The maximum of the two opacities
	 */
	public DrawOpacity or(DrawOpacity other) {
		return QommonsUtils.max(this, other);
	}
}