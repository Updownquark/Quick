package org.observe.quick;

import java.text.ParseException;

import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.NonStructuredParser;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** A size for one dimension of a widget or shape. A QuickSize may be absolute (e.g. pixels) or relative to the size of the container. */
public interface QuickSize {
	/** An absolute size with zero magnitude */
	static ZeroSize ZERO = new ZeroSize();

	/** @return This size's absolute pixel value as an integer */
	int getIPixels();

	/** @return Whether this size has a floating-point pixel value */
	boolean hasFloatPixels();

	/** @return This size's absolute pixel value as a float */
	float getFPixels();

	/** @return Whether this size has a relative ({@link #getPercent()}) component */
	boolean isRelative();

	/** @return This size's relative component as a percent of the container size */
	float getPercent();

	/**
	 * @param containerSize The size of the container
	 * @return This size's value
	 */
	int evaluateInt(int containerSize);

	/**
	 * @param containerSize The size of the container
	 * @return This size's value
	 */
	float evaluateFloat(float containerSize);

	/**
	 * @return The number of pixels that a container must be for this size's pixels to be equal to its relative size as specified by
	 *         {@link #getPercent()}, or just this size's pixels if one or both fields are zero
	 */
	int resolveIntExponential();

	/**
	 * @return The number of pixels that a container must be for this size's pixels to be equal to its relative size as specified by
	 *         {@link #getPercent()}, or just this size's pixels if one or both fields are zero
	 */
	float resolveFloatExponential();

	/** @return The negative of this size */
	QuickSize negate();

	/**
	 * @param other The other size to add
	 * @return A size that is this size plus the other
	 */
	QuickSize plus(QuickSize other);

	/**
	 * @param pixels The pixels to add to this size
	 * @return A size that is this size plus the given number of pixels
	 */
	QuickSize plus(int pixels);

	/**
	 * @param pixels The pixels to add to this size
	 * @return A size that is this size plus the given number of pixels
	 */
	QuickSize plus(float pixels);

	/**
	 * @param other The other size to subtract
	 * @return A new size that is this size minus the other
	 */
	QuickSize minus(QuickSize other);

	/**
	 * @param amount The amount to multiply this size by
	 * @return A size that is this size times the given amount
	 */
	QuickSize times(int amount);

	/**
	 * @param amount The amount to multiply this size by
	 * @return A size that is this size times the given amount
	 */
	QuickSize times(float amount);

	/**
	 * @param amount The amount to divide this size by
	 * @return A size that is this size divided by the given amount
	 */
	QuickSize divideBy(int amount);

	float divideBy(QuickSize other);

	/**
	 * @param pixels The number of pixels to create the size for
	 * @return An absolute size with the given number of pixels
	 */
	static QuickSize ofPixels(int pixels) {
		return pixels == 0 ? ZERO : new IntPixels(pixels);
	}

	/**
	 * @param pixels The number of pixels to create the size for
	 * @return An absolute size with the given number of pixels
	 */
	static QuickSize ofPixels(float pixels) {
		return pixels == 0.0f ? ZERO : new FloatPixels(pixels);
	}

	/**
	 * @param percent The relative size in percent of the container length
	 * @return A relative size with the given percentage
	 */
	static QuickSize ofPercent(float percent) {
		return percent == 0.0f ? ZERO : new PercentSize(percent);
	}

	/**
	 * @param lexips The number of pixels from the trailing edge of the container
	 * @return A size (really this only makes sense for a position) that evaluates to the length of the container minus the given number of
	 *         pixels
	 */
	static QuickSize ofLexips(int lexips) {
		return lexips == 0 ? new PercentSize(100.0f) : new IntMixed(100.0f, -lexips);
	}

	/**
	 * @param lexips The number of pixels from the trailing edge of the container
	 * @return A size (really this only makes sense for a position) that evaluates to the length of the container minus the given number of
	 *         pixels
	 */
	static QuickSize ofLexips(float lexips) {
		return lexips == 0.0f ? new PercentSize(100.0f) : new FloatMixed(100.0f, -lexips);
	}

	/**
	 * @param percent The relative size in percent of the container length
	 * @param pixels The number of pixels to create the size for
	 * @return A size that evaluates to the given percentage of the container length plus the given number of pixels
	 */
	static QuickSize of(float percent, int pixels) {
		if (percent == 0.0f)
			return ofPixels(pixels);
		else if (pixels == 0)
			return new PercentSize(percent);
		else
			return new IntMixed(percent, pixels);
	}

	/**
	 * @param percent The relative size in percent of the container length
	 * @param pixels The number of pixels to create the size for
	 * @return A size that evaluates to the given percentage of the container length plus the given number of pixels
	 */
	static QuickSize of(float percent, float pixels) {
		if (percent == 0.0f)
			return ofPixels(pixels);
		else if (pixels == 0.0f)
			return new PercentSize(percent);
		else
			return new FloatMixed(percent, pixels);
	}

	/**
	 * @param text The text to parse as a position
	 * @return The parsed position
	 * @throws NumberFormatException If the number component of the position could not be parsed
	 */
	static QuickSize parsePosition(String text) throws NumberFormatException {
		boolean isFloat = false;
		for (int c = 0; !isFloat && c < text.length(); c++) {
			switch (text.charAt(c)) {
			case '.':
			case 'E':
			case 'e':
				isFloat = true;
			}
		}
		String content;
		boolean px = false, xp = false;
		if (text.endsWith("px")) {
			content = text.substring(0, text.length() - 2);
			px = true;
		} else if (text.endsWith("%")) {
			content = text.substring(0, text.length() - 1);
		} else if (text.endsWith("xp")) {
			content = text.substring(0, text.length() - 2);
			xp = true;
		} else {
			content = text;
			px = true;
		}
		if (px)
			return isFloat ? ofPixels(Float.parseFloat(content)) : ofPixels(Integer.parseInt(content));
		else if (xp)
			return isFloat ? ofLexips(Float.parseFloat(content)) : ofLexips(Integer.parseInt(content));
		else
			return ofPercent(Float.parseFloat(content));
	}

	/**
	 * @param text The text to parse as a size
	 * @return The parsed size
	 * @throws NumberFormatException If the number component of the size could not be parsed
	 */
	static QuickSize parseSize(String text) throws NumberFormatException {
		boolean isFloat = false;
		for (int c = 0; !isFloat && c < text.length(); c++) {
			switch (text.charAt(c)) {
			case '.':
			case 'E':
			case 'e':
				isFloat = true;
			}
		}
		String content;
		boolean px = false;
		if (text.endsWith("px")) {
			content = text.substring(0, text.length() - 2);
			px = true;
		} else if (text.endsWith("%")) {
			content = text.substring(0, text.length() - 1);
		} else {
			content = text;
			px = true;
		}
		if (px)
			return isFloat ? ofPixels(Float.parseFloat(content)) : ofPixels(Integer.parseInt(content));
		else
			return ofPercent(Float.parseFloat(content));
	}

	/** The type of {@link QuickSize#ZERO} */
	static class ZeroSize implements QuickSize {
		private ZeroSize() {
		}

		@Override
		public int getIPixels() {
			return 0;
		}

		@Override
		public boolean hasFloatPixels() {
			return false;
		}

		@Override
		public float getFPixels() {
			return 0.0f;
		}

		@Override
		public boolean isRelative() {
			return false;
		}

		@Override
		public float getPercent() {
			return 0.0f;
		}

		@Override
		public int evaluateInt(int containerSize) {
			return 0;
		}

		@Override
		public float evaluateFloat(float containerSize) {
			return 0.0f;
		}

		@Override
		public int resolveIntExponential() {
			return 0;
		}

		@Override
		public float resolveFloatExponential() {
			return 0.0f;
		}

		@Override
		public QuickSize negate() {
			return this;
		}

		@Override
		public QuickSize plus(QuickSize other) {
			return other;
		}

		@Override
		public QuickSize plus(int pixels) {
			return pixels == 0 ? this : new IntPixels(pixels);
		}

		@Override
		public QuickSize plus(float pixels) {
			return pixels == 0.0f ? this : new FloatPixels(pixels);
		}

		@Override
		public QuickSize minus(QuickSize other) {
			return other.negate();
		}

		@Override
		public QuickSize times(int amount) {
			return this;
		}

		@Override
		public QuickSize times(float amount) {
			return this;
		}

		@Override
		public QuickSize divideBy(int amount) {
			return this;
		}

		@Override
		public float divideBy(QuickSize other) {
			return 0.0f;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QuickSize))
				return false;
			QuickSize other = (QuickSize) obj;
			if (other.isRelative())
				return false;
			else if (other.hasFloatPixels())
				return other.getFPixels() == 0.0f;
			else
				return other.getIPixels() == 0;
		}

		@Override
		public String toString() {
			return "0px";
		}
	}

	/** Absolute size with int-typed pixels */
	static class IntPixels implements QuickSize {
		private final int thePixels;

		private IntPixels(int pixels) {
			thePixels = pixels;
		}

		@Override
		public int getIPixels() {
			return thePixels;
		}

		@Override
		public boolean hasFloatPixels() {
			return false;
		}

		@Override
		public float getFPixels() {
			return thePixels;
		}

		@Override
		public boolean isRelative() {
			return false;
		}

		@Override
		public float getPercent() {
			return 0.0f;
		}

		@Override
		public int evaluateInt(int containerSize) {
			return thePixels;
		}

		@Override
		public float evaluateFloat(float containerSize) {
			return thePixels;
		}

		@Override
		public int resolveIntExponential() {
			return thePixels;
		}

		@Override
		public float resolveFloatExponential() {
			return thePixels;
		}

		@Override
		public QuickSize negate() {
			return new IntPixels(-thePixels);
		}

		@Override
		public QuickSize plus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative()) {
				if (other.hasFloatPixels())
					return QuickSize.of(other.getPercent(), thePixels + other.getFPixels());
				else
					return QuickSize.of(other.getPercent(), thePixels + other.getIPixels());
			} else {
				if (other.hasFloatPixels())
					return QuickSize.ofPixels(thePixels + other.getFPixels());
				else
					return QuickSize.ofPixels(thePixels + other.getIPixels());
			}
		}

		@Override
		public QuickSize plus(int pixels) {
			return QuickSize.ofPixels(thePixels + pixels);
		}

		@Override
		public QuickSize plus(float pixels) {
			return QuickSize.ofPixels(thePixels + pixels);
		}

		@Override
		public QuickSize minus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative()) {
				if (other.hasFloatPixels())
					return QuickSize.of(-other.getPercent(), thePixels - other.getFPixels());
				else
					return QuickSize.of(-other.getPercent(), thePixels - other.getIPixels());
			} else {
				if (other.hasFloatPixels())
					return QuickSize.ofPixels(thePixels - other.getFPixels());
				else
					return QuickSize.ofPixels(thePixels - other.getIPixels());
			}
		}

		@Override
		public QuickSize times(int amount) {
			switch (amount) {
			case 0:
				return ZERO;
			case 1:
				return this;
			default:
				return new IntPixels(thePixels * amount);
			}
		}

		@Override
		public QuickSize times(float amount) {
			if (amount == 0.0f)
				return ZERO;
			else if (amount == 1.0f)
				return this;
			else
				return new FloatPixels(thePixels * amount);
		}

		@Override
		public QuickSize divideBy(int amount) {
			if (amount == 1)
				return this;
			else if (Math.abs(amount) > thePixels)
				return ZERO;
			else
				return new IntPixels(thePixels / amount);
		}

		@Override
		public float divideBy(QuickSize other) {
			return thePixels / other.getFPixels();
		}

		@Override
		public int hashCode() {
			return thePixels;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QuickSize))
				return false;
			QuickSize other = (QuickSize) obj;
			if (other.isRelative())
				return false;
			else if (other.hasFloatPixels())
				return thePixels == other.getFPixels();
			else
				return thePixels == other.getIPixels();
		}

		@Override
		public String toString() {
			return thePixels + "px";
		}
	}

	/** Absolute size with float-typed pixels */
	static class FloatPixels implements QuickSize {
		private final float thePixels;

		private FloatPixels(float pixels) {
			thePixels = pixels;
		}

		@Override
		public int getIPixels() {
			return (int) thePixels;
		}

		@Override
		public boolean hasFloatPixels() {
			return true;
		}

		@Override
		public float getFPixels() {
			return thePixels;
		}

		@Override
		public boolean isRelative() {
			return false;
		}

		@Override
		public float getPercent() {
			return 0.0f;
		}

		@Override
		public int evaluateInt(int containerSize) {
			return (int) thePixels;
		}

		@Override
		public float evaluateFloat(float containerSize) {
			return thePixels;
		}

		@Override
		public int resolveIntExponential() {
			return (int) thePixels;
		}

		@Override
		public float resolveFloatExponential() {
			return thePixels;
		}

		@Override
		public QuickSize negate() {
			return new FloatPixels(-thePixels);
		}

		@Override
		public QuickSize plus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative())
				return QuickSize.of(other.getPercent(), thePixels + other.getFPixels());
			else
				return QuickSize.ofPixels(thePixels + other.getFPixels());
		}

		@Override
		public QuickSize plus(int pixels) {
			return QuickSize.ofPixels(thePixels + pixels);
		}

		@Override
		public QuickSize plus(float pixels) {
			return QuickSize.ofPixels(thePixels + pixels);
		}

		@Override
		public QuickSize minus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative())
				return QuickSize.of(-other.getPercent(), thePixels - other.getFPixels());
			else
				return QuickSize.ofPixels(thePixels - other.getFPixels());
		}

		@Override
		public QuickSize times(int amount) {
			switch (amount) {
			case 0:
				return ZERO;
			case 1:
				return this;
			default:
				return new FloatPixels(thePixels * amount);
			}
		}

		@Override
		public QuickSize times(float amount) {
			if (amount == 0.0f)
				return ZERO;
			else if (amount == 1.0f)
				return this;
			else
				return new FloatPixels(thePixels * amount);
		}

		@Override
		public QuickSize divideBy(int amount) {
			if (amount == 1)
				return this;
			else
				return new FloatPixels(thePixels / amount);
		}

		@Override
		public float divideBy(QuickSize other) {
			return thePixels / other.getFPixels();
		}

		@Override
		public int hashCode() {
			return Float.hashCode(thePixels);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QuickSize))
				return false;
			QuickSize other = (QuickSize) obj;
			return !other.isRelative() && other.getFPixels() == thePixels;
		}

		@Override
		public String toString() {
			return thePixels + "px";
		}
	}

	/** Relative size with no absolute component */
	static class PercentSize implements QuickSize {
		private final float thePercent;

		private PercentSize(float percent) {
			thePercent = percent;
		}

		@Override
		public int getIPixels() {
			return 0;
		}

		@Override
		public boolean hasFloatPixels() {
			return false;
		}

		@Override
		public float getFPixels() {
			return 0.0f;
		}

		@Override
		public boolean isRelative() {
			return true;
		}

		@Override
		public float getPercent() {
			return thePercent;
		}

		@Override
		public int evaluateInt(int containerSize) {
			return Math.round(containerSize / 100.0f * thePercent);
		}

		@Override
		public float evaluateFloat(float containerSize) {
			return containerSize / 100.0f * thePercent;
		}

		@Override
		public int resolveIntExponential() {
			return 0;
		}

		@Override
		public float resolveFloatExponential() {
			return 0.0f;
		}

		@Override
		public QuickSize negate() {
			return new PercentSize(-thePercent);
		}

		@Override
		public QuickSize plus(QuickSize other) {
			if (other.isRelative()) {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent + other.getPercent(), other.getFPixels());
				else
					return QuickSize.of(thePercent + other.getPercent(), other.getIPixels());
			} else {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent, other.getFPixels());
				else
					return QuickSize.of(thePercent, other.getIPixels());
			}
		}

		@Override
		public QuickSize plus(int pixels) {
			return pixels == 0 ? this : new IntMixed(thePercent, pixels);
		}

		@Override
		public QuickSize plus(float pixels) {
			return pixels == 0.0f ? this : new FloatMixed(thePercent, pixels);
		}

		@Override
		public QuickSize minus(QuickSize other) {
			if (other.isRelative()) {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent - other.getPercent(), -other.getFPixels());
				else
					return QuickSize.of(thePercent - other.getPercent(), -other.getIPixels());
			} else {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent, -other.getFPixels());
				else
					return QuickSize.of(thePercent, -other.getIPixels());
			}
		}

		@Override
		public QuickSize times(int amount) {
			if (amount == 0)
				return ZERO;
			else if (amount == 1)
				return this;
			else
				return new PercentSize(thePercent * amount);
		}

		@Override
		public QuickSize times(float amount) {
			if (amount == 0.0f)
				return ZERO;
			else if (amount == 1.0f)
				return this;
			else
				return new PercentSize(thePercent * amount);
		}

		@Override
		public QuickSize divideBy(int amount) {
			if (amount == 1)
				return this;
			else
				return new PercentSize(thePercent / amount);
		}

		@Override
		public float divideBy(QuickSize other) {
			return thePercent / other.getPercent();
		}

		@Override
		public int hashCode() {
			return Float.hashCode(thePercent);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (obj instanceof PercentSize)
				return thePercent == ((PercentSize) obj).thePercent;
			else if (!(obj instanceof QuickSize))
				return false;
			QuickSize other = (QuickSize) obj;
			if (!other.isRelative() || thePercent != other.getPercent())
				return false;
			else if (other.hasFloatPixels())
				return other.getFPixels() == 0;
			else
				return other.getIPixels() == 0;
		}

		@Override
		public String toString() {
			return thePercent + "%";
		}
	}

	/** Relative size with int-typed pixels */
	static class IntMixed implements QuickSize {
		private final float thePercent;
		private final int thePixels;

		private IntMixed(float percent, int pixels) {
			thePercent = percent;
			thePixels = pixels;
		}

		@Override
		public int getIPixels() {
			return thePixels;
		}

		@Override
		public boolean hasFloatPixels() {
			return false;
		}

		@Override
		public float getFPixels() {
			return thePixels;
		}

		@Override
		public boolean isRelative() {
			return true;
		}

		@Override
		public float getPercent() {
			return thePercent;
		}

		@Override
		public int evaluateInt(int containerSize) {
			return Math.round(containerSize / 100.0f * thePercent) + thePixels;
		}

		@Override
		public float evaluateFloat(float containerSize) {
			return containerSize / 100.0f * thePercent + thePixels;
		}

		@Override
		public int resolveIntExponential() {
			if (thePercent <= 0 || thePercent >= 100 || thePixels <= 0)
				return thePixels;
			// Solve absSize+percent/100*totalSize = totalSize
			return Math.round(thePixels / (1 - thePercent / 100));
		}

		@Override
		public float resolveFloatExponential() {
			if (thePercent <= 0 || thePercent >= 100 || thePixels <= 0)
				return thePixels;
			// Solve absSize+percent/100*totalSize = totalSize
			return thePixels / (1 - thePercent / 100);
		}

		@Override
		public QuickSize negate() {
			return new IntMixed(-thePercent, -thePixels);
		}

		@Override
		public QuickSize plus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative()) {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent + other.getPercent(), thePixels + other.getFPixels());
				else
					return QuickSize.of(thePercent + other.getPercent(), thePixels + other.getIPixels());
			} else {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent, thePixels + other.getFPixels());
				else
					return QuickSize.of(thePercent, thePixels + other.getIPixels());
			}
		}

		@Override
		public QuickSize plus(int pixels) {
			if (pixels == 0)
				return this;
			int newPixels = thePixels + pixels;
			if (newPixels == 0)
				return new PercentSize(thePercent);
			else
				return QuickSize.of(thePercent, newPixels);
		}

		@Override
		public QuickSize plus(float pixels) {
			if (pixels == 0.0f)
				return this;
			float newPixels = thePixels + pixels;
			if (newPixels == 0.0f)
				return new PercentSize(thePercent);
			else
				return QuickSize.of(thePercent, newPixels);
		}

		@Override
		public QuickSize minus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative()) {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent - other.getPercent(), thePixels - other.getFPixels());
				else
					return QuickSize.of(thePercent - other.getPercent(), thePixels - other.getIPixels());
			} else {
				if (other.hasFloatPixels())
					return QuickSize.of(thePercent, thePixels - other.getFPixels());
				else
					return QuickSize.of(thePercent, thePixels - other.getIPixels());
			}
		}

		@Override
		public QuickSize times(int amount) {
			switch (amount) {
			case 0:
				return ZERO;
			case 1:
				return this;
			default:
				return new IntMixed(thePercent * amount, thePixels * amount);
			}
		}

		@Override
		public QuickSize times(float amount) {
			if (amount == 0.0f)
				return ZERO;
			else if (amount == 1.0f)
				return this;
			else
				return new FloatMixed(thePercent * amount, thePixels * amount);
		}

		@Override
		public QuickSize divideBy(int amount) {
			if (amount == 1)
				return this;
			else if (Math.abs(amount) > thePixels)
				return new PercentSize(thePercent / amount);
			else
				return new IntMixed(thePercent / amount, thePixels / amount);
		}

		@Override
		public float divideBy(QuickSize other) {
			float pctDiv = thePercent / other.getPercent();
			float pixDiv = thePixels / other.getFPixels();
			if (Float.isInfinite(pctDiv))
				return pixDiv;
			else if (Float.isInfinite(pixDiv))
				return pctDiv;
			else
				return Math.max(pctDiv, pixDiv);
		}

		@Override
		public int hashCode() {
			return Integer.rotateLeft(Float.hashCode(thePercent), 16) ^ thePixels;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QuickSize))
				return false;
			QuickSize other = (QuickSize) obj;
			if (!other.isRelative() || thePercent != other.getPercent())
				return false;
			else if (other.hasFloatPixels())
				return other.getFPixels() == thePixels;
			else
				return other.getIPixels() == thePixels;
		}

		@Override
		public String toString() {
			if (thePixels >= 0)
				return thePercent + "%+" + thePixels + "px";
			else
				return thePercent + "%" + thePixels + "px";
		}
	}

	/** Relative size with float-typed pixels */
	static class FloatMixed implements QuickSize {
		private final float thePercent;
		private final float thePixels;

		private FloatMixed(float percent, float pixels) {
			thePercent = percent;
			thePixels = pixels;
		}

		@Override
		public int getIPixels() {
			return (int) thePixels;
		}

		@Override
		public boolean hasFloatPixels() {
			return true;
		}

		@Override
		public float getFPixels() {
			return thePixels;
		}

		@Override
		public boolean isRelative() {
			return true;
		}

		@Override
		public float getPercent() {
			return thePercent;
		}

		@Override
		public int evaluateInt(int containerSize) {
			return Math.round(containerSize / 100.0f * thePercent + thePixels);
		}

		@Override
		public float evaluateFloat(float containerSize) {
			return containerSize / 100.0f * thePercent + thePixels;
		}

		@Override
		public int resolveIntExponential() {
			if (thePercent <= 0 || thePercent >= 100 || thePixels <= 0)
				return (int) thePixels;
			// Solve absSize+percent/100*totalSize = totalSize
			return Math.round(thePixels / (1 - thePercent / 100));
		}

		@Override
		public float resolveFloatExponential() {
			if (thePercent <= 0 || thePercent >= 100 || thePixels <= 0)
				return (int) thePixels;
			// Solve absSize+percent/100*totalSize = totalSize
			return thePixels / (1 - thePercent / 100);
		}

		@Override
		public QuickSize negate() {
			return new FloatMixed(-thePercent, -thePixels);
		}

		@Override
		public QuickSize plus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative()) {
				return QuickSize.of(thePercent + other.getPercent(), thePixels + other.getFPixels());
			} else {
				return QuickSize.of(thePercent, thePixels + other.getFPixels());
			}
		}

		@Override
		public QuickSize plus(int pixels) {
			if (pixels == 0)
				return this;
			float newPixels = thePixels + pixels;
			if (newPixels == 0.0f)
				return new PercentSize(thePercent);
			else
				return QuickSize.of(thePercent, newPixels);
		}

		@Override
		public QuickSize plus(float pixels) {
			if (pixels == 0)
				return this;
			float newPixels = thePixels + pixels;
			if (newPixels == 0.0f)
				return new PercentSize(thePercent);
			else
				return QuickSize.of(thePercent, newPixels);
		}

		@Override
		public QuickSize minus(QuickSize other) {
			if (other == ZERO)
				return this;
			else if (other.isRelative()) {
				return QuickSize.of(thePercent - other.getPercent(), thePixels - other.getFPixels());
			} else {
				return QuickSize.of(thePercent, thePixels - other.getFPixels());
			}
		}

		@Override
		public QuickSize times(int amount) {
			switch (amount) {
			case 0:
				return ZERO;
			case 1:
				return this;
			default:
				return new FloatMixed(thePercent * amount, thePixels * amount);
			}
		}

		@Override
		public QuickSize times(float amount) {
			if (amount == 0.0f)
				return ZERO;
			else if (amount == 1.0f)
				return this;
			else
				return new FloatMixed(thePercent * amount, thePixels * amount);
		}

		@Override
		public QuickSize divideBy(int amount) {
			if (amount == 1)
				return this;
			else
				return new FloatMixed(thePercent / amount, thePixels / amount);
		}

		@Override
		public float divideBy(QuickSize other) {
			float pctDiv = thePercent / other.getPercent();
			float pixDiv = thePixels / other.getFPixels();
			if (Float.isInfinite(pctDiv))
				return pixDiv;
			else if (Float.isInfinite(pixDiv))
				return pctDiv;
			else
				return Math.max(pctDiv, pixDiv);
		}

		@Override
		public int hashCode() {
			return Integer.rotateLeft(Float.hashCode(thePercent), 16) ^ Float.hashCode(thePixels);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QuickSize))
				return false;
			QuickSize other = (QuickSize) obj;
			if (!other.isRelative() || thePercent != other.getPercent())
				return false;
			else if (other.hasFloatPixels())
				return other.getFPixels() == thePixels;
			else
				return other.getIPixels() == thePixels;
		}

		@Override
		public String toString() {
			if (thePixels >= 0.0f)
				return thePercent + "%+" + thePixels + "px";
			else
				return thePercent + "%" + thePixels + "px";
		}
	}

	/** Parses {@link QuickSize}s for Expresso */
	static class Parser extends NonStructuredParser.Simple<QuickSize> {
		private final boolean isPosition;

		/** @param position Whether this parser should parse positions (potentially with "xp" unit) or sizes */
		public Parser(boolean position) {
			super(TypeTokens.get().of(QuickSize.class), TypeTokens.get().of(QuickSize.class));
			this.isPosition = position;
		}

		@Override
		public boolean checkText(String text, InterpretedExpressoEnv env) {
			int c = 0;
			for (; c < text.length(); c++) {
				if (!Character.isWhitespace(text.charAt(c)))
					break;
			}
			int digits = 0;
			for (; c < text.length(); c++) {
				if (text.charAt(c) < '0' || text.charAt(c) > '9')
					break;
				digits++;
			}
			if (digits == 0)
				return false;
			for (; c < text.length(); c++) {
				if (!Character.isWhitespace(text.charAt(c)))
					break;
			}
			switch (text.substring(c).trim()) {
			case "px":
			case "%":
				break;
			case "xp":
				if (isPosition)
					break;
				else
					return false;
			default:
				return false;
			}
			return true;
		}

		@Override
		protected <T2 extends QuickSize> T2 parseValue(TypeToken<T2> type, String text, InterpretedExpressoEnv env) throws ParseException {
			boolean pct, xp;
			int unit;
			if (text.endsWith("%")) {
				unit = 1;
				pct = true;
				xp = false;
			} else if (text.endsWith("px")) {
				unit = 2;
				pct = false;
				xp = false;
			} else if (isPosition && text.endsWith("xp")) {
				unit = 2;
				pct = false;
				xp = true;
			} else {
				pct = xp = false;
				unit = 0;
			}
			String numberStr;
			if (unit > 0)
				numberStr = text.substring(0, text.length() - unit).trim();
			else {
				numberStr = text.trim();
				int lastDig;
				for (lastDig = numberStr.length() - 1; lastDig >= 0; lastDig--) {
					char ch = numberStr.charAt(lastDig);
					if (ch < '0' || ch > '9')
						break;
				}
				lastDig++;
				if (lastDig < numberStr.length())
					throw new ParseException(
						"Unrecognized " + (isPosition ? "position" : "size") + " unit: '" + numberStr.substring(lastDig), lastDig);
			}

			if (pct) {
				float value;
				try {
					value = Float.parseFloat(numberStr);
				} catch (NumberFormatException e) {
					throw new ParseException("Could not parse " + (isPosition ? "position" : "size") + " value: '" + numberStr + "'", 0);
				}
				return (T2) ofPercent(value);
			} else {
				int value;
				try {
					value = Integer.parseInt(numberStr);
				} catch (NumberFormatException e) {
					throw new ParseException("Could not parse " + (isPosition ? "position" : "size") + " value: '" + numberStr + "'", 0);
				}
				if (xp)
					return (T2) ofLexips(value);
				else
					return (T2) ofPixels(value);
			}
		}

		@Override
		public String getDescription() {
			return "Simple " + (isPosition ? "position" : "size") + " literal";
		}
	}
}
