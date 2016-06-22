package org.quick.core.parser;

import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;

import org.observe.ObservableValue;
import org.observe.collect.ObservableOrderedCollection;
import org.quick.core.QuickEnvironment;
import org.quick.core.QuickParseEnv;

import com.google.common.reflect.TypeToken;

import prisms.lang.*;
import prisms.lang.types.*;

public class PrismsPropertyParser extends AbstractPropertyParser {
	private final PrismsParser theParser;

	public PrismsPropertyParser(QuickEnvironment env) {
		super(env);
		theParser = new PrismsParser();
		try {
			theParser.configure(PrismsPropertyParser.class.getResource("MVX.xml"));
		} catch (IOException e) {
			throw new IllegalStateException("Could not configure property parser", e);
		}
	}

	@Override
	protected ObservableValue<?> parseDefaultValue(QuickParseEnv parseEnv, String value) throws QuickParseException {
		ParseMatch[] matches;
		try {
			matches = theParser.parseMatches(value);
		} catch (ParseException e) {
			throw new QuickParseException("Failed to parse " + value, e);
		}
		if (matches.length != 1)
			throw new QuickParseException("One value per property expected: " + value);
		ParsedItem item;
		try {
			item = theParser.parseStructures(new ParseStructRoot(value), matches)[0];
		} catch (ParseException e) {
			throw new QuickParseException("Failed to structure parsed value for " + value, e);
		}
		return evaluate(parseEnv, item);
	}

	private ObservableValue<?> evaluate(QuickParseEnv parseEnv, ParsedItem parsedItem) throws QuickParseException {
		if (parsedItem instanceof ParsedArrayIndex) {
			ParsedArrayIndex pai = (ParsedArrayIndex) parsedItem;
			ObservableValue<?> array = evaluate(parseEnv, pai.getArray());
			TypeToken<?> resultType;
			if (array.getType().isArray()) {
				resultType = array.getType().getComponentType();
			} else if (TypeToken.of(List.class).isAssignableFrom(array.getType())) {
				resultType = array.getType().resolveType(List.class.getTypeParameters()[0]);
			} else if (TypeToken.of(ObservableOrderedCollection.class).isAssignableFrom(array.getType())) {
				resultType = array.getType().resolveType(ObservableOrderedCollection.class.getTypeParameters()[0]);
			} else {
				throw new QuickParseException(
					"array value in " + parsedItem.getMatch().text + " evaluates to type " + array.getType() + ", which is not indexable");
			}
			ObservableValue<?> index = evaluate(parseEnv, pai.getIndex());
			if (TypeToken.of(Long.class).isAssignableFrom(array.getType().wrap())) {
			} else {
				throw new QuickParseException("index value in " + parsedItem.getMatch().text + " evaluates to type " + index.getType()
					+ ", which is not a valid index");
			}
			if (TypeToken.of(ObservableOrderedCollection.class).isAssignableFrom(array.getType())) {
			} else
				return array.combineV((TypeToken<Object>) resultType, index, (BiFunction<Object, Object, Object>) (a, i) -> {
					int idx = ((Number) i).intValue();
					if (TypeToken.of(Object[].class).isAssignableFrom(array.getType())) {
						return ((Object[]) a)[idx];
					} else if (array.getType().isArray()) {
						return java.lang.reflect.Array.get(a, idx);
					} else if (TypeToken.of(List.class).isAssignableFrom(array.getType())) {
						return ((List<?>) a).get(idx);
					} else if (TypeToken.of(ObservableOrderedCollection.class).isAssignableFrom(array.getType())) {
						return ((ObservableOrderedCollection<?>) a).get(idx);
					}
				});
		} else if (parsedItem instanceof ParsedIdentifier) {
		} else if(parsedItem instanceof ParsedMethod){
		} else if (parsedItem instanceof ParsedParenthetic) {
		} else if (parsedItem instanceof ParsedArrayInitializer) {
		} else if (parsedItem instanceof ParsedAssignmentOperator) {
		} else if (parsedItem instanceof ParsedBinaryOp) {
		} else if (parsedItem instanceof ParsedCast) {
		} else if (parsedItem instanceof ParsedConditional) {
		} else if (parsedItem instanceof ParsedConstructor) {
		} else if (parsedItem instanceof ParsedEnhancedForLoop) {
		} else if (parsedItem instanceof ParsedIfStatement) {
		} else if (parsedItem instanceof ParsedInstanceofOp) {
		} else if (parsedItem instanceof ParsedBoolean) {
		} else if (parsedItem instanceof ParsedChar) {
		} else if (parsedItem instanceof ParsedNull) {
		} else if (parsedItem instanceof ParsedNumber) {
		} else if (parsedItem instanceof ParsedString) {
		} else if (parsedItem instanceof ParsedLoop) {
		} else if (parsedItem instanceof ParsedType) {
		} else if (parsedItem instanceof ParsedUnaryOp) {
		} else if (parsedItem instanceof ParsedUnitValue) {
		} else if (parsedItem instanceof ParsedPlaceHolder) {
		} else
			throw new QuickParseException("Unrecognized parsed item type: " + parsedItem.getClass());
	}

	public static class ParsedUnitValue extends ParsedItem {
		private ParsedItem theValue;
		private String theUnit;

		@Override
		public void setup(PrismsParser parser, ParsedItem parent, ParseMatch match) throws ParseException {
			super.setup(parser, parent, match);
			theValue = parser.parseStructures(this, getStored("value"))[0];
			theUnit = getStored("unit").text;
		}

		@Override
		public ParsedItem[] getDependents() {
			return new ParsedItem[] { theValue };
		}

		@Override
		public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
			if (theValue == dependent)
				theValue = toReplace;
			else
				theValue.replace(dependent, toReplace);
		}
	}

	public static class ParsedPlaceHolder extends ParsedItem {
		private String theName;

		@Override
		public void setup(PrismsParser parser, ParsedItem parent, ParseMatch match) throws ParseException {
			super.setup(parser, parent, match);
			theName = getStored("name").text;
		}

		@Override
		public ParsedItem[] getDependents() {
			return new ParsedItem[0];
		}

		@Override
		public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {}
	}
}
