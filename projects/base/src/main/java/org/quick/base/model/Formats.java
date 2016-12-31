package org.quick.base.model;

import java.awt.Color;
import java.util.function.Function;

import org.observe.Observable;
import org.quick.core.QuickException;
import org.quick.core.model.QuickDocumentModel;
import org.quick.core.model.SelectableDocumentModel;
import org.quick.core.style.Colors;

import com.google.common.reflect.TypeToken;

/** A utility class containing standard formats */
public class Formats {
	/** Formats objects by their {@link Object#toString()} methods. Does not support parsing. */
	public static final QuickFormatter<Object> def = new SimpleFormatter<Object>() {
		@Override
		public TypeToken<Object> getFormatType() {
			return TypeToken.of(Object.class);
		}

		@Override
		public TypeToken<?> getParseType() {
			return null;
		}

		@Override
		public String format(Object value) {
			return String.valueOf(value);
		}

		@Override
		public Object parse(String text) throws QuickParseException {
			throw new QuickParseException("The default formatter does not support parsing", -1, -1);
		}

		@Override
		public String toString() {
			return "formats.def";
		}
	};

	/** A function that is a pass-through for non-null values and returns the default formatter for null values */
	public static final Function<QuickFormatter<?>, QuickFormatter<?>> defNullCatch = (QuickFormatter<?> f) -> {
		return f != null ? f : def;
	};

	/** Simple formatter for strings */
	public static final QuickFormatter<String> string = new SimpleFormatter<String>() {
		@Override
		public TypeToken<String> getFormatType() {
			return TypeToken.of(String.class);
		}

		@Override
		public TypeToken<String> getParseType() {
			return TypeToken.of(String.class);
		}

		@Override
		public String format(String value) {
			return value;
		}

		@Override
		public String parse(String text) throws QuickParseException {
			return text;
		}

		@Override
		public String toString() {
			return "formats.string";
		}
	};

	/** Formats integers (type long) */
	public static final QuickFormatter<Number> number = new SimpleFormatter<Number>() {
		@Override
		public TypeToken<Number> getFormatType() {
			return TypeToken.of(Number.class);
		}

		@Override
		public TypeToken<Number> getParseType() {
			return TypeToken.of(Number.class);
		}

		@Override
		public String format(Number value) {
			return value.toString();
		}

		@Override
		public Number parse(String text) throws QuickParseException {
			try {
				if (text.indexOf('.') >= 0)
					return Double.valueOf(text);
				else
					return Long.valueOf(text);
			} catch(NumberFormatException e) {
				throw new QuickParseException(e, -1, -1);
			}
		}

		@Override
		public String toString() {
			return "formats.number";
		}
	};

	/** Formats integers */
	public static final AdjustableFormatter<Integer> integer = new SimpleFormatter.SimpleAdjustableFormatter<Integer>() {
		@Override
		public TypeToken<Integer> getFormatType() {
			return TypeToken.of(Integer.class);
		}

		@Override
		public TypeToken<Integer> getParseType() {
			return TypeToken.of(Integer.class);
		}

		@Override
		public String format(Integer value) {
			return value.toString();
		}

		@Override
		public Integer parse(String text) throws QuickParseException {
			try {
				return Integer.valueOf(text);
			} catch (NumberFormatException e) {
				throw new QuickParseException(e, -1, -1);
			}
		}

		@Override
		public Integer increment(Integer value) {
			if (value.intValue() == Integer.MAX_VALUE)
				throw new IllegalStateException(isIncrementEnabled(value));
			else
				return value + 1;
		}

		@Override
		public String isIncrementEnabled(Integer value) {
			if (value.intValue() == Integer.MAX_VALUE)
				return value + " is the maximum integer value";
			else
				return null;
		}

		@Override
		public Integer decrement(Integer value) {
			if (value.intValue() == Integer.MIN_VALUE)
				throw new IllegalStateException(isDecrementEnabled(value));
			else
				return value - 1;
		}

		@Override
		public String isDecrementEnabled(Integer value) {
			if (value.intValue() == Integer.MIN_VALUE)
				return value + " is the minimum integer value";
			else
				return null;
		}

		@Override
		public String toString() {
			return "formats.integer";
		}
	};

	/** Formats integers, allowing them to be incremented variably depending on the location of the cursor */
	public static final AdjustableFormatter.Factory<Integer> advancedInteger = new AdjustableFormatter.Factory<Integer>() {
		@Override
		public AdjustableFormatter<Integer> create(QuickDocumentModel doc, Observable<?> until) {
			return new AdvancedIntFormatter(doc);
		}

	};

	private static class AdvancedIntFormatter implements SimpleFormatter.SimpleAdjustableFormatter<Integer> {
		private final QuickDocumentModel theDoc;

		AdvancedIntFormatter(QuickDocumentModel doc) {
			theDoc = doc;
		}

		@Override
		public TypeToken<Integer> getFormatType() {
			return TypeToken.of(Integer.class);
		}

		@Override
		public TypeToken<Integer> getParseType() {
			return TypeToken.of(Integer.class);
		}

		@Override
		public String format(Integer value) {
			return value.toString();
		}

		@Override
		public Integer parse(String text) throws QuickParseException {
			try {
				return Integer.valueOf(text);
			} catch (NumberFormatException e) {
				throw new QuickParseException(e, -1, -1);
			}
		}

		@Override
		public Integer increment(Integer value) {
			String enabled = isIncrementEnabled(value);
			if (enabled != null)
				throw new IllegalStateException(enabled);
			return value.intValue() + getAdjustment();
		}

		int getAdjustment() {
			int cursor = theDoc instanceof SelectableDocumentModel ? ((SelectableDocumentModel) theDoc).getCursor() : theDoc.length();
			int add = 1;
			for (int i = 0; i < theDoc.length() - cursor - 1; i++) {
				add *= 10;
				if (add < 0)
					return -1;
			}
			return add;
		}

		@Override
		public String isIncrementEnabled(Integer value) {
			int adjust = getAdjustment();
			int cursor = theDoc instanceof SelectableDocumentModel ? ((SelectableDocumentModel) theDoc).getCursor() : theDoc.length();
			int place = theDoc.length() - cursor - 1;
			String suffix;
			switch (place % 10) {
			case 1:
				suffix = "st";
				break;
			case 2:
				suffix = "nd";
				break;
			default:
				suffix = "th";
			}
			if (adjust < 0) {
				return "Integer values cannot be increased in the " + place + suffix + " place";
			}
			if (value.intValue() > 0 && value.intValue() + adjust < 0)
				return "This value is too great to be increased in the " + place + suffix + " place";
			else
				return null;
		}

		@Override
		public Integer decrement(Integer value) {
			String enabled = isDecrementEnabled(value);
			if (enabled != null)
				throw new IllegalStateException(enabled);
			return value.intValue() - getAdjustment();
		}

		@Override
		public String isDecrementEnabled(Integer value) {
			int adjust = getAdjustment();
			int cursor = theDoc instanceof SelectableDocumentModel ? ((SelectableDocumentModel) theDoc).getCursor() : theDoc.length();
			int place = theDoc.length() - cursor - 1;
			String suffix;
			switch (place % 10) {
			case 1:
				suffix = "st";
				break;
			case 2:
				suffix = "nd";
				break;
			default:
				suffix = "th";
			}
			if (adjust < 0) {
				return "Integer values cannot be decreased in the " + place + suffix + " place";
			}
			if (value.intValue() > 0 && value.intValue() + adjust < 0)
				return "This value is too low to be decreased in the " + place + suffix + " place";
			else
				return null;
		}

		@Override
		public String toString() {
			return "formats.advanced-integer";
		}
	}

	/** Formats and parses colors using the {@link Colors} class */
	public static final QuickFormatter<Color> color = new SimpleFormatter<Color>() {
		@Override
		public TypeToken<Color> getFormatType() {
			return TypeToken.of(Color.class);
		}

		@Override
		public TypeToken<Color> getParseType() {
			return TypeToken.of(Color.class);
		}

		@Override
		public String format(Color value) {
			return value == null ? "" : Colors.toString(value);
		}

		@Override
		public Color parse(String text) throws QuickParseException {
			Color ret;
			try {
				ret = Colors.parseColor(text);
			} catch(QuickException e) {
				throw new QuickParseException(e.getMessage(), e, -1, -1);
			}
			return ret;
		}

		@Override
		public String toString() {
			return "formats.color";
		}
	};
}
