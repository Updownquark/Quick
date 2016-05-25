package org.quick.core;

import java.awt.Color;
import java.io.IOException;
import java.net.URL;
import java.time.Period;
import java.util.*;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.qommons.TypeUtil;
import org.quick.core.eval.impl.ObservableEvaluator;
import org.quick.core.eval.impl.ParsedColor;
import org.quick.core.parser.QuickParseException;
import org.quick.core.style.Colors;
import org.quick.util.QuickUtils;

import com.google.common.reflect.TypeToken;
import com.sun.org.apache.xpath.internal.operations.Variable;

/**
 * Represents a property in MUIS
 *
 * @param <T> The type of values that may be associated with the property
 */
public abstract class QuickProperty<T> {
	public static class TypeMapping<F, T> {
		final TypeToken<F> from;

		final TypeToken<T> to;

		final Function<? super F, ? extends T> map;

		TypeMapping(TypeToken<F> from, TypeToken<T> to, Function<? super F, ? extends T> map) {
			this.from = from;
			this.to = to;
			this.map = map;
		}
	}

	public static class Unit<F, T> extends TypeMapping<F, T> {
		public final String name;

		public Unit(String name, TypeToken<F> from, TypeToken<T> to, Function<? super F, ? extends T> operator) {
			super(from, to, operator);
			this.name = name;
		}
	}

	/**
	 * A property type understands how to produce items of a certain type from parseable strings and other types
	 *
	 * @param <T> The type of value that this property type produces TODO Get rid of all the V types
	 */
	public static final class PropertyType<T> {
		private final TypeToken<T> theType;
		private final List<TypeMapping<?, T>> theMappings;
		private final List<Function<String, ?>> theValueSuppliers;
		private final List<Unit<?, ?>> theUnits;
		private final Function<? super T, String> thePrinter;

		private PropertyType(TypeToken<T> type, List<TypeMapping<?, T>> mappings, List<Function<String, ?>> valueSuppliers,
			List<Unit<?, ?>> units, Function<? super T, String> printer) {
			theType = type;
			theMappings = Collections.unmodifiableList(new ArrayList<>(mappings));
			theValueSuppliers = Collections.unmodifiableList(new ArrayList<>(valueSuppliers));
			theUnits = Collections.unmodifiableList(new ArrayList<>(units));
			thePrinter = printer;
		}

		/** @return The java type that this property type parses strings into instances of */
		public TypeToken<T> getType(){
			return theType;
		}

		/**
		 * @param type The type to check
		 * @return Whether objects of the given type can be converted to items of this property's type
		 */
		public boolean canAccept(TypeToken<?> type) {
			if(theType.isAssignableFrom(type))
				return true;
			for(TypeMapping<?, T> mapping : theMappings)
				if(mapping.from.isAssignableFrom(type))
					return true;
			return false;
		}

		/**
		 * Casts any object to an appropriate value of this type, or returns null if the given value cannot be interpreted as an instance of
		 * this property's type. This method may choose to convert liberally by creating new instances of this type corresponding to
		 * instances of other types, or it may choose to be conservative, only returning non-null for instances of this type.
		 *
		 * @param <X> The type of the value to be cast
		 * @param <V> The type of value cast by this property type
		 * @param type The run-time type of the value to cast
		 * @param value The value to cast
		 * @return An instance of this type whose value matches the parameter in some sense, or null if the conversion cannot be made
		 */
		public <X, V extends T> V cast(TypeToken<X> type, X value) {
			V cast = null;
			if(theType.isAssignableFrom(type))
				cast = (V) value;
			boolean mappingFound = false;
			for(TypeMapping<?, T> mapping : theMappings)
				if(mapping.from.isAssignableFrom(type)) {
					mappingFound = true;
					cast = ((TypeMapping<? super X, V>) mapping).map.apply(value);
				}
			if(!mappingFound)
				return null;
			return cast;
		}

		public static <T> Builder<T> build(TypeToken<T> type) {
			return new Builder(type);
		}

		public static class Builder<T> {
			private final TypeToken<T> theType;
			private final List<TypeMapping<?, T>> theMappings;
			private final List<Function<String, ?>> theValueSuppliers;
			private final List<Unit<?, ?>> theUnits;
			private Function<? super T, String> thePrinter;

			private Builder(TypeToken<T> type) {
				theType = type;
				theMappings = new ArrayList<>();
				theValueSuppliers = new ArrayList<>();
				theUnits = new ArrayList<>();
			}

			public <F> Builder<T> map(TypeToken<F> from, Function<? super F, ? extends T> map) {
				theMappings.add(new TypeMapping<>(from, theType, map));
				return this;
			}

			public Builder<T> withValues(Function<String, ?> values) {
				theValueSuppliers.add(values);
				return this;
			}

			public <F, T2> Builder<T> withUnit(String name, TypeToken<F> from, TypeToken<T2> to,
				Function<? super F, ? extends T2> operator) {
				theUnits.add(new Unit<>(name, from, to, operator));
				return this;
			}

			public Builder<T> withToString(Function<? super T, String> toString) {
				thePrinter = toString;
				return this;
			}

			public PropertyType<T> build() {
				return new PropertyType<>(theType, theMappings, theValueSuppliers, theUnits, thePrinter);
			}
		}
	}

	/**
	 * A property validator places constraints on the value of a property
	 *
	 * @param <T> The type of value that this validator can validate
	 */
	public static interface PropertyValidator<T> {
		/**
		 * @param value The value to check
		 * @return Whether the value is valid by this validator's constraints
		 */
		boolean isValid(T value);

		/**
		 * @param value The value to check
		 * @throws QuickException If the value was not valid by this validator's constraints. The message in this exception will be as
		 *             descriptive and user-friendly as possible.
		 */
		void assertValid(T value) throws QuickException;
	}

	/**
	 * An abstract class to help {@link QuickProperty.PropertyType}s and {@link QuickProperty.PropertyValidator}s generate better error
	 * messages
	 *
	 * @param <T> The type of property that this helper is for
	 */
	public static abstract class PropertyHelper<T> {
		private QuickProperty<T> theProperty;

		/** @return The property that this helper is for */
		public QuickProperty<T> getProperty() {
			return theProperty;
		}

		void setProperty(QuickProperty<T> property) {
			theProperty = property;
		}

		/** @return The concatenation of this helper's property's type name and its name. Useful for error messages. */
		protected String propName() {
			if(theProperty == null)
				return "";
			return theProperty.getPropertyTypeName() + " " + theProperty.getName();
		}
	}

	/**
	 * A property type that is helped by {@link QuickProperty.PropertyHelper}
	 *
	 * @param <T> The type of property that this type is for
	 */
	public static abstract class AbstractPropertyType<T> extends PropertyHelper<T> implements PropertyType<T> {
	}

	/**
	 * A property type that is helped by {@link QuickProperty.PropertyHelper}
	 *
	 * @param <T> The type of property that this validator is for
	 */
	public static abstract class AbstractPropertyValidator<T> extends PropertyHelper<T> implements PropertyValidator<T> {
	}

	private static abstract class AbstractPrintablePropertyType<T> extends AbstractPropertyType<T> implements PrintablePropertyType<T> {
	}

	private static final TypeToken<String> STRING_TYPE = TypeToken.of(String.class);
	private static final TypeToken<CharSequence> CHAR_SEQ_TYPE = TypeToken.of(CharSequence.class);

	/**
	 * Parses MUIS properties using a PrismsParser
	 *
	 * @param <T> The type of the property
	 */
	public static class PrismsParsedPropertyType<T> extends AbstractPropertyType<T> {
		private final TypeToken<T> theType;
		private final boolean evalAsType;

		/** @param type The type of the property */
		public PrismsParsedPropertyType(TypeToken<T> type) {
			this(type, false);
		}

		/**
		 * @param type The type of the property
		 * @param asType Whether to parse this property type's values as types or instances
		 */
		public PrismsParsedPropertyType(TypeToken<T> type, boolean asType) {
			theType = type;
			evalAsType = asType;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		/** @return Whether this property type evaluates its values as types */
		public boolean asType() {
			return evalAsType;
		}

		@Override
		public ObservableValue<? extends T> parse(QuickParseEnv env, String value) throws QuickException {
			org.quick.core.parser.DefaultModelValueReferenceParser parser;
			try {
				parser = new org.quick.core.parser.DefaultModelValueReferenceParser(env.getValueParser(), null) {
					@Override
					protected void applyModification() {
						super.applyModification();
						try {
							mutate(getParser(), getEvaluator(), getEvaluationEnvironment());
						} catch(QuickException e) {
							throw new org.quick.util.ExceptionWrapper(e);
						}
					}
				};
			} catch(org.quick.util.ExceptionWrapper e) {
				throw (QuickException) e.getCause();
			}

			ObservableValue<?> ret = parser.parse(value, evalAsType);
			if(theType.equals(ret.getType()))
				return (ObservableValue<? extends T>) ret;
			else if(canCast(ret.getType()))
				return ret.mapV(theType, v -> cast((TypeToken<Object>) ret.getType(), v), true);
			else if(new TypeToken<ObservableValue<?>>() {
			}.isAssignableFrom(ret.getType())) {
				ObservableValue<?> contained=(ObservableValue<?>) ret.get();
				if(theType.equals(contained.getType()))
					return ObservableValue.flatten(theType, (ObservableValue<? extends ObservableValue<? extends T>>) ret);
				else if(canCast(contained.getType()))
					return ObservableValue
						.flatten((TypeToken<Object>) contained.getType(), (ObservableValue<? extends ObservableValue<?>>) ret)
						.mapV(v -> cast((TypeToken<Object>) contained.getType(), v));
			}
			throw new QuickException("The given value of type " + ret.getType() + " is not compatible with this property's type (" + theType
				+ ")");
		}

		/**
		 * May be used by subclasses to modify the prisms parsing types
		 *
		 * @param parser The parser
		 * @param eval The evaluator
		 * @param env The evaluation environment
		 * @throws QuickException If an error occurs mutating the parsing types
		 */
		protected void mutate(PrismsParser parser, ObservableEvaluator eval, EvaluationEnvironment env) throws QuickException {
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			return theType.isAssignableFrom(type);
		}

		@Override
		public <X, V extends T> V cast(TypeToken<X> type, X value) {
			if(value == null)
				return null;
			if(theType.isAssignableFrom(type))
				return (V) theType.wrap().getRawType().cast(value);
			return null;
		}

		@Override
		public boolean equals(Object o) {
			return o != null && o.getClass() == getClass() && ((PrismsParsedPropertyType<?>) o).getType().equals(theType);
		}

		@Override
		public int hashCode() {
			return theType.hashCode();
		}

		@Override
		public String toString() {
			return theType.toString();
		}
	}

	/**
	 * @param env The parsing environment
	 * @param text The text to parse
	 * @param asType Whether to evaluate the result as a type or an instance
	 * @return An observable value, if the text is explicitly marked to be parsed as such. Null otherwise.
	 * @throws QuickParseException If an exception occurs parsing the explicitly-marked observable from the text.
	 */
	public static ObservableValue<?> parseExplicitObservable(QuickParseEnv env, String text, boolean asType) throws QuickParseException {
		if(text.startsWith("${") && text.endsWith("}"))
			return env.getValueParser().parse(text.substring(2, text.length() - 1), asType);
		else
			return null;
	}

	private final String theName;
	private final PropertyType<T> theType;
	private final PropertyValidator<T> theValidator;

	/**
	 * @param name The name for the property
	 * @param type The type of the property
	 * @param validator The validator for the property
	 */
	protected QuickProperty(String name, PropertyType<T> type, PropertyValidator<T> validator) {
		theName = name;
		theType = type;
		theValidator = validator;
		if(theType instanceof PropertyHelper)
			((PropertyHelper<T>) theType).setProperty(this);
		if(theValidator instanceof PropertyHelper)
			((PropertyHelper<T>) theValidator).setProperty(this);
	}

	/**
	 * Creates a MUIS property without a validator or a path acceptor
	 *
	 * @param name The name for the property
	 * @param type The type of the property
	 */
	protected QuickProperty(String name, PropertyType<T> type) {
		this(name, type, null);
	}

	/** @return This property's name */
	public String getName() {
		return theName;
	}

	/** @return This property's type */
	public PropertyType<T> getType() {
		return theType;
	}

	/** @return The validator for this property. May be null. */
	public PropertyValidator<T> getValidator() {
		return theValidator;
	}

	/** @return A string describing this sub-type */
	public abstract String getPropertyTypeName();

	@Override
	public String toString() {
		return getPropertyTypeName() + " " + theName + "(" + theType + ")";
	}

	@Override
	public boolean equals(Object o) {
		if(o == null || !o.getClass().equals(getClass()))
			return false;
		QuickProperty<?> prop = (QuickProperty<?>) o;
		return prop.theName.equals(theName) && prop.theType.equals(theType);
	}

	@Override
	public int hashCode() {
		return theName.hashCode() * 13 + theType.hashCode() * 7;
	}

	/** A string property type--this type validates anything */
	public static final AbstractPropertyType<String> stringAttr = new AbstractPrintablePropertyType<String>() {
		@Override
		public ObservableValue<String> parse(QuickParseEnv env, String value) throws QuickException {
			ObservableValue<?> ret = parseExplicitObservable(env, value, false);
			if(ret != null) {
				if(STRING_TYPE.isAssignableFrom(ret.getType())) {
				} else if(CHAR_SEQ_TYPE.isAssignableFrom(ret.getType())) {
					ret = ((ObservableValue<? extends CharSequence>) ret).mapV(seq -> {
						return seq.toString();
					});
				} else
					throw new QuickException("Model value " + value + " is not of type string");
			} else
				ret = ObservableValue.constant(value);
			return (ObservableValue<String>) ret;
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			return CHAR_SEQ_TYPE.isAssignableFrom(type);
		}

		@Override
		public <X, V extends String> V cast(TypeToken<X> type, X value) {
			if(value instanceof String)
				return (V) value;
			else if(value instanceof CharSequence)
				return (V) value.toString();
			else
				return null;
		}

		@Override
		public TypeToken<String> getType() {
			return STRING_TYPE;
		}

		@Override
		public String toString(String value) {
			return value;
		}

		@Override
		public String toString() {
			return "string";
		}
	};

	/** A boolean property type--values must be either true or false */
	public static final AbstractPropertyType<Boolean> boolAttr = new PrismsParsedPropertyType<>(TypeToken.of(Boolean.class));

	/** An integer property type--values must be valid integers */
	public static final AbstractPropertyType<Long> intAttr = new PrismsParsedPropertyType<Long>(TypeToken.of(Long.class)) {
		@Override
		public String toString() {
			return "int";
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			return TypeUtil.isIntMathable(type.getRawType());
		}

		@Override
		public <X, V extends Long> V cast(TypeToken<X> type, X value) {
			if(value instanceof Long)
				return (V) value;
			else if(value instanceof Number)
				return (V) Long.valueOf(((Number) value).longValue());
			else if(value instanceof Character)
				return (V) Long.valueOf(((Character) value).charValue());
			else
				return null;
		}
	};

	/** A floating-point property type--values must be valid real numbers */
	public static final AbstractPropertyType<Double> floatAttr = new PrismsParsedPropertyType<Double>(TypeToken.of(Double.class)) {
		@Override
		public String toString() {
			return "float";
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			return TypeUtil.isIntMathable(type.getRawType());
		}

		@Override
		public <X, V extends Double> V cast(TypeToken<X> type, X value) {
			if(value instanceof Double)
				return (V) value;
			else if(value instanceof Number)
				return (V) Double.valueOf(((Number) value).doubleValue());
			else if(value instanceof Character)
				return (V) Double.valueOf(((Character) value).charValue());
			else
				return null;
		}
	};

	/** Represents a time amount. The value is interpreted in milliseconds. */
	public static final AbstractPropertyType<Long> timeAttr = new AbstractPropertyType<Long>() {
		private static final TypeToken<Long> LONG_TYPE = TypeToken.of(Long.class);

		private static final TypeToken<Date> DATE_TYPE = TypeToken.of(Date.class);

		private static final TypeToken<Period> PERIOD_TYPE = TypeToken.of(Period.class);
		@Override
		public TypeToken<Long> getType() {
			return TypeToken.of(Long.class);
		}

		@Override
		public ObservableValue<Long> parse(QuickParseEnv env, String value) throws QuickException {
			ObservableValue<?> retObs = parseExplicitObservable(env, value, false);
			if(retObs != null) {
				if(LONG_TYPE.isAssignableFrom(retObs.getType())) {
				} else if(DATE_TYPE.isAssignableFrom(retObs.getType())) {
					retObs = ((ObservableValue<? extends Date>) retObs).mapV(date -> {
						return date.getTime();
					});
				} else if(PERIOD_TYPE.isAssignableFrom(retObs.getType())) {
					retObs = ((ObservableValue<? extends Period>) retObs).mapV(date -> {
						return date.get(java.time.temporal.ChronoUnit.MILLIS);
					});
				} else
					throw new QuickException("Model value " + value + " is not of type long, Date, or Period");
				return (ObservableValue<Long>) retObs;
			}
			long ret = 0;
			for(String s : value.trim().split("\\s")) {
				char unit = s.charAt(s.length() - 1);
				if(unit >= '0' && unit <= '9') {
					try {
						ret += Long.parseLong(s);
					} catch(NumberFormatException e) {
						throw new QuickException("Illegal time value: " + value, e);
					}
				} else {
					s = s.substring(0, s.length() - 1);
					if(s.length() == 0)
						throw new QuickException("No value in front of unit for time value: " + value);
					long t;
					try {
						t = Long.parseLong(s) * 1000;
					} catch(NumberFormatException e) {
						throw new QuickException("Illegal time value: " + value, e);
					}
					if(unit == 's') {
						ret += t * 1000;
					} else if(unit == 'm') {
						ret += t * 60000;
					} else if(unit == 'h') {
						ret += t * 60 * 60 * 1000;
					} else if(unit == 'd') {
						ret += t * 24 * 60 * 60 * 1000;
					} else
						throw new QuickException("Unrecognized unit '" + unit + "' in time value: " + value);
				}
			}
			return ObservableValue.constant(ret);
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			return TypeUtil.isMathable(type.getRawType()) && type.wrap().getRawType() != Character.class;
		}

		@Override
		public <X, V extends Long> V cast(TypeToken<X> type, X value) {
			if(value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte)
				return (V) Long.valueOf(((Number) value).longValue());
			else if(value instanceof Double || value instanceof Float)
				return (V) Long.valueOf(Math.round(((Number) value).floatValue() * 1000));
			else
				return null;
		}

		@Override
		public String toString() {
			return "time";
		}
	};

	public static class ColorValueSupply implements Function<String, Color> {
		@Override
		public Color apply(String str) {
			try {
				return Colors.parseIfColor(str);
			} catch (QuickException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
		}
	}

	private static class ColorPropertyType extends PrismsParsedPropertyType<Color> implements PrintablePropertyType<Color> {
		private final PrismsConfig theColorOp;

		private final List<ParsedFunctionDeclaration> theFunctions;

		ColorPropertyType() {
			super(TypeToken.of(Color.class));
			theColorOp = new prisms.arch.MutableConfig("entity");
			((prisms.arch.MutableConfig) theColorOp).set("name", "color").set("order", "1").set("impl", ParsedColor.class.getName()) //
				.getOrCreate("select") //
				/**/.getOrCreate("option") //
				/*		*/.getOrCreate("literal").set("storeAs", "rgb").setValue("#") //
				/*		*/.getParent() //
				/**/.getParent().getOrCreate("option") //
				/*		*/.getOrCreate("literal").set("storeAs", "hsb").setValue("$") //
				/*		*/.getParent() //
				/**/.getParent() //
				.getParent().getOrCreate("charset").set("storeAs", "value").set("pattern", "[0-9][A-F][a-f]{6}");

			theFunctions = new java.util.ArrayList<>();
			// Use the default prisms.lang Grammar.xml to implement some setup declarations to prepare the environment
			PrismsParser setupParser = new PrismsParser();
			try {
				setupParser.configure(PrismsParser.class.getResource("Grammar.xml"));
			} catch(IOException e) {
				throw new IllegalStateException("Could not configure style sheet setup parser", e);
			}

			ArrayList<String> commands = new ArrayList<>();
			// Add constants and functions like rgb(r, g, b) here
			commands
				.add("java.awt.Color rgb(int r, int g, int b){return " + org.quick.core.style.Colors.class.getName() + ".rgb(r, g, b);}");
			commands
				.add("java.awt.Color hsb(int h, int s, int b){return " + org.quick.core.style.Colors.class.getName() + ".hsb(h, s, b);}");
			// TODO Add more constants and functions
			// TODO Are these defined by default? Maybe we don't need them here.
			for(String command : commands) {
				try {
					theFunctions.add((ParsedFunctionDeclaration) setupParser.parseStructures(new prisms.lang.ParseStructRoot(command),
						setupParser.parseMatches(command))[0]);
				} catch(ParseException e) {
					System.err.println("Could not compile color property evaluation functions: " + command);
					e.printStackTrace();
				}
			}
		}

		@Override
		protected void mutate(PrismsParser parser, ObservableEvaluator eval, EvaluationEnvironment env) throws QuickException {
			parser.insertOperator(theColorOp);
			eval.addEvaluator(ParsedColor.class, new org.quick.core.eval.impl.ColorEvaluator());
			eval.addEvaluator(prisms.lang.types.ParsedDeclaration.class, new prisms.lang.eval.DeclarationEvaluator());
			eval.addEvaluator(ParsedFunctionDeclaration.class, new prisms.lang.eval.FunctionDeclarationEvaluator());
			eval.addEvaluator(prisms.lang.types.ParsedStatementBlock.class, new prisms.lang.eval.StatementBlockEvaluator());
			eval.addEvaluator(prisms.lang.types.ParsedReturn.class, new prisms.lang.eval.ReturnEvaluator());

			for(ParsedFunctionDeclaration func : theFunctions)
				env.declareFunction(func);

			TypeToken<Color> colorType = TypeToken.of(Color.class);
			if(env instanceof DefaultEvaluationEnvironment) {
				((DefaultEvaluationEnvironment) env).addVariableSource(new VariableSource() {
					@Override
					public Variable [] getDeclaredVariables() {
						java.util.Collection<String> names = Colors.getColorNames();
						Variable [] ret = new Variable[names.size()];
						int i = 0;
						for(String name : names) {
							ret[i] = new VariableImpl(colorType, name, true);
							try {
								((VariableImpl) ret[i]).setValue(Colors.parseColor(name));
							} catch(QuickException e) {
							}
							i++;
						}
						return ret;
					}

					@Override
					public Variable getDeclaredVariable(String name) {
						Color value;
						try {
							value = Colors.parseIfColor(name);
						} catch(QuickException e) {
							return null;
						}
						if(value != null) {
							VariableImpl ret = new VariableImpl(colorType, name, true);
							ret.setValue(value);
							return ret;
						}
						return null;
					}
				});
			} else {
				for(String colorName : Colors.getColorNames()) {
					try {
						env.declareVariable(colorName, colorType, true, null, 0);
						env.setVariable(colorName, Colors.parseColor(colorName), null, 0);
					} catch(EvaluationException | QuickException e) {

					}
				}
			}
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			if(super.canCast(type))
				return true;
			if(STRING_TYPE.isAssignableFrom(type))
				return true;
			return false;
		}

		@Override
		public <X, V extends Color> V cast(TypeToken<X> type, X value) {
			if(value instanceof String)
				try {
					return (V) Colors.parseIfColor((String) value);
				} catch(QuickException e) {
					return null;
				}
			return super.cast(type, value);
		}

		@Override
		public String toString(Color value) {
			return org.quick.core.style.Colors.toString(value);
		}

		@Override
		public String toString() {
			return "color";
		}
	}

	/** A color property type--values must be parse to colors via {@link org.quick.core.style.Colors#parseColor(String)} */
	public static final AbstractPropertyType<Color> colorAttr = new ColorPropertyType();

	/**
	 * A resource property--values may be:
	 * <ul>
	 * <li>namespace:tag, where <code>tag</code> maps to a resource in the <code>namespace</code> toolkit</li>
	 * <li>A relative path to a resource that may be resolved from the element's toolkit. A <code>namespace:</code> prefix may be used to
	 * specify a different toolkit</li>
	 * <li>An absolute URL. Permissions will be checked before resources at any external URLs are retrieved. TODO cite specific permission.</li>
	 * </ul>
	 */
	public static final AbstractPropertyType<URL> resourceAttr = new AbstractPropertyType<java.net.URL>() {
		private static final TypeToken<URL> URL_TYPE = TypeToken.of(URL.class);
		@Override
		public ObservableValue<URL> parse(QuickParseEnv env, String value) throws QuickException {
			ObservableValue<?> ret = parseExplicitObservable(env, value, false);
			if(ret != null) {
				if(TypeToken.of(ObservableValue.class).isAssignableFrom(ret.getType()))
					ret = ObservableValue.flatten(null, (ObservableValue<? extends ObservableValue<?>>) ret);
				if(URL_TYPE.isAssignableFrom(ret.getType())) {
				} else if(CHAR_SEQ_TYPE.isAssignableFrom(ret.getType())) {
					ret = ((ObservableValue<? extends CharSequence>) ret).mapV(seq -> {
						try {
							return new URL(seq.toString());
						} catch(java.net.MalformedURLException e) {
							throw new IllegalArgumentException("Malformed URL", e);
						}
					});
				} else
					throw new QuickException("Model value " + value + " is not of type string or URL");
			}
			int sepIdx = value.indexOf(':');
			String namespace = sepIdx < 0 ? null : value.substring(0, sepIdx);
			String content = sepIdx < 0 ? value : value.substring(sepIdx + 1);
			QuickToolkit toolkit = env.cv().getToolkit(namespace);
			if(toolkit == null)
				try {
					return ObservableValue.constant(new URL(value));
				} catch(java.net.MalformedURLException e) {
					throw new QuickException(propName() + ": Resource property is not a valid URL: \"" + value + "\"", e);
				}
			ret = parseExplicitObservable(env, content, false);
			if(ret != null) {
				if(TypeToken.of(ObservableValue.class).isAssignableFrom(ret.getType()))
					ret = ObservableValue.flatten(null, (ObservableValue<? extends ObservableValue<?>>) ret);
				if(CHAR_SEQ_TYPE.isAssignableFrom(ret.getType())) {
					return ((ObservableValue<? extends CharSequence>) ret).mapV(seq -> {
						try {
							return getMappedResource(toolkit, seq.toString());
						} catch(Exception e) {
							throw new IllegalArgumentException(e);
						}
					});
				} else
					throw new QuickException("Model value " + content + " is not of type string");
			}
			return ObservableValue.constant(getMappedResource(toolkit, content));
		}

		private URL getMappedResource(QuickToolkit toolkit, String resource) throws QuickException {
			ResourceMapping mapping = toolkit.getMappedResource(resource);
			if(mapping == null)
				throw new QuickException(propName() + ": Resource property must map to a declared resource: \"" + resource
					+ "\" in toolkit " + toolkit.getName() + " or one of its dependencies");
			if(mapping.getLocation().contains(":"))
				try {
					return new URL(mapping.getLocation());
				} catch(java.net.MalformedURLException e) {
					throw new QuickException(propName() + ": Resource property maps to an invalid URL \"" + mapping.getLocation()
						+ "\" in toolkit " + mapping.getOwner().getName() + ": \"" + resource + "\"");
				}
			try {
				return QuickUtils.resolveURL(mapping.getOwner().getURI(), mapping.getLocation());
			} catch(QuickException e) {
				throw new QuickException(propName() + ": Resource property maps to a resource (" + mapping.getLocation()
					+ ") that cannot be resolved with respect to toolkit \"" + mapping.getOwner().getName() + "\"'s URL: \"" + resource
					+ "\"");
			}
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			return URL_TYPE.isAssignableFrom(type);
		}

		@Override
		public <X, V extends URL> V cast(TypeToken<X> type, X value) {
			if(value instanceof URL)
				return (V) value;
			else
				return null;
		}

		@Override
		public TypeToken<URL> getType() {
			return URL_TYPE;
		}

		@Override
		public boolean equals(Object o) {
			return o != null && o.getClass() == getClass();
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public String toString() {
			return "url";
		}
	};

	/**
	 * A MUIS-type property type--values must be valid types mapped under MUIS
	 *
	 * @param <T> The subtype that the value must map to
	 */
	public static class QuickTypeProperty<T> extends PrismsParsedPropertyType<Class<? extends T>> {
		/** @param aType The subtype that the value must map to */
		public QuickTypeProperty(Class<T> aType) {
			super((TypeToken<Class<? extends T>>) TypeToken.of(aType), true);
		}
	}

	/**
	 * An property type that creates actual instances of a specified type
	 *
	 * @param <T> The type of value to create
	 */
	public static final class QuickTypeInstanceProperty<T> extends PrismsParsedPropertyType<T> {
		/** @param aType The subtype that the value must map to */
		public QuickTypeInstanceProperty(Class<? extends T> aType) {
			super((TypeToken<T>) TypeToken.of(aType), false);
		}

		@Override
		public boolean canCast(TypeToken<?> type) {
			if(getType().isAssignableFrom(type))
				return true;
			if(TypeToken.of(Class.class).isAssignableFrom(type)
				&& getType().isAssignableFrom(type.resolveType(Class.class.getTypeParameters()[0])))
				return true;
			return false;
		}

		@Override
		public <X, V extends T> V cast(TypeToken<X> type, X value) {
			if(value == null) {
				if(type == Type.NULL)
					return null;
				if(type.getRawType() != null)
					try {
						return (V) getType().getRawType().cast(type.getRawType().newInstance());
					} catch(Exception e) {
						throw new IllegalStateException("Could not instantiate type " + type, e);
					}
				else
					throw new IllegalStateException("Could not instantiate type " + type);
			} else if(getType().isAssignableFrom(type))
				return (V) getType().getRawType().cast(value);
			else
				return null;
		}
	}

	/** A QuickTypeProperty for a generic QuickElement */
	public static final QuickTypeProperty<QuickElement> elementTypeProp = new QuickTypeProperty<>(QuickElement.class);

	/**
	 * An enumeration property type--validates elements whose value matches any of the values given in the constructor
	 *
	 * @param <T> The enumeration type
	 */
	public static final class QuickEnumProperty<T extends Enum<T>> extends AbstractPropertyType<T> {
		/** The enumeration that this property represents */
		public final Class<T> enumType;

		/** Whether all values in this enum are unique case-insensitively. If this is true, property values are case-insensitive. */
		public final boolean ciUnique;

		/**
		 * Creates an enumeration property from an enumerated type. The options will be all the type's constants in lower-case.
		 *
		 * @param enumClass The enumerated type
		 */
		public QuickEnumProperty(Class<T> enumClass) {
			enumType = enumClass;
			java.util.HashSet<String> values = new java.util.HashSet<>();
			boolean unique = true;
			for(T value : enumType.getEnumConstants())
				if(!values.add(value.name().toLowerCase())) {
					unique = false;
					break;
				}
			ciUnique = unique;
		}

		@Override
		public ObservableValue<T> parse(QuickParseEnv env, String value) throws QuickException {
			if(value == null)
				return null;
			ObservableValue<?> ret = parseExplicitObservable(env, value, false);
			if(ret != null) {
				if(ret.getType().canAssignTo(enumType)) {
					return (ObservableValue<T>) ret;
				} else if(ret.getType().canAssignTo(String.class)) {
					return ((ObservableValue<String>) ret).mapV(str -> {
						return Enum.valueOf(enumType, str);
					});
				} else
					throw new QuickException("Model value " + value + " is not of type " + enumType.getSimpleName());
			}
			T [] consts = enumType.getEnumConstants();
			for(T e : consts)
				if(ciUnique) {
					if(e.name().equalsIgnoreCase(value))
						return ObservableValue.constant(e);
				} else if(e.name().equals(value))
					return ObservableValue.constant(e);
			throw new QuickException(propName() + ": Value " + value + " does not match any of the allowable values for type "
				+ enumType.getName());
		}

		@Override
		public boolean canCast(Type type) {
			return type.canAssignTo(enumType);
		}

		@Override
		public T cast(Type type, Object value) {
			if(enumType.isInstance(value))
				return (T) value;
			else
				return null;
		}

		@Override
		public Type getType() {
			return TypeToken.of(enumType);
		}

		@Override
		public boolean equals(Object o) {
			return o != null && o.getClass() == getClass() && ((QuickEnumProperty<?>) o).enumType.equals(enumType);
		}

		@Override
		public int hashCode() {
			return getClass().hashCode() * 7 + enumType.hashCode();
		}

		@Override
		public String toString() {
			return enumType.getSimpleName();
		}
	}

	/**
	 * Wraps another property type, allowing the user to specify predefined values in addition to normal, parseable values
	 *
	 * @param <T> The type of the property
	 */
	public static class NamedValuePropertyType<T> extends AbstractPropertyType<T> {
		private final PropertyType<T> theWrapped;

		private final Map<String, T> theNamedValues;

		private final int theHashCode;

		/**
		 * @param wrap The property type to wrap
		 * @param namedValues Name-value pairs of values that can be specified by name
		 */
		public NamedValuePropertyType(PropertyType<T> wrap, Object... namedValues) {
			this(wrap, compileNamedValues(namedValues, wrap.getType()));
		}

		/**
		 * @param wrap The property type to wrap
		 * @param namedValues Name-value pairs of values that can be specified by name
		 */
		public NamedValuePropertyType(PropertyType<T> wrap, Map<String, T> namedValues) {
			theWrapped = wrap;
			checkTypes(namedValues, wrap.getType());
			java.util.HashMap<String, T> copy = new java.util.HashMap<>(namedValues);
			theNamedValues = java.util.Collections.unmodifiableMap(copy);
			theHashCode = theNamedValues.hashCode();
		}

		private static <T> Map<String, T> compileNamedValues(Object [] nv, Type type) {
			if(nv == null || nv.length == 0)
				return null;
			if(nv.length % 2 != 0)
				throw new IllegalArgumentException("Named values must be pairs in the form name, " + type + ", name, " + type + "...");
			java.util.HashMap<String, T> ret = new java.util.HashMap<>();
			for(int i = 0; i < nv.length; i += 2) {
				if(!(nv[i] instanceof String) || !type.isAssignableFrom(nv[i + 1].getClass()))
					throw new IllegalArgumentException("Named values must be pairs in the form name, " + type + ", name, " + type + "...");
				if(ret.containsKey(nv[i]))
					throw new IllegalArgumentException("Named value \"" + nv[i] + "\" specified multiple times");
				ret.put((String) nv[i], (T) type.cast(nv[i + 1]));
			}
			return ret;
		}

		private static <T> void checkTypes(Map<String, T> map, Type type) {
			if(map == null)
				return;
			for(Map.Entry<?, ?> entry : map.entrySet())
				if(!(entry.getKey() instanceof String) || !type.isAssignableFrom(entry.getValue().getClass()))
					throw new IllegalArgumentException("name-value pairs must be typed String, " + type);
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<? extends T> parse(QuickParseEnv env, String value) throws QuickException {
			ObservableValue<?> ret = parseExplicitObservable(env, value, false);
			if(ret != null) {
				if(theWrapped.getType().isAssignable(ret.getType())) {
					return (ObservableValue<T>) ret;
				} else if(ret.getType().canAssignTo(String.class)) {
					return ((ObservableValue<String>) ret).mapV(str -> {
						if(theNamedValues.containsKey(str))
							return theNamedValues.get(str);
						else
							throw new IllegalArgumentException("Model value " + value + " does not match a predefined value.");
					});
				} else
					throw new QuickException("Model value " + value + " is not of type " + theWrapped.getType());
			}
			if(theNamedValues.containsKey(value))
				return ObservableValue.constant(theNamedValues.get(value));
			return theWrapped.parse(env, value);
		}

		@Override
		public boolean canCast(Type type) {
			return theWrapped.canCast(type);
		}

		@Override
		public <V extends T> V cast(Type type, Object value) {
			return theWrapped.cast(type, value);
		}

		@Override
		public boolean equals(Object o) {
			if(o.getClass() != getClass())
				return false;
			NamedValuePropertyType<?> nvpt = (NamedValuePropertyType<?>) o;
			return theWrapped.equals(nvpt.theWrapped) && theNamedValues.equals(nvpt.theNamedValues);
		}

		@Override
		public int hashCode() {
			return theHashCode;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * A simple validator that compares values to minimum and maximum values
	 *
	 * @param <T> The type of value to validate
	 */
	public static class ComparableValidator<T> extends AbstractPropertyValidator<T> {
		private final Comparator<? super T> theCompare;
		private final Comparator<? super T> theInternalCompare;

		private final T theMin;
		private final T theMax;

		private final int theHashCode;

		/**
		 * Shorter constructor for comparable types
		 *
		 * @param min The minimum value for the property
		 * @param max The maximum value for the property
		 */
		public ComparableValidator(T min, T max) {
			this(min, max, null);
		}

		/**
		 * @param min The minimum value for the property
		 * @param max The maximum value for the property
		 * @param compare The comparator to use to compare. May be null if this type is comparable.
		 */
		public ComparableValidator(T min, T max, Comparator<? super T> compare) {
			theCompare = compare;
			theMin = min;
			theMax = max;
			if(compare != null)
				theInternalCompare = compare;
			else {
				if(theMin != null && !(theMin instanceof Comparable))
					throw new IllegalArgumentException("No comparator given, but minimum value is not Comparable");
				if(theMax != null && !(theMax instanceof Comparable))
					throw new IllegalArgumentException("No comparator given, but maximum value is not Comparable");
				theInternalCompare = new Comparator<T>() {
					@Override
					public int compare(T o1, T o2) {
						return ((Comparable<T>) o1).compareTo(o2);
					}
				};
			}

			int hc = 0;
			if(theCompare != null)
				hc += theCompare.getClass().hashCode();
			if(theMin != null)
				hc = hc * 7 + theMin.hashCode();
			if(theMax != null)
				hc = hc * 7 + theMax.hashCode();
			theHashCode = hc;
		}

		/** @return The comparator that is used to validate values. May be null if the type is comparable. */
		public Comparator<? super T> getCompare() {
			return theCompare;
		}

		/** @return The minimum value to validate against */
		public T getMin() {
			return theMin;
		}

		/** @return The maximum value to validate against */
		public T getMax() {
			return theMax;
		}

		@Override
		public boolean isValid(T value) {
			if(theMin != null && theInternalCompare.compare(theMin, value) > 0)
				return false;
			if(theMax != null && theInternalCompare.compare(value, theMax) > 0)
				return false;
			return true;
		}

		@Override
		public void assertValid(T value) throws QuickException {
			if(theMin != null && theInternalCompare.compare(theMin, value) > 0)
				throw new QuickException(propName() + "Value must be at least " + theMin + ": " + value + " is invalid");
			if(theMax != null && theInternalCompare.compare(value, theMax) > 0)
				throw new QuickException(propName() + "Value must be at most " + theMax + ": " + value + " is invalid");
		}

		@Override
		public String toString() {
			if(theMin == null && theMax == null)
				return "Empty comparable validator";
			return "Comparable validator: " + (theMin != null ? theMin + " < " : "") + "value" + (theMax != null ? " < " + theMax : "");
		}

		@Override
		public boolean equals(Object o) {
			if(o == null || o.getClass() != getClass())
				return false;
			ComparableValidator<?> val = (ComparableValidator<?>) o;
			if(theCompare != null) {
				if(val.theCompare == null)
					return false;
				if(theCompare.getClass() != val.theCompare.getClass())
					return false;
			} else if(val.theCompare != null)
				return false;
			if(theMin == null ? val.theMin != null : !theMin.equals(val.theMin))
				return false;
			if(theMax == null ? val.theMax != null : !theMax.equals(val.theMax))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			return theHashCode;
		}
	}
}
