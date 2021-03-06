package org.quick.core.prop.antlr;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.*;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.quick.core.QuickException;
import org.quick.core.QuickParseEnv;
import org.quick.core.model.QuickAppModel;
import org.quick.core.parser.MathUtils;
import org.quick.core.parser.QuickParseException;
import org.quick.core.prop.ExpressionFunction;
import org.quick.core.prop.Unit;
import org.quick.core.prop.antlr.ExpressionTypes.MethodInvocation;
import org.quick.util.QuickUtils;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Evaluates structures parsed by {@link AntlrPropertyParser} into observable values, actions, etc. */
class AntlrPropertyEvaluator {
	private static final Set<String> ASSIGN_BIOPS = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(//
		"=", "+=", "-=", "*=", "/=", "%=", "|=", "&=", "^=", "++", "--")));

	private static <T> ObservableValue<? extends T> evaluateTypeChecked(QuickParseEnv parseEnv, TypeToken<T> type,
		QPPExpression parsedItem, boolean actionAccepted, boolean actionRequired) throws QuickParseException {
		ObservableValue<?> result = evaluateTypeless(parseEnv, type, parsedItem, actionAccepted, actionRequired);

		if (!QuickUtils.isAssignableFrom(type, result.getType()))
			throw new QuickParseException(
				parsedItem + " evaluates to type " + result.getType() + ", which is not compatible with expected type " + type);
		return (ObservableValue<? extends T>) result;
	}

	/**
	 * Evaluates a type
	 *
	 * @param parseEnv The environment in which to resolve the type
	 * @param parsedType The type structure
	 * @return The resolved type
	 * @throws QuickParseException If the type could not be evaluated
	 */
	public static TypeToken<?> evaluateType(QuickParseEnv parseEnv, ExpressionTypes.Type parsedType) throws QuickParseException {
		return evaluateType(parseEnv, parsedType, TypeToken.of(Object.class));
	}

	/**
	 * Evaluates a value
	 *
	 * @param <T> A placeholder to make some of the internal generics easier
	 * @param parseEnv The environment in which to evaluate the value
	 * @param type The expected type of the value
	 * @param parsedItem The parsed value
	 * @param actionAccepted Whether an action is acceptable for the evaluated value
	 * @param actionRequired Whether the evaluated value <b>must</b> be an action
	 * @return The evaluated value
	 * @throws QuickParseException If the value cannot be evaluated
	 */
	public static <T> ObservableValue<?> evaluateTypeless(QuickParseEnv parseEnv, TypeToken<T> type, QPPExpression parsedItem,
		boolean actionAccepted, boolean actionRequired) throws QuickParseException {
		// Sort from easiest to hardest
		// Literals first
		if (parsedItem instanceof ExpressionTypes.NullLiteral) {
			if (actionRequired)
				throw new QuickParseException("null literal cannot be an action");
			return ObservableValue.constant(type, null);
		} else if (parsedItem instanceof ExpressionTypes.Literal) {
			if (actionRequired)
				throw new QuickParseException("literal cannot be an action");
			ExpressionTypes.Literal<?> literal = (ExpressionTypes.Literal<?>) parsedItem;
			return ObservableValue.constant((TypeToken<Object>) literal.getType(), literal.getValue());
			// Now easy operations
		} else if (parsedItem instanceof ExpressionTypes.Parenthetic) {
			return evaluateTypeChecked(parseEnv, type, ((ExpressionTypes.Parenthetic) parsedItem).getContents(), actionAccepted,
				actionRequired);
		} else if (parsedItem instanceof ExpressionTypes.CompoundExpression) {
			if(!actionAccepted || !TypeToken.of(ObservableAction.class).isAssignableFrom(type))
				throw new QuickParseException("Semicolon-separated expressions may only be used with actions");
			TypeToken<?> valueType = type.resolveType(ObservableAction.class.getTypeParameters()[0]);
			if (!valueType.isArray() && !new TypeToken<ObservableAction<?>>() {}.isAssignableFrom(type))
				throw new QuickParseException("Compound action cannot be assigned to non-array type " + valueType);
			ExpressionTypes.CompoundExpression compound=(ExpressionTypes.CompoundExpression) parsedItem;
			TypeToken<?> componentType;
			if (valueType.isArray())
				componentType = new TypeToken<ObservableAction<T>>() {}.where(new TypeParameter<T>() {},
					(TypeToken<T>) valueType.getComponentType());
			else
				componentType = new TypeToken<ObservableAction<?>>() {};
			List<ObservableAction<?>> actions = new ArrayList<>(compound.getChildren().size());
			for(QPPExpression child : compound.getChildren()){
				ObservableValue<?> eval=evaluateTypeless(parseEnv, componentType, child, true, true);
				if (!TypeToken.of(ObservableAction.class).isAssignableFrom(eval.getType()))
					throw new QuickParseException("Compound expressions may only be actions");
				if (!componentType.isAssignableFrom(eval.getType()))
					throw new QuickParseException(
						child + "evaluates to type " + eval.getType() + " and cannot be assigned to type " + componentType);
				if (eval instanceof ObservableValue.ConstantObservableValue)
					actions.add((ObservableAction<?>) eval.get());
				else
					actions.add(ObservableAction.flatten((ObservableValue<ObservableAction<?>>) eval));
			}
			return ObservableValue.constant((TypeToken<ObservableAction<?>>) type, ObservableAction.and(ObservableList
				.constant((TypeToken<ObservableAction<Object>>) componentType, (List<ObservableAction<Object>>) (List<?>) actions)));
		} else if (parsedItem instanceof ExpressionTypes.Operation) {
			ExpressionTypes.Operation op = (ExpressionTypes.Operation) parsedItem;
			boolean actionOp = ASSIGN_BIOPS.contains(op.getName());
			if (actionRequired && !actionOp)
				throw new QuickParseException(op.getName() + " operation cannot be an action");
			else if (!actionAccepted && actionOp)
				throw new QuickParseException("Assignment operator " + op.getName() + " must be an action");

			ObservableValue<?> primary = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), op.getPrimaryOperand(), actionAccepted,
				false);
			if (actionOp) {
				if (!(primary instanceof SettableValue))
					throw new QuickParseException(
						op.getPrimaryOperand() + " does not parse to a settable value; cannot be assigned: " + op);
			}
			if (op instanceof ExpressionTypes.UnaryOperation) {
				ExpressionTypes.UnaryOperation unOp = (ExpressionTypes.UnaryOperation) op;
				ObservableValue<?> assignValue;
				if (actionOp) {
					SettableValue<Object> settable = (SettableValue<Object>) primary;
					assignValue = getUnaryAssignValue(settable, (ExpressionTypes.UnaryOperation) op, parseEnv);
					return makeActionValue(settable, assignValue, !unOp.isPreOp());
				} else {
					return mapUnary(primary, unOp, parseEnv);
				}
			} else {
				ExpressionTypes.BinaryOperation binOp = (ExpressionTypes.BinaryOperation) op;
				if (binOp.getName().equals("instanceof")) {
					// This warrants its own block here instead of doing it in the combineBinary because the right argument is a type
					TypeToken<?> testType = evaluateType(parseEnv, (ExpressionTypes.Type) binOp.getRight(), type);
					if (testType.getType() instanceof ParameterizedType)
						throw new QuickParseException(
							"instanceof checks cannot be performed against parameterized types: " + binOp.getRight());
					if (!testType.getRawType().isInterface() && !primary.getType().getRawType().isInterface()) {
						if (testType.isAssignableFrom(primary.getType())) {
							parseEnv.msg()
								.warn(binOp.getPrimaryOperand() + " is always an instance of " + binOp.getRight() + " (if non-null)");
							return primary.mapV(MathUtils.BOOLEAN, v -> v != null, true);
						} else if (!primary.getType().isAssignableFrom(testType)) {
							parseEnv.msg().error(binOp.getPrimaryOperand() + " is never an instance of " + binOp.getRight());
							return ObservableValue.constant(MathUtils.BOOLEAN, false);
						} else {
							return primary.mapV(MathUtils.BOOLEAN, v -> testType.getRawType().isInstance(v), true);
						}
					} else {
						return primary.mapV(MathUtils.BOOLEAN, v -> testType.getRawType().isInstance(v), true);
					}
				} else {
					ObservableValue<?> arg2 = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), binOp.getRight(), actionAccepted,
						false);
					if (actionOp) {
						SettableValue<Object> settable = (SettableValue<Object>) primary;
						ObservableValue<?> assignValue = getBinaryAssignValue(settable, arg2, binOp, parseEnv);
						return makeActionValue(settable, assignValue, false);
					} else {
						return combineBinary(primary, arg2, binOp, parseEnv);
					}
				}
			}
		} else if (parsedItem instanceof ExpressionTypes.Conditional) {
			if (actionRequired)
				throw new QuickParseException("Conditionals cannot be an action");
			ExpressionTypes.Conditional cond = (ExpressionTypes.Conditional) parsedItem;
			ObservableValue<?> condition = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), cond.getCondition(), actionAccepted,
				false);
			if (!TypeToken.of(Boolean.class).isAssignableFrom(condition.getType().wrap()))
				throw new QuickParseException(
					"Condition in " + cond + " evaluates to type " + condition.getType() + ", which is not boolean");
			ObservableValue<? extends T> affirm = evaluateTypeChecked(parseEnv, type, cond.getAffirmative(), actionAccepted, false);
			ObservableValue<? extends T> neg = evaluateTypeChecked(parseEnv, type, cond.getNegative(), actionAccepted, false);
			TypeToken<T> resultType = (TypeToken<T>) QuickUtils.getCommonType(affirm.getType(), neg.getType());
			TypeToken<ObservableValue<? extends T>> resultObsType = new TypeToken<ObservableValue<? extends T>>() {}
				.where(new TypeParameter<T>() {}, resultType.wrap());
			return ObservableValue.flatten(((ObservableValue<Boolean>) condition).mapV(resultObsType, v -> v ? affirm : neg, true))
				.mapV(resultType, v -> v, true);
		} else if (parsedItem instanceof ExpressionTypes.Cast) {
			if (actionRequired)
				throw new QuickParseException("Cast cannot be an action");
			ExpressionTypes.Cast cast = (ExpressionTypes.Cast) parsedItem;
			TypeToken<?> testType = evaluateType(parseEnv, cast.getType(), type);
			ObservableValue<?> var = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), cast.getValue(), actionAccepted, false);
			// TODO Can check better by checking for final types
			if (!testType.getRawType().isInterface() && !var.getType().getRawType().isInterface()) {
				if (testType.isAssignableFrom(var.getType())) {
					parseEnv.msg().warn(cast.getValue() + " is always an instance of " + cast.getType());
					return var.mapV((TypeToken<Object>) testType, v -> v, true);
				} else if (!QuickUtils.isAssignableFrom(var.getType(), testType)) {
					parseEnv.msg().error(cast.getValue() + " is never an instance of " + cast.getType());
					return var.mapV((TypeToken<Object>) testType, v -> {
						if (v == null) {
							if (testType.isPrimitive())
								throw new NullPointerException("null cannot be cast to " + testType);
							else
								return null;
						} else
							throw new ClassCastException(v + " is not an instance of " + testType);
					}, true);
				} else {
					return var.mapV((TypeToken<Object>) testType, v -> {
						if (v == null) {
							if (testType.isPrimitive())
								throw new NullPointerException("null cannot be cast to " + testType);
							else
								return null;
						} else if (testType.getRawType().isInstance(v))
							return v;
						else if (QuickUtils.isAssignableFrom(testType, TypeToken.of(v.getClass())))
							return QuickUtils.convert(testType, v);
						else if (QuickUtils.canConvert(testType, TypeToken.of(v.getClass())))
							return QuickUtils.convert(testType, v);
						else
							throw new ClassCastException(v + " is not an instance of " + testType);
					}, true);
				}
			} else {
				return var.mapV((TypeToken<Object>) testType, v -> {
					if (v == null) {
						if (testType.isPrimitive())
							throw new NullPointerException("null cannot be cast to " + testType);
						else
							return null;
					} else if (testType.getRawType().isInstance(v))
						return v;
					else if (QuickUtils.isAssignableFrom(testType, TypeToken.of(v.getClass())))
						return QuickUtils.convert(testType, v);
					else if (QuickUtils.canConvert(testType, TypeToken.of(v.getClass())))
						return QuickUtils.convert(testType, v);
					else
						throw new ClassCastException(v + " is not an instance of " + testType);
				}, true);
			}
			// Harder operations
			// Array operations
		} else if (parsedItem instanceof ExpressionTypes.ArrayAccess) {
			if (actionRequired)
				throw new QuickParseException("Array index operation cannot be an action");
			ExpressionTypes.ArrayAccess pai = (ExpressionTypes.ArrayAccess) parsedItem;
			ObservableValue<?> array = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), pai.getArray(), actionAccepted, false);
			TypeToken<? extends T> resultType;
			{
				TypeToken<?> testResultType;
				if (array.getType().isArray()) {
					testResultType = array.getType().getComponentType();
				} else if (TypeToken.of(List.class).isAssignableFrom(array.getType())) {
					testResultType = array.getType().resolveType(List.class.getTypeParameters()[0]);
				} else if (TypeToken.of(ObservableOrderedCollection.class).isAssignableFrom(array.getType())) {
					testResultType = array.getType().resolveType(ObservableOrderedCollection.class.getTypeParameters()[0]);
				} else {
					throw new QuickParseException("array value in " + parsedItem + " evaluates to type " + array.getType()
						+ ", which is not indexable");
				}
				if (!QuickUtils.isAssignableFrom(type, testResultType))
					throw new QuickParseException("array value in " + parsedItem + " evaluates to type " + array.getType()
						+ " which is not indexable to type " + type);
				resultType = (TypeToken<? extends T>) testResultType;
			}
			ObservableValue<?> index = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), pai.getIndex(), actionAccepted, false);
			if (!TypeToken.of(Long.class).isAssignableFrom(array.getType().wrap())) {
				throw new QuickParseException(
					"index value in " + parsedItem + " evaluates to type " + index.getType()
					+ ", which is not a valid index");
			}
			if (TypeToken.of(ObservableList.class).isAssignableFrom(array.getType())) {
				return SettableValue.flatten(((ObservableValue<ObservableList<? extends T>>) array)
					.combineV((list, idx) -> list.observeAt(((Number) idx).intValue(), null), index));
			} else if (TypeToken.of(ObservableOrderedCollection.class).isAssignableFrom(array.getType())) {
				return ObservableValue.flatten(((ObservableValue<ObservableOrderedCollection<? extends T>>) array)
					.combineV((coll, idx) -> coll.observeAt(((Number) idx).intValue(), null), index));
			} else
				return array.combineV((TypeToken<T>) resultType, (BiFunction<Object, Number, T>) (a, i) -> {
					int idx = i.intValue();
					if (TypeToken.of(Object[].class).isAssignableFrom(array.getType())) {
						return ((T[]) a)[idx];
					} else if (array.getType().isArray()) {
						return (T) java.lang.reflect.Array.get(a, idx);
					} else/* if (TypeToken.of(List.class).isAssignableFrom(array.getType()))*/ {
						return ((List<? extends T>) a).get(idx);
					}
				}, (ObservableValue<? extends Number>) index, true);
		} else if (parsedItem instanceof ExpressionTypes.ArrayInitializer) {
			if (actionRequired)
				throw new QuickParseException("Array init operation cannot be an action");
			ExpressionTypes.ArrayInitializer arrayInit = (ExpressionTypes.ArrayInitializer) parsedItem;
			TypeToken<?> arrayType = evaluateType(parseEnv, arrayInit.getType(), type);
			if (arrayInit.getSizes() != null) {
				ObservableValue<? extends Number>[] sizes = new ObservableValue[arrayInit.getSizes().size()];
				for (int i = 0; i < sizes.length; i++) {
					arrayType = QuickUtils.arrayTypeOf(arrayType);
					ObservableValue<?> size_i = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), arrayInit.getSizes().get(i),
						actionAccepted, false);
					if (!QuickUtils.isAssignableFrom(TypeToken.of(Integer.TYPE), size_i.getType()))
						throw new QuickParseException("Array size " + arrayInit.getSizes().get(i) + " parses to type "
							+ size_i.getType() + ", which is not a valid array size type");
					sizes[i] = (ObservableValue<? extends Number>) size_i;
				}

				TypeToken<?> fArrayType = arrayType;
				return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) arrayType, new Function<Object[], Object>() {
					@Override
					public Object apply(Object[] args) {
						return makeArray(fArrayType.getRawType(), args, 0);
					}

					private Object makeArray(Class<?> arrayClass, Object[] args, int argIdx) {
						int size = ((Number) args[argIdx]).intValue();
						Object array = Array.newInstance(arrayClass, size);
						if (argIdx < args.length - 1) {
							Class<?> elementClass = arrayClass.getComponentType();
							for (int i = 0; i < size; i++)
								Array.set(array, i, makeArray(elementClass, args, argIdx + 1));
						}
						return array;
					}
				}, true, sizes);
			} else if (arrayInit.getElements() != null) {
				ObservableValue<?>[] elements = new ObservableValue[arrayInit.getElements().size()];
				TypeToken<?> componentType = arrayType.getComponentType();
				for (int i = 0; i < elements.length; i++) {
					ObservableValue<?> element_i = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), arrayInit.getElements().get(i),
						actionAccepted, false);
					if (!QuickUtils.isAssignableFrom(componentType, element_i.getType()))
						throw new QuickParseException("Array element " + arrayInit.getElements().get(i) + " parses to type "
							+ element_i.getType() + ", which cannot be cast to " + componentType);
					elements[i] = element_i;
				}

				TypeToken<?> fArrayType = arrayType;
				return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) arrayType, new Function<Object[], Object>() {
					@Override
					public Object apply(Object[] args) {
						Object array = Array.newInstance(fArrayType.getRawType(), elements.length);
						for (int i = 0; i < args.length; i++)
							Array.set(array, i, args[i]);
						return array;
					}
				}, true, elements);
			} else
				throw new QuickParseException("Either array sizes or a value list must be specifieded for array initialization");
			// Now pulling stuff from the context
			// Identifier, placeholder, and unit
		} else if (parsedItem instanceof ExpressionTypes.Placeholder) {
			ExpressionTypes.Placeholder placeholder = (ExpressionTypes.Placeholder) parsedItem;
			ObservableValue<?> result = parseEnv.getContext().getVariable(placeholder.print());
			if (result == null)
				throw new QuickParseException("Unrecognized placeholder: " + placeholder);
			return result;
		} else if (parsedItem instanceof ExpressionTypes.UnitValue) {
			if (actionRequired)
				throw new QuickParseException("Unit value cannot be an action");
			ExpressionTypes.UnitValue unitValue = (ExpressionTypes.UnitValue) parsedItem;
			String unitName = unitValue.getUnit();
			List<Unit<?, ?>> units = parseEnv.getContext().getUnits(unitName, new ArrayList<>());
			if (units.isEmpty())
				throw new QuickParseException("Unrecognized unit " + unitName);
			Unit<?, ?> bestUnit = null;
			InvokableMatch bestMatch = null;
			for (Unit<?, ?> u : units) {
				if (!QuickUtils.isAssignableFrom(type, u.getToType()))
					continue;
				InvokableMatch match = getMatch(new TypeToken[] { u.getFromType() }, u.getToType(), false,
					Arrays.asList(unitValue.getValue()), parseEnv, type, actionAccepted);
				if (match != null && match.compareTo(bestMatch) < 0) {
					bestUnit = u;
					bestMatch = match;
				}
			}
			if (bestUnit == null)
				throw new QuickParseException("Unit " + unitName + " cannot convert from " + unitValue.getValue() + " to type " + type);
			if (!bestMatch.matches)
				throw new QuickParseException("Unit " + unitName + " cannot be applied to " + bestMatch.getArgumentTypes());
			ObservableValue<?> value = bestMatch.parameters[0];
			Unit<?, ?> unit = bestUnit;
			Function<Object, T> forwardMap = v -> {
				try {
					Object unitMapped = ((Unit<Object, ?>) unit).getMap().apply(QuickUtils.convert(unit.getFromType(), v));
					return QuickUtils.convert(type, unitMapped);
				} catch (QuickException e) {
					parseEnv.msg().error("Unit " + unit.getName() + " could not convert value " + v + " to " + unit.getToType(), e);
					return null; // TODO What to do with this?
				}
			};
			Function<T, Object> reverseMap = v -> {
				try {
					Object unitMapped = ((Unit<Object, Object>) unit).getReverseMap().apply(QuickUtils.convert(unit.getToType(), v));
					return QuickUtils.convert(unit.getFromType(), unitMapped);
				} catch (QuickException e) {
					parseEnv.msg().error("Unit " + unit.getName() + " could not convert value " + v + " to " + unit.getFromType(), e);
					return null; // TODO What to do with this?
				}
			};
			if (value instanceof SettableValue && unit.getReverseMap() != null) {
				return ((SettableValue<Object>) value).mapV((TypeToken<T>) bestMatch.returnType, forwardMap, reverseMap, true);
			} else {
				return value.mapV((TypeToken<T>) bestMatch.returnType, forwardMap);
			}
			// Now harder operations
		} else if (parsedItem instanceof ExpressionTypes.Constructor) {
			if (actionRequired)
				throw new QuickParseException("Constructor cannot be an action");
			ExpressionTypes.Constructor constructor = (ExpressionTypes.Constructor) parsedItem;
			TypeToken<?> typeToCreate = evaluateType(parseEnv, constructor.getType(), type);
			Constructor<?> bestConstructor = null;
			InvokableMatch bestMatch = null;
			for (Constructor<?> c : typeToCreate.getRawType().getConstructors()) {
				if (!c.isAccessible())
					continue;
				InvokableMatch match = getMatch(c.getGenericParameterTypes(), typeToCreate.getType(), c.isVarArgs(),
					constructor.getArguments(), parseEnv, type, actionAccepted);
				if (match != null && match.compareTo(bestMatch) < 0) {
					bestMatch = match;
					bestConstructor = c;
				}
			}
			if (bestConstructor == null)
				throw new QuickParseException("No such constructor found: " + parsedItem);
			if (!bestMatch.matches)
				throw new QuickParseException("Constructor " + bestConstructor + " cannot be applied to " + bestMatch.getArgumentTypes());
			Constructor<?> toInvoke = bestConstructor;
			return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) bestMatch.returnType, args -> {
				try {
					return toInvoke.newInstance(args);
				} catch (Exception e) {
					parseEnv.msg().error("Invocation failed for " + constructor, e);
					return null; // TODO What to do with this?
				}
			}, true, bestMatch.parameters);
		} else if (parsedItem instanceof ExpressionTypes.QualifiedName) {
			ExpressionTypes.QualifiedName qName = (ExpressionTypes.QualifiedName) parsedItem;
			return evaluateMember(new ExpressionTypes.FieldAccess(qName.getContext(), qName.getQualifier(), qName.getName()), parseEnv,
				type, actionAccepted, actionRequired);
		} else if (parsedItem instanceof ExpressionTypes.MemberAccess) {
			ExpressionTypes.MemberAccess member = (ExpressionTypes.MemberAccess) parsedItem;
			return evaluateMember(member, parseEnv, type, actionAccepted, actionRequired);
		} else
			throw new QuickParseException("Unrecognized parsed item type: " + parsedItem.getClass());
	}

	private static <T> ObservableValue<?> evaluateMember(ExpressionTypes.MemberAccess member, QuickParseEnv parseEnv, TypeToken<T> type,
		boolean actionAccepted, boolean actionRequired) throws QuickParseException {
		if (member.getMemberContext() == null) {
			if (member instanceof ExpressionTypes.MethodInvocation) {
				// A function
				return evaluateFunction((MethodInvocation) member, parseEnv, type, actionAccepted);
			} else {
				// A variable
				ObservableValue<?> result = parseEnv.getContext().getVariable(member.getName());
				if (result == null)
					throw new QuickParseException(member.getName() + " cannot be resolved");
				if (actionRequired && !TypeToken.of(ObservableAction.class).isAssignableFrom(result.getType()))
					throw new QuickParseException("Variable " + member.getName() + " is not an action");
				return result;
			}
		} else {
			// May be a method invocation on a value or a static invocation on a type
			String rootCtx = getRootContext(member.getMemberContext());
			Class<?> contextType = null;
			if (rootCtx != null && parseEnv.getContext().getVariable(rootCtx) == null) {
				// May be a static invocation on a type
				try {
					contextType = rawType(member.getMemberContext().toString(), parseEnv);
				} catch (QuickParseException e) {
					// We'll throw a different exception later if we can't resolve it
				}
			}
			ObservableValue<?> result;
			if (contextType != null) {
				result = evaluateStatic(member, contextType, parseEnv, type, actionAccepted);
			} else {
				// Not a static invocation. Evaluate the context. Let that evaluation throw the exception if needed.
				ObservableValue<?> context = evaluateTypeChecked(parseEnv, TypeToken.of(Object.class), member.getMemberContext(),
					actionAccepted, false);
				result = evaluateMemberAccess(member, context, parseEnv, type, actionAccepted);
			}
			if (actionRequired && !(member instanceof ExpressionTypes.MethodInvocation)/*field*/
				&& !TypeToken.of(ObservableAction.class).isAssignableFrom(result.getType()))
				throw new QuickParseException(
					"Field " + member.getMemberContext().toString() + "." + member.getName() + " is not an action");
			return result;
		}
	}

	private static TypeToken<?> evaluateType(QuickParseEnv parseEnv, ExpressionTypes.Type parsedType, TypeToken<?> expected)
		throws QuickParseException {
		Type reflectType = getReflectType(parsedType, expected, parseEnv);

		return TypeToken.of(reflectType);
	}

	private static Type getReflectType(ExpressionTypes.Type parsedType, TypeToken<?> expected, QuickParseEnv parseEnv)
		throws QuickParseException {
		if (parsedType instanceof ExpressionTypes.PrimitiveType)
			return ((ExpressionTypes.PrimitiveType) parsedType).getType();
		else if (parsedType instanceof ExpressionTypes.ClassType) {
			ExpressionTypes.ClassType classType = (ExpressionTypes.ClassType) parsedType;
			if (classType.getTypeArgs() == null)
				return rawType(classType.getName(), parseEnv);
			else
				return parameterizedType(classType.getName(), classType.getTypeArgs(), expected, parseEnv);
		} else if (parsedType instanceof ExpressionTypes.ArrayType) {
			ExpressionTypes.ArrayType arrayType = (ExpressionTypes.ArrayType) parsedType;
			TypeToken<?> expectedComponent = expected;
			for (int i = 0; i < arrayType.getDimension() && expectedComponent.isArray(); i++)
				expectedComponent = expectedComponent.getComponentType();
			Type arrayTypeResult = arrayType(getReflectType(arrayType.getComponentType(), expectedComponent, parseEnv));
			for (int i = 1; i < arrayType.getDimension(); i++)
				arrayTypeResult = arrayType(arrayTypeResult);
			return arrayTypeResult;
		} else
			throw new QuickParseException("Unrecognized type: " + parsedType.getClass().getSimpleName());
	}

	private static Type parameterizedType(String name, List<ExpressionTypes.Type> parameterTypes, TypeToken<?> expected,
		QuickParseEnv parseEnv)
		throws QuickParseException {
		Class<?> raw = rawType(name, parseEnv);

		Type[] typeArgs = new Type[raw.getTypeParameters().length];
		if (parameterTypes.isEmpty()) {
			// Diamond operator. Figure out the parameter types.
			for (int i = 0; i < typeArgs.length; i++)
				typeArgs[i] = expected.resolveType(raw.getTypeParameters()[i]).getType();
		} else if (parameterTypes.size() != typeArgs.length)
			throw new QuickParseException("Type " + raw.getName() + " cannot be parameterized with " + parameterTypes.size() + " types");
		else {
			for (int i = 0; i < typeArgs.length; i++)
				typeArgs[i] = getReflectType(parameterTypes.get(i), expected.resolveType(raw.getTypeParameters()[i]), parseEnv);
		}
		return new ParameterizedType(raw, typeArgs);
	}

	static class ParameterizedType implements java.lang.reflect.ParameterizedType {
		private final Type theRawType;
		private final Type[] theTypeArgs;

		ParameterizedType(Type raw, Type[] args) {
			theRawType = raw;
			theTypeArgs = args;
		}

		@Override
		public Type getRawType() {
			return theRawType;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return theTypeArgs;
		}

		@Override
		public Type getOwnerType() {
			return null;
		}
	}

	private static Class<?> rawType(String name, QuickParseEnv parseEnv) throws QuickParseException {
		int dotIdx = name.lastIndexOf('.');
		if (dotIdx < 0) {
			Class<?> quickType;
			try {
				quickType = parseEnv.cv().loadIfMapped(name, Object.class);
				if (quickType == null) {
					try {
						quickType = parseEnv.cv().loadClass(name);
					} catch (ClassNotFoundException e) {
						try {
							quickType = parseEnv.cv().loadClass("java.lang." + name);
						} catch (ClassNotFoundException e2) {
							throw new QuickParseException("Could not load type " + name);
						}
					}
				}
			} catch (QuickException e) {
				throw new QuickParseException("Could not load type " + name, e);
			}
			if (quickType != null)
				return quickType;
		}
		try {
			return parseEnv.cv().loadClass(name);
		} catch (ClassNotFoundException e) {
			// Do nothing. We'll throw our own exception if we can't find the class.
		}
		StringBuilder qClassName = new StringBuilder(name);
		while (dotIdx >= 0) {
			qClassName.setCharAt(dotIdx, '$');
			try {
				return parseEnv.cv().loadClass(qClassName.toString());
			} catch (ClassNotFoundException e) {
			}
			dotIdx = qClassName.lastIndexOf(".");
		}
		throw new QuickParseException("Could not load class " + name);
	}

	private static Type arrayType(Type reflectType) {
		if (reflectType instanceof Class)
			return Array.newInstance((Class<?>) reflectType, 0).getClass();
		class ArrayType implements GenericArrayType {
			@Override
			public Type getGenericComponentType() {
				return reflectType;
			}
		}
		return new ArrayType();
	}

	static class InvokableMatch implements Comparable<InvokableMatch> {
		final ObservableValue<?>[] parameters;
		final TypeToken<?> returnType;
		final double distance;
		final boolean matches;

		InvokableMatch(ObservableValue<?>[] parameters, TypeToken<?> returnType, double distance, boolean matches) {
			this.parameters = parameters;
			this.returnType = returnType;
			this.distance = distance;
			this.matches = matches;
		}

		public List<TypeToken<?>> getArgumentTypes() {
			return Arrays.stream(parameters).map(v -> v.getType()).collect(Collectors.toList());
		}

		@Override
		public int compareTo(InvokableMatch o) {
			if (o == null)
				return -1;
			if (matches && !o.matches)
				return -1;
			if (!matches && o.matches)
				return 1;
			double distDiff = distance - o.distance;
			return distDiff < 0 ? -1 : (distDiff > 0 ? 1 : 0);
		}
	}

	private static String getRootContext(QPPExpression context) {
		if (context instanceof ExpressionTypes.MemberAccess) {
			ExpressionTypes.MemberAccess method = (ExpressionTypes.MemberAccess) context;
			if (method instanceof ExpressionTypes.MethodInvocation)
				return null;
			String ret = getRootContext(method.getMemberContext());
			if (ret == null)
				return null;
			return ret + "." + method.getName();
		} else if (context instanceof ExpressionTypes.QualifiedName) {
			ExpressionTypes.QualifiedName qName = (ExpressionTypes.QualifiedName) context;
			if (qName.getQualifier() == null)
				return qName.getName();
			String ctxStr = getRootContext(qName.getQualifier());
			return ctxStr + "." + qName.getName();
		} else
			return null;
	}

	private static InvokableMatch getMatch(Type[] paramTypes, Type returnType, boolean varArgs, List<QPPExpression> arguments,
		QuickParseEnv env,
		TypeToken<?> type, boolean actionAccepted) throws QuickParseException {
		TypeToken<?>[] typeTokenParams = new TypeToken[paramTypes.length];
		for (int i = 0; i < paramTypes.length; i++)
			typeTokenParams[i] = type.resolveType(paramTypes[i]);
		return getMatch(typeTokenParams, type.resolveType(returnType), varArgs, arguments, env, type, actionAccepted);
	}

	private static InvokableMatch getMatch(TypeToken<?>[] paramTypes, TypeToken<?> returnType, boolean varArgs,
		List<QPPExpression> arguments,
		QuickParseEnv parseEnv, TypeToken<?> type, boolean actionAccepted) throws QuickParseException {
		TypeToken<?>[] argTargetTypes = new TypeToken[arguments.size()];
		if (paramTypes.length == arguments.size()) {
			argTargetTypes = paramTypes;
		} else if (varArgs) {
			if (arguments.size() >= paramTypes.length - 1) {
				for (int i = 0; i < paramTypes.length - 1; i++)
					argTargetTypes[i] = paramTypes[i];
				for (int i = paramTypes.length - 1; i < arguments.size(); i++)
					argTargetTypes[i] = paramTypes[paramTypes.length - 1];
			} else
				return null;
		} else
			return null;

		ObservableValue<?>[] args = new ObservableValue[arguments.size()];
		for (int i = 0; i < args.length; i++)
			args[i] = evaluateTypeless(parseEnv, argTargetTypes[i], arguments.get(i), actionAccepted, false);

		Map<TypeToken<?>, TypeToken<?>> typeVariables = new HashMap<>();
		for (int i = 0; i < argTargetTypes.length; i++)
			resolveTypeVariables(argTargetTypes[i], args[i].getType(), typeVariables);
		for (int i = 0; i < argTargetTypes.length; i++)
			argTargetTypes[i] = resolveParams(argTargetTypes[i], typeVariables);

		for (int i = 0; i < args.length; i++) {
			if (!QuickUtils.isAssignableFrom(argTargetTypes[i], args[i].getType()))
				return new InvokableMatch(args, resolveParams(returnType, typeVariables), 0, false);
		}
		double distance = 0;
		for (int i = 0; i < paramTypes.length && i < args.length; i++)
			distance += getDistance(argTargetTypes[i].wrap(), args[i].getType().wrap());
		return new InvokableMatch(args, resolveParams(returnType, typeVariables), distance, true);
	}

	private static void resolveTypeVariables(TypeToken<?> paramType, TypeToken<?> argType,
		Map<TypeToken<?>, TypeToken<?>> typeVariables) {
		if (paramType.getType() instanceof TypeVariable) {
			TypeToken<?> type = typeVariables.get(paramType);
			if (type == null)
				type = argType;
			else
				type = QuickUtils.getCommonType(type, argType);
			typeVariables.put(paramType, type.wrap());
		} else if (paramType.getType() instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) paramType.getType();
			for (Type subPT : pt.getActualTypeArguments())
				resolveTypeVariables(TypeToken.of(subPT), argType.resolveType(subPT), typeVariables);
		} else if (paramType.isArray()) {
			TypeToken<?> compType = paramType.getComponentType();
			resolveTypeVariables(compType, argType.resolveType(compType.getType()), typeVariables);
		}
	}

	private static TypeToken<?> resolveParams(TypeToken<?> type, Map<TypeToken<?>, TypeToken<?>> typeVariables) {
		if (type.getType() instanceof TypeVariable)
			return typeVariables.get(type);
		else if (type.getType() instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type.getType();
			boolean changed = false;
			Type[] typeParams = new Type[pt.getActualTypeArguments().length];
			for (int i = 0; i < typeParams.length; i++) {
				TypeToken<?> typeParam = type.resolveType(pt.getActualTypeArguments()[i]);
				TypeToken<?> resolved = resolveParams(typeParam, typeVariables);
				typeParams[i] = resolved.getType();
				changed |= resolved != typeParam;
			}
			if (changed)
				return TypeToken.of(new ParameterizedType(type.getRawType(), typeParams));
			else
				return type;
		} else if (type.isArray()) {
			TypeToken<?> typeParam = type.getComponentType();
			TypeToken<?> resolved = resolveParams(typeParam, typeVariables);
			if (typeParam != resolved)
				return QuickUtils.arrayTypeOf(resolved);
			else
				return type;
		} else
			return type;
	}

	private static double getDistance(TypeToken<?> paramType, TypeToken<?> argType) {
		double distance = 0;
		distance += getRawDistance(paramType.getRawType(), argType.getRawType());
		if (paramType.getType() instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) paramType.getType();
			for (int i = 0; i < pt.getActualTypeArguments().length; i++) {
				double paramDist = getDistance(paramType.resolveType(pt.getActualTypeArguments()[i]),
					argType.resolveType(pt.getActualTypeArguments()[i]));
				distance += paramDist / 10;
			}
		}
		return distance;
	}

	private static int getRawDistance(Class<?> paramType, Class<?> argType) {
		if (paramType.equals(argType))
			return 0;
		else if (paramType.isAssignableFrom(argType)) {
			int dist = 0;
			while (argType.getSuperclass() != null && paramType.isAssignableFrom(argType.getSuperclass())) {
				argType = argType.getSuperclass();
				dist++;
			}
			if (paramType.isInterface())
				dist += getInterfaceDistance(paramType, argType);
			return dist;
		} else if (argType.isAssignableFrom(paramType))
			return getRawDistance(argType, paramType);
		else if (Number.class.isAssignableFrom(paramType) && Number.class.isAssignableFrom(argType)) {
			int paramNumTypeOrdinal = getNumberTypeOrdinal(paramType);
			int argNumTypeOrdinal = getNumberTypeOrdinal(argType);
			if (paramNumTypeOrdinal < 0 || argNumTypeOrdinal < 0 || argNumTypeOrdinal > paramNumTypeOrdinal)
				throw new IllegalStateException("Shouldn't get here: " + paramType.getName() + " and " + argType.getName());
			return paramNumTypeOrdinal - argNumTypeOrdinal;
		} else
			throw new IllegalStateException("Shouldn't get here: " + paramType.getName() + " and " + argType.getName());
	}

	private static int getInterfaceDistance(Class<?> paramType, Class<?> argType) {
		if (paramType.equals(argType))
			return 0;
		for (Class<?> intf : argType.getInterfaces()) {
			if (paramType.isAssignableFrom(intf))
				return getInterfaceDistance(paramType, intf) + 1;
		}
		throw new IllegalStateException("Shouldn't get here");
	}

	private static int getNumberTypeOrdinal(Class<?> numType) {
		if (numType == Byte.class)
			return 0;
		else if (numType == Short.class)
			return 1;
		else if (numType == Integer.class)
			return 2;
		else if (numType == Long.class)
			return 3;
		else if (numType == Float.class)
			return 4;
		else if (numType == Double.class)
			return 5;
		else
			return -1;
	}

	private static ObservableValue<?> evaluateStatic(ExpressionTypes.MemberAccess member, Class<?> targetType, QuickParseEnv parseEnv,
		TypeToken<?> type, boolean actionAccepted) throws QuickParseException {
		// Evaluate as a static invocation on the type
		if (member instanceof ExpressionTypes.MethodInvocation) {
			Method bestMethod = null;
			InvokableMatch bestMatch = null;
			int publicStatic = Modifier.STATIC | Modifier.PUBLIC;
			for (Method m : targetType.getMethods()) {
				if ((m.getModifiers() & publicStatic) != publicStatic || !m.getName().equals(member.getName()))
					continue;
				InvokableMatch match = getMatch(m.getGenericParameterTypes(), m.getGenericReturnType(), m.isVarArgs(),
					((ExpressionTypes.MethodInvocation) member).getArguments(), parseEnv, type, actionAccepted);
				if (match != null && match.compareTo(bestMatch) < 0) {
					bestMatch = match;
					bestMethod = m;
				}
			}
			if (bestMethod == null)
				throw new QuickParseException("No such method found: " + targetType.getName() + "." + member.getName());
			if (!bestMatch.matches)
				throw new QuickParseException("Method " + bestMethod + " cannot be applied to " + bestMatch.getArgumentTypes());
			Method toInvoke = bestMethod;
			return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) bestMatch.returnType,
				args -> {
					try {
						return toInvoke.invoke(null, args);
					} catch (Exception e) {
						parseEnv.msg().error("Invocation failed for static method " + member, e);
						return null; // TODO What to do with this?
					}
				}, true, bestMatch.parameters);
		} else {
			Field fieldRef;
			try {
				fieldRef = targetType.getField(member.getName());
			} catch (NoSuchFieldException e) {
				throw new QuickParseException("No such field " + targetType.getName() + "." + member.getName(), e);
			} catch (SecurityException e) {
				throw new QuickParseException("Could not access field " + targetType.getName() + "." + member.getName(), e);
			}
			if ((fieldRef.getModifiers() & Modifier.STATIC) == 0)
				throw new QuickParseException("Field " + targetType.getName() + "." + fieldRef.getName() + " is not static");
			return new ObservableValue<Object>() {
				private final TypeToken<Object> fieldType = (TypeToken<Object>) TypeToken.of(fieldRef.getGenericType());

				@Override
				public Subscription subscribe(org.observe.Observer<? super ObservableValueEvent<Object>> observer) {
					observer.onNext(createInitialEvent(get()));
					return () -> {
					}; // Can't get notifications on a field change
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public TypeToken<Object> getType() {
					return fieldType;
				}

				@Override
				public Object get() {
					try {
						return fieldRef.get(null);
					} catch (Exception e) {
						parseEnv.msg().error("Could not get static field " + targetType.getName() + "." + fieldRef.getName(), e);
						return null; // TODO What to do with this?
					}
				}

				@Override
				public String toString() {
					return member.toString();
				}
			};
		}
	}

	private static ObservableValue<?> evaluateMemberAccess(ExpressionTypes.MemberAccess member, ObservableValue<?> context,
		QuickParseEnv parseEnv, TypeToken<?> type, boolean actionAccepted) throws QuickParseException {
		if (TypeToken.of(QuickAppModel.class).isAssignableFrom(context.getType())) {
			if (!(context instanceof ObservableValue.ConstantObservableValue))
				throw new QuickParseException("Model observables must be constant to be evaluated");
			QuickAppModel model = (QuickAppModel) context.get();
			Object fieldVal = model.getField(member.getName());
			if (fieldVal == null)
				throw new QuickParseException("Model " + member.getMemberContext() + " does not contain field " + member.getName());
			if (member instanceof ExpressionTypes.FieldAccess) {
				if (fieldVal instanceof ObservableValue)
					return (ObservableValue<?>) fieldVal;
				else
					return ObservableValue.constant(fieldVal);
			} else {
				ExpressionTypes.MethodInvocation method = (ExpressionTypes.MethodInvocation) member;
				if (fieldVal instanceof ExpressionFunction) {
					ExpressionFunction<?> fn = (ExpressionFunction<?>) fieldVal;
					InvokableMatch match = getMatch(fn.getArgumentTypes().toArray(new TypeToken[0]), fn.getReturnType(), fn.isVarArgs(),
						method.getArguments(), parseEnv, type, actionAccepted);
					return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) match.returnType, args -> {
						try {
							return fn.apply(Arrays.asList(args));
						} catch (RuntimeException e) {
							parseEnv.msg().error("Invocation failed for function " + method, e);
							return null; // TODO What to do with this?
						}
					}, true, match.parameters);
				} else if (fieldVal instanceof ObservableAction) {
					if (method.getArguments().size() != 0)
						throw new QuickParseException("Invalid invocation of action " + method.getName() + " of model "
							+ member.getMemberContext() + ": actions cannot take arguments");
					return valueOf((ObservableAction<?>) fieldVal);
				} else
					throw new QuickParseException(
						"Field " + method.getName() + " of model " + method.getMemberContext() + " is not a function or an action");
			}
		} else {
			if (member instanceof ExpressionTypes.FieldAccess) {
				Field fieldRef;
				try {
					fieldRef = context.getType().getRawType().getField(member.getName());
				} catch (NoSuchFieldException e) {
					throw new QuickParseException("No such field " + member.getMemberContext() + "." + member.getName(), e);
				} catch (SecurityException e) {
					throw new QuickParseException("Could not access field " + member.getMemberContext() + "." + member.getName(), e);
				}
				if ((fieldRef.getModifiers() & Modifier.STATIC) != 0)
					throw new QuickParseException("Field " + context + "." + fieldRef.getName() + " is static");
				return context.mapV((TypeToken<Object>) context.getType().resolveType(fieldRef.getGenericType()), v -> {
					try {
						return fieldRef.get(v);
					} catch (Exception e) {
						parseEnv.msg().error("Could not get field " + member.getMemberContext() + "." + fieldRef.getName(), e);
						return null; // TODO What to do with this?
					}
				});
			} else {
				ExpressionTypes.MethodInvocation method = (ExpressionTypes.MethodInvocation) member;
				Method bestMethod = null;
				InvokableMatch bestMatch = null;
				for (Method m : context.getType().getRawType().getMethods()) {
					if (!m.getName().equals(method.getName()) || !m.isAccessible())
						continue;
					InvokableMatch match = getMatch(m.getGenericParameterTypes(), m.getGenericReturnType(), m.isVarArgs(),
						method.getArguments(), parseEnv, type, actionAccepted);
					if (match != null && match.compareTo(bestMatch) < 0) {
						bestMatch = match;
						bestMethod = m;
					}
				}
				if (bestMethod == null)
					throw new QuickParseException("No such method found: " + method);
				if (!bestMatch.matches)
					throw new QuickParseException("Method " + bestMethod + " cannot be applied to " + bestMatch.getArgumentTypes());
				ObservableValue<?>[] composed = new ObservableValue[bestMatch.parameters.length + 1];
				composed[0] = context;
				System.arraycopy(bestMatch.parameters, 0, composed, 1, bestMatch.parameters.length);
				Method toInvoke = bestMethod;
				return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) bestMatch.returnType, args -> {
					Object ctx = args[0];
					Object[] params = new Object[args.length - 1];
					System.arraycopy(args, 1, params, 0, params.length);
					try {
						return toInvoke.invoke(ctx, params);
					} catch (Exception e) {
						parseEnv.msg().error("Invocation failed for method " + method, e);
						return null; // TODO What to do with this?
					}
				}, true, composed);
			}
		}
	}

	private static <T> ObservableValue<ObservableAction<T>> valueOf(ObservableAction<T> action) {
		return ObservableValue.constant(new TypeToken<ObservableAction<T>>() {}.where(new TypeParameter<T>() {}, action.getType().wrap()),
			action);
	}

	private static ObservableValue<?> evaluateFunction(ExpressionTypes.MethodInvocation method, QuickParseEnv parseEnv, TypeToken<?> type,
		boolean actionAccepted) throws QuickParseException {
		List<ExpressionFunction<?>> functions = new ArrayList<>();
		parseEnv.getContext().getFunctions(method.getName(), functions);
		ExpressionFunction<?> bestFunction = null;
		InvokableMatch bestMatch = null;
		for (ExpressionFunction<?> fn : functions) {
			InvokableMatch match = getMatch(fn.getArgumentTypes().toArray(new TypeToken[0]), fn.getReturnType(), fn.isVarArgs(),
				method.getArguments(), parseEnv, type, actionAccepted);
			if (match != null && match.compareTo(bestMatch) < 0) {
				bestMatch = match;
				bestFunction = fn;
			}
		}
		if (bestFunction == null)
			throw new QuickParseException("No such function found: " + method);
		else if (!bestMatch.matches)
			throw new QuickParseException("Function " + bestFunction + " cannot be applied to " + bestMatch.getArgumentTypes());
		ExpressionFunction<?> toInvoke = bestFunction;
		return new ObservableValue.ComposedObservableValue<>((TypeToken<Object>) bestMatch.returnType, args -> {
			try {
				return toInvoke.apply(Arrays.asList(args));
			} catch (RuntimeException e) {
				parseEnv.msg().error("Invocation failed for function " + method, e);
				return null; // TODO What to do with this?
			}
		}, true, bestMatch.parameters);
	}

	private static ObservableValue<?> mapUnary(ObservableValue<?> arg1, ExpressionTypes.UnaryOperation op, QuickParseEnv parseEnv)
		throws QuickParseException {
		List<ExpressionFunction<?>> functions = parseEnv.getContext().getFunctions(op.getName(), new ArrayList<>());
		for (ExpressionFunction<?> fn : functions) {
			if (fn.applies(Arrays.asList(arg1.getType())))
				return arg1.mapV((TypeToken<Object>) fn.getReturnType(), v -> fn.apply(Arrays.asList(v)));
		}
		try {
			switch (op.getName()) {
			case "+":
			case "-":
			case "~":
				try {
					return MathUtils.unaryOp(op.getName(), arg1);
				} catch (IllegalArgumentException e) {
					throw new QuickParseException(e.getMessage(), e);
				}
			case "!":
				try {
					return MathUtils.not(arg1);
				} catch (IllegalArgumentException e) {
					throw new QuickParseException(e.getMessage(), e);
				}
			default:
				throw new QuickParseException("Unrecognized unary operator: " + op.getName());
			}
		} catch (IllegalArgumentException e) {
			throw new QuickParseException(e.getMessage(), e);
		}
	}

	private static <T> ObservableValue<T> getUnaryAssignValue(SettableValue<T> arg1, ExpressionTypes.UnaryOperation op,
		QuickParseEnv parseEnv)
		throws QuickParseException {
		List<ExpressionFunction<?>> functions = parseEnv.getContext().getFunctions(op.getName().substring(0, 1), new ArrayList<>());
		for (ExpressionFunction<?> fn : functions) {
			if (arg1.getType().isAssignableFrom(fn.getReturnType())
				&& fn.applies(Arrays.asList(arg1.getType(), TypeToken.of(Integer.TYPE))))
				return arg1.mapV((TypeToken<T>) fn.getReturnType(), v -> (T) fn.apply(Arrays.asList(v, 1)));
		}
		ObservableValue<T> result;
		try {
			switch (op.getName()) {
			case "++":
				result = MathUtils.addOne(arg1);
				break;
			case "--":
				result = MathUtils.minusOne(arg1);
				break;
			default:
				throw new QuickParseException("Unrecognized unary assignment operator: " + op.getName());
			}
		} catch (IllegalArgumentException e) {
			throw new QuickParseException(e.getMessage(), e);
		}
		return result;
	}

	private static ObservableValue<?> combineBinary(ObservableValue<?> arg1, ObservableValue<?> arg2,
		ExpressionTypes.BinaryOperation op, QuickParseEnv parseEnv) throws QuickParseException {
		List<ExpressionFunction<?>> functions = parseEnv.getContext().getFunctions(op.getName(), new ArrayList<>());
		for (ExpressionFunction<?> fn : functions) {
			if (fn.applies(Arrays.asList(arg1.getType(), arg2.getType())))
				return arg1.combineV((TypeToken<Object>) fn.getReturnType(), (v1, v2) -> fn.apply(Arrays.asList(v1, v2)), arg2, true);
		}
		try {
			switch (op.getName()) {
			case "+":
				TypeToken<String> strType = TypeToken.of(String.class);
				if (strType.isAssignableFrom(arg1.getType()) || strType.isAssignableFrom(arg2.getType())) {
					return arg1.combineV(strType, (a1, a2) -> new StringBuilder().append(a1).append(a2).toString(), arg2, true);
				}
				//$FALL-THROUGH$
			case "-":
			case "*":
			case "/":
			case "%":
			case "<<":
			case ">>":
			case ">>>":
			case "&":
			case "|":
			case "^":
			case "&&":
			case "||":
			case "==":
			case "!=":
			case ">":
			case "<":
			case ">=":
			case "<=":
				return MathUtils.binaryMathOp(op.getName(), arg1, arg2);
			default:
				throw new QuickParseException("Unrecognized binary operator: " + op.getName());
			}
		} catch (IllegalArgumentException e) {
			throw new QuickParseException(op + ": " + e.getMessage(), e);
		}
	}

	private static ObservableValue<?> getBinaryAssignValue(SettableValue<?> arg1, ObservableValue<?> arg2,
		ExpressionTypes.BinaryOperation op, QuickParseEnv parseEnv) throws QuickParseException {
		List<ExpressionFunction<?>> functions = parseEnv.getContext().getFunctions(op.getName().substring(0, op.getName().length() - 1),
			new ArrayList<>());
		for (ExpressionFunction<?> fn : functions) {
			if (arg1.getType().isAssignableFrom(fn.getReturnType())
				&& fn.applies(Arrays.asList(arg1.getType(), TypeToken.of(Integer.TYPE))))
				return arg1.combineV((TypeToken<Object>) fn.getReturnType(), (v1, v2) -> fn.apply(Arrays.asList(v1, v2)), arg2, true);
		}
		switch (op.getName()) {
		case "=":
			if (!QuickUtils.isAssignableFrom(arg1.getType(), arg2.getType()))
				throw new QuickParseException(
					op.getRight() + ", type " + arg2.getType() + ", cannot be assigned to type " + arg1.getType());
			return arg2;
		case "+=":
		case "-=":
		case "*=":
		case "/=":
		case "%=":
		case "&=":
		case "|=":
		case "^=":
		case "<<=":
		case ">>=":
		case ">>>=":
			String mathOp = op.getName().substring(0, op.getName().length() - 1);
			ObservableValue<?> result;
			try {
				result = MathUtils.binaryMathOp(mathOp, arg1, arg2);
			} catch (IllegalArgumentException e) {
				throw new QuickParseException(e.getMessage(), e);
			}
			if (!QuickUtils.isAssignableFrom(arg1.getType(), result.getType()))
				throw new QuickParseException(op.getPrimaryOperand() + " " + mathOp + " " + op.getRight()
					+ ", type " + result.getType() + ", cannot be assigned to type " + arg1.getType());
			return result;
		default:
			throw new QuickParseException("Unrecognized binary assignment operator: " + op.getName());
		}
	}

	private static <T, V extends T> ObservableValue<ObservableAction<T>> makeActionValue(SettableValue<T> settable,
		ObservableValue<V> value, boolean resultPreOp) throws QuickParseException {
		ObservableAction<V> action = settable.assignmentTo(value);
		// TODO not handling the pre-op boolean at all
		return ObservableValue.constant(new TypeToken<ObservableAction<T>>() {}.where(new TypeParameter<T>() {}, settable.getType().wrap()),
			new ObservableAction<T>() {
				@Override
				public TypeToken<T> getType() {
					return settable.getType();
				}

				@Override
				public T act(Object cause) throws IllegalStateException {
					T ret;
					if (resultPreOp) {
						ret=settable.get();
						action.act(cause);
					} else {
						ret = action.act(cause);
					}
					return ret;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return action.isEnabled();
				}

				@Override
				public String toString() {
					return action.toString();
				}
			});
	}
}
