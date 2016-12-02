package org.quick.core.style;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.quick.core.QuickElement;
import org.quick.core.mgr.QuickState;

/** The style on a {@link QuickElement} */
public class QuickElementStyle implements QuickStyle {
	private final QuickElement theElement;
	private final ObservableSet<QuickState> theExtraStates;
	private StyleConditionInstance<?> theCondition;

	/** @param element The element that this style is for */
	public QuickElementStyle(QuickElement element) {
		this(element, null);
	}

	/** @return The condition instance representing this style */
	public StyleConditionInstance<?> getCondition() {
		if (theCondition == null) {
			theCondition = theExtraStates == null ? StyleConditionInstance.of(theElement)
				: StyleConditionInstance.of(theElement, theExtraStates);
		}
		return theCondition;
	}

	/**
	 * @param element The element that this style is for
	 * @param extraStates Extra states, if any to use for determining style from style sheets
	 */
	public QuickElementStyle(QuickElement element, ObservableSet<QuickState> extraStates) {
		theElement = element;
		theExtraStates = extraStates;
	}

	/** @return The element that this style is for */
	public QuickElement getElement() {
		return theElement;
	}

	@Override
	public ObservableSet<StyleAttribute<?>> attributes() {
		ObservableValue<QuickStyle> localStyle = theElement.atts().getHolder(StyleAttributes.style);
		ObservableSet<StyleAttribute<?>> localAttrs = ObservableSet.flattenValue(localStyle.mapV(s -> s.attributes()));
		StyleSheet sheet = theElement.getDocument() == null ? null : theElement.getDocument().getStyle();
		ObservableSet<StyleAttribute<?>> parentAtts = ObservableSet
			.flattenValue(theElement.getParent().mapV(p -> p.getStyle().attributes())).filterStatic(att -> att.isInherited());
		ObservableCollection<StyleAttribute<?>> flattened;
		if (sheet == null)
			flattened = ObservableCollection.flattenCollections(localAttrs, parentAtts);
		else
			flattened = ObservableCollection.flattenCollections(localAttrs, parentAtts, sheet.attributes());
		return ObservableSet.unique(flattened, Object::equals);
	}

	@Override
	public boolean isSet(StyleAttribute<?> attr) {
		QuickStyle localStyle = theElement.atts().get(StyleAttributes.style);
		if (localStyle != null && localStyle.isSet(attr))
			return true;
		StyleSheet sheet = theElement.getDocument().getStyle();
		if (sheet.isSet(getCondition(), attr))
			return true;
		if (attr.isInherited()) {
			QuickElement parent = theElement.getParent().get();
			// TODO not exactly right, because the parent's style may not have this style's extra states
			if (parent != null && parent.getStyle().isSet(attr))
				return true;
		}
		return false;
	}

	@Override
	public <T> ObservableValue<T> get(StyleAttribute<T> attr, boolean withDefault) {
		ObservableValue<QuickStyle> localStyle = theElement.atts().getHolder(StyleAttributes.style);
		ObservableValue<T> localValue = ObservableValue.flatten(localStyle.mapV(s -> s.get(attr, false)));
		if (attr.isInherited()) {
			ObservableValue<T> parentLocalValue = ObservableValue.flatten(theElement.getParent().mapV(p -> {
				if (p == null)
					return null;
				ObservableValue<QuickStyle> parentStyle = p.atts().getHolder(StyleAttributes.style);
				return ObservableValue.flatten(parentStyle.mapV(s -> s.get(attr, false)));
			}));
			localValue = ObservableValue.firstValue(attr.getType().getType(), null, null, localValue, parentLocalValue);
		}
		if (theElement.getDocument() == null)
			return localValue;
		StyleSheet sheet = theElement.getDocument().getStyle();
		ObservableValue<StyleConditionValue<T>> ssMatch = sheet.getBestMatch(getCondition(), attr);
		ObservableValue<T> ssValue;
		if (attr.isInherited()) {
			ObservableValue<StyleConditionValue<T>> parentSSMatch = ObservableValue.flatten(theElement.getParent().mapV(p -> {
				if (p == null)
					return null;
				StyleConditionInstance<?> pCondition = theExtraStates == null ? StyleConditionInstance.of(p)
					: StyleConditionInstance.of(p, theExtraStates);
				return sheet.getBestMatch(pCondition, attr);
			}));
			ssMatch = ssMatch.combineV(null, (ss, pSS) -> {
				if (ss == null && pSS == null)
					return null;
				else if (ss == null)
					return pSS;
				else if (pSS == null)
					return ss;
				int comp = ss.compareTo(pSS);
				if (comp <= 0)
					return ss;
				else
					return pSS;
			}, parentSSMatch, true);
		}
		if (withDefault)
			ssValue = ObservableValue.flatten(ssMatch, () -> attr.getDefault());
		else
			ssValue = ObservableValue.flatten(ssMatch);
		return ObservableValue.firstValue(attr.getType().getType(), null, null, localValue, ssValue);
	}
}
