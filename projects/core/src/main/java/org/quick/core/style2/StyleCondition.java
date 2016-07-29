package org.quick.core.style2;

import java.util.*;

import org.observe.ObservableValue;
import org.quick.core.QuickElement;
import org.quick.core.QuickTemplate;
import org.quick.core.style.attach.StyleAttributes;

public class StyleCondition implements Comparable<StyleCondition> {
	private final StateCondition theState;
	private final List<QuickTemplate.AttachPoint> theRolePath;
	private final Set<String> theGroups;
	private final Class<? extends QuickElement> theType;

	private StyleCondition(StateCondition state, List<QuickTemplate.AttachPoint> rolePath, Set<String> groups,
		Class<? extends QuickElement> type) {
		theState = state;
		theRolePath = Collections.unmodifiableList(rolePath);
		theGroups = Collections.unmodifiableSet(groups);
		if (!QuickElement.class.isAssignableFrom(type))
			throw new IllegalArgumentException("The type of a condition must extend " + QuickElement.class.getName());
		theType = type;
	}

	public StateCondition getState() {
		return theState;
	}

	public List<QuickTemplate.AttachPoint> getRolePath() {
		return theRolePath;
	}

	public Set<String> getGroups() {
		return theGroups;
	}

	public Class<? extends QuickElement> getType() {
		return theType;
	}

	@Override
	public int compareTo(StyleCondition o) {
		int compare;
		if(theState!=null){
			if(o.theState==null)
				return -1;
			compare=theState.compareTo(o.theState);
			if(compare!=0)
				return compare;
		} else if(o.theState!=null)
			return 1;

		if (theRolePath.size() != o.theRolePath.size())
			return o.theRolePath.size() - theRolePath.size();

		if (theGroups.size() != o.theGroups.size())
			return o.theGroups.size() - theGroups.size();

		if (theType != o.theType) {
			int depth = getTypeDepth();
			int oDepth = o.getTypeDepth();
			if (depth != oDepth)
				return oDepth - depth;
		}

		return 0;
	}

	public int getTypeDepth() {
		int depth = 0;
		Class<?> type = theType;
		while (type != QuickElement.class) {
			depth++;
			type = type.getSuperclass();
		}
		return depth;
	}

	public ObservableValue<Boolean> matches(QuickElement element) {
		if (!theType.isInstance(element))
			return ObservableValue.constant(false);

		ObservableValue<Boolean> stateMatches;
		if (theState == null)
			stateMatches = ObservableValue.constant(true);
		else
			stateMatches = theState.observeMatches(element.state().activeStates());

		ObservableValue<Boolean> groupMatches;
		if (theGroups.isEmpty())
			groupMatches = ObservableValue.constant(true);
		else
			groupMatches = element.atts().getHolder(StyleAttributes.GROUP_ATTRIBUTE)
				.mapV(groups -> Arrays.asList(groups).containsAll(theGroups));

		ObservableValue<Boolean> rolePathMatches;
		if (theRolePath.isEmpty())
			rolePathMatches = ObservableValue.constant(true);
		else // TODO Make this observable. Probably easier when element children are observable collections
			rolePathMatches = ObservableValue.constant(rolePathMatches(element, theRolePath.size() - 1));

		return stateMatches.combineV((b1, b2, b3) -> b1 && b2 && b3, groupMatches, rolePathMatches);
	}

	private boolean rolePathMatches(QuickElement element, int index) {
		QuickTemplate.AttachPoint role = theRolePath.get(index);
		if (element.atts().get(role.template.role) != role)
			return false;
		if (index == 0)
			return true;
		QuickElement owner = element.getParent();
		while (owner != null && !(owner instanceof QuickTemplate && ((QuickTemplate) owner).getTemplate() == role.template))
			owner = owner.getParent();
		if (owner == null)
			return false;
		return rolePathMatches(owner, index - 1);
	}

	@Override
	public int hashCode() {
		return Objects.hash(theState, theRolePath, theGroups, theType);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof StyleCondition))
			return false;
		StyleCondition c = (StyleCondition) obj;
		return Objects.equals(theState, c.theState) && theRolePath.equals(c.theRolePath) && theGroups.equals(c.theGroups)
			&& theType == c.theType;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(theType.getSimpleName());

		for (QuickTemplate.AttachPoint role : theRolePath)
			str.append('.').append(role.name);

		if (!theGroups.isEmpty()) {
			str.append('[');
			boolean first = true;
			for (String group : theGroups) {
				if (!first)
					str.append(',');
				first = false;
				str.append(group);
			}

			str.append(']');
		}

		if (theState != null) {
			str.append('(').append(theState).append(')');
		}

		return str.toString();
	}

	public static Builder build(Class<? extends QuickElement> type) {
		return new Builder(type);
	}

	public static class Builder {
		private final Class<? extends QuickElement> theType;

		private Builder(Class<? extends QuickElement> type) {
			if (!QuickElement.class.isAssignableFrom(type))
				throw new IllegalArgumentException("The type of a condition must extend " + QuickElement.class.getName());
			theType = type;
		}

		public StyleCondition build() {}
	}
}
