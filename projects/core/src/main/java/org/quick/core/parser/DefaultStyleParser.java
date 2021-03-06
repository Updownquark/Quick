package org.quick.core.parser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom2.Element;
import org.observe.ObservableValue;
import org.quick.core.*;
import org.quick.core.mgr.QuickMessageCenter;
import org.quick.core.mgr.QuickState;
import org.quick.core.prop.DefaultExpressionContext;
import org.quick.core.style.*;
import org.quick.util.QuickUtils;

/** Parses .qss XML style files for Quick */
public class DefaultStyleParser implements QuickStyleParser {
	private static final Pattern STATE_PATTERN = Pattern.compile("[a-zA-Z]+[a-zA-Z0-9-]*");
	private final QuickEnvironment theEnvironment;

	/** @param env The environment that this parser is for */
	public DefaultStyleParser(QuickEnvironment env) {
		theEnvironment = env;
	}

	@Override
	public QuickEnvironment getEnvironment() {
		return theEnvironment;
	}

	@Override
	public ImmutableStyleSheet parseStyleSheet(URL location, QuickToolkit toolkit, QuickPropertyParser parser, QuickClassView cv,
		QuickMessageCenter msg) throws IOException, QuickParseException {
		Element rootEl;
		try {
			rootEl = new org.jdom2.input.SAXBuilder().build(new java.io.InputStreamReader(location.openStream())).getRootElement();
		} catch (org.jdom2.JDOMException e) {
			throw new QuickParseException("Could not parse quick style XML for " + location, e);
		}

		ExpressionContextStack stack = new ExpressionContextStack(theEnvironment, toolkit);
		stack.push();
		addNamespaces(rootEl, location, stack, msg);
		QuickParseEnv parseEnv = new SimpleParseEnv(new QuickClassView(theEnvironment, cv, toolkit), msg,
			DefaultExpressionContext.build().build()); // TODO time variables
		ImmutableStyleSheet.Builder builder = ImmutableStyleSheet.build(msg);
		for (Element child : rootEl.getChildren())
			parseStyleElement(child, location, parser, parseEnv, stack, builder);
		return builder.build();
	}

	private void addNamespaces(Element xml, URL location, ExpressionContextStack stack, QuickMessageCenter msg) {
		for (org.jdom2.Namespace ns : xml.getNamespacesIntroduced()) {
			QuickToolkit toolkit;
			try {
				toolkit = theEnvironment.getToolkit(QuickUtils.resolveURL(location, ns.getURI()));
			} catch (MalformedURLException e) {
				msg.error("Invalid URL \"" + ns.getURI() + "\" for toolkit at namespace " + ns.getPrefix(), e);
				continue;
			} catch (IOException e) {
				msg.error("Could not read toolkit " + ns.getPrefix() + ":" + ns.getURI(), e);
				continue;
			} catch (QuickParseException e) {
				msg.error("Could not parse toolkit " + ns.getPrefix() + ":" + ns.getURI(), e);
				continue;
			} catch (QuickException e) {
				msg.error("Could not resolve location of toolkit for namespace " + ns.getPrefix(), e);
				continue;
			}
			try {
				stack.top().getClassView().addNamespace(ns.getPrefix(), toolkit);
			} catch (QuickException e) {
				throw new IllegalStateException("Should not happen", e);
			}
		}
	}

	private void parseStyleElement(Element xml, URL location, QuickPropertyParser parser, QuickParseEnv parseEnv,
		ExpressionContextStack stack, ConditionalStyleSetter setter) {
		stack.push();
		addNamespaces(xml, location, stack, parseEnv.msg());
		String name = xml.getAttributeValue("name");
		switch (xml.getName()) {
		case "type":
			Class<? extends QuickElement> type;
			try {
				type = stack.top().getClassView().loadMappedClass(name, QuickElement.class);
			} catch (QuickException e) {
				type = null;
				parseEnv.msg().error("Could not load element type", e, "type", name);
			}
			if (type != null) {
				try {
					stack.setType(type);
				} catch (QuickParseException e) {
					parseEnv.msg().error(e.getMessage(), e, "type", type);
				}
			}
			for (Element child : xml.getChildren())
				parseStyleElement(child, location, parser, parseEnv, stack, setter);
			break;
		case "state":
			StateCondition state;
			try {
				state = parseState(name, stack);
			} catch (QuickParseException e) {
				state = null;
				parseEnv.msg().error(e.getMessage(), e, "state", name);
			}
			if (state != null)
				stack.setState(state);
			for (Element child : xml.getChildren())
				parseStyleElement(child, location, parser, parseEnv, stack, setter);
			break;
		case "group":
			stack.addGroup(name);
			for (Element child : xml.getChildren())
				parseStyleElement(child, location, parser, parseEnv, stack, setter);
			break;
		case "attach-point":
			try {
				stack.addAttachPoint(name);
			} catch (QuickParseException e) {
				parseEnv.msg().error(e.getMessage(), e, "attach-point", name);
			}
			for (Element child : xml.getChildren())
				parseStyleElement(child, location, parser, parseEnv, stack, setter);
			break;
		case "domain":
			for (Element child : xml.getChildren()) {
				if (!"attr".equals(child.getName())) {
					parseEnv.msg().error("Only attr elements are allowed under domain elements in style sheets", "name", child.getName());
					continue;
				}
				if (!child.getChildren().isEmpty())
					parseEnv.msg().error("attr elements are not allowed any children");
				String attr = child.getAttributeValue("name");
				String valueStr = child.getAttributeValue("value");
				applyStyleValue(name, attr, valueStr, parser, parseEnv, setter, stack);
			}
			break;
		case "attr":
			String domain = xml.getAttributeValue("domain");
			String valueStr = xml.getAttributeValue("value");
			applyStyleValue(domain, name, valueStr, parser, parseEnv, setter, stack);
			if (!xml.getChildren().isEmpty())
				parseEnv.msg().error("attr elements are not allowed any children");
		}
		stack.pop();
	}

	private StateCondition parseState(String name, ExpressionContextStack stack) throws QuickParseException {
		return parseState(name, stack, new int[] { 0 }, new int[] { name.length() });
	}

	private StateCondition parseState(String name, ExpressionContextStack stack, int[] start, int[] end) throws QuickParseException {
		if (start[0] == end[0])
			return null;
		StateCondition total = null;
		boolean and = false;
		boolean or = false;
		while (start[0] != end[0]) {
			StateCondition next = parseNextState(name, stack, start, end);
			if (total == null)
				total = next;
			else if (and) {
				total = total.and(next);
			} else if (or) {
				total = total.or(next);
			}
			and = or = false;

			while (start[0] != end[0] && Character.isWhitespace(name.charAt(start[0])))
				start[0]++;
			if (start[0] != end[0]) {
				switch (name.charAt(start[0])) {
				case '&':
					and = true;
					start[0]++;
					break;
				case '|':
					or = true;
					start[0]++;
					break;
				default:
					throw new QuickParseException("Unrecognized state expression at char " + start[0]);
				}
			}
		}
		return total;
	}

	private StateCondition parseNextState(String name, ExpressionContextStack stack, int[] start, int[] end) throws QuickParseException {
		while (start[0] != end[0] && Character.isWhitespace(name.charAt(start[0])))
			start[0]++;
		switch (name.charAt(start[0])) {
		case '(':
			int endParen = getEndParen(name);
			if (endParen < 0)
				throw new QuickParseException("Parentheses do not match");
			StateCondition next = parseState(name, stack, new int[] { start[0] + 1 }, new int[] { endParen });
			start[0] = endParen + 1;
			return next;
		case '!':
			start[0]++;
			return parseNextState(name, stack, start, end).not();
		default:
			Matcher matcher = STATE_PATTERN.matcher(name.substring(start[0], end[0]));
			if (!matcher.lookingAt()) {
				throw new QuickParseException("Unrecognized state expression at char " + start[0]);
			}
			QuickState state = findState(matcher.group(), stack);
			start[0] += matcher.end();
			return StateCondition.forState(state);
		}
	}

	private int getEndParen(String name) {
		int depth = 1;
		for (int c = 1; c < name.length(); c++) {
			if (name.charAt(c) == '(')
				depth++;
			else if (name.charAt(c) == ')') {
				depth--;
				if (depth == 0)
					return c;
			}
		}
		return -1;
	}

	private QuickState findState(String name, ExpressionContextStack stack) throws QuickParseException {
		Class<? extends QuickElement> type = stack.getType();
		QuickState ret = null;
		boolean found = false;
		for (QuickState state : org.quick.core.tags.QuickTagUtils.getStatesFor(type))
			if (state.getName().equals(name)) {
				found = true;
				if (ret == null)
					ret = state;
			}
		if (!found)
			throw new QuickParseException("Element type " + type.getName() + " does not support state \"" + name + "\"");
		return ret;
	}

	private void applyStyleValue(String domainName, String attrName, String valueStr, QuickPropertyParser parser, QuickParseEnv parseEnv,
		ConditionalStyleSetter setter, ExpressionContextStack stack) {
		String ns;
		int nsIdx = domainName.indexOf(':');
		if (nsIdx >= 0) {
			ns = domainName.substring(0, nsIdx).trim();
			domainName = domainName.substring(nsIdx + 1).trim();
		} else
			ns = null;

		StyleDomain domain;
		try {
			domain = StyleParsingUtils.getStyleDomain(ns, domainName, stack.top().getClassView());
		} catch (QuickException e) {
			parseEnv.msg().error("Could not get style domain " + domainName, e);
			return;
		}

		StyleAttribute<?> styleAttr = null;
		for (StyleAttribute<?> attrib : domain)
			if (attrib.getName().equals(attrName)) {
				styleAttr = attrib;
				break;
			}

		if (styleAttr == null) {
			parseEnv.msg().warn("No such attribute " + attrName + " in domain " + domainName);
			return;
		}

		applyParsedValue(parser, parseEnv, styleAttr, valueStr, setter, stack.asCondition());
	}

	private static <T> void applyParsedValue(QuickPropertyParser parser, QuickParseEnv env, StyleAttribute<T> styleAttr, String valueStr,
		ConditionalStyleSetter setter, StyleCondition condition) {
		ObservableValue<? extends T> value;
		try {
			value = parser.parseProperty(styleAttr, env, valueStr);
		} catch (org.quick.core.QuickException e) {
			env.msg().warn("Value " + valueStr + " is not appropriate for style attribute " + styleAttr.getName() + " of domain "
				+ styleAttr.getDomain().getName(), e);
			return;
		}
		setter.set(styleAttr, condition, value);
	}
}
