/* Created by Andrew Mar 19, 2010 */
package org.quick.core.mgr;

import org.quick.core.QuickDocument;
import org.quick.core.QuickElement;
import org.quick.core.QuickEnvironment;

/** Represents an error, warning, or information message attached to a Quick element */
public class QuickMessage {
	/** The types of element messages available */
	public static enum Type {
		/** Represents a debugging message */
		DEBUG,
		/** Represents an information message */
		INFO,
		/** Represents a warning message */
		WARNING,
		/** Represents an error message */
		ERROR,
		/** Represents a fatal error which effectively disables an element or document */
		FATAL;
	}

	/** The Quick environment that the message is for */
	public final QuickEnvironment environment;

	/** The Quick document that the message is for */
	public final QuickDocument document;

	/** The Quick element that the message is for */
	public final QuickElement element;

	/** The type of this message */
	public final Type type;

	/** The stage in the element's life cycle at which this message occurred */
	public final String stage;

	/** The text of the message, describing the problem */
	public final String text;

	/** The exception that may have caused this message */
	public final Throwable exception;

	private java.util.Map<String, Object> theParams;

	private QuickMessage(QuickEnvironment env, QuickDocument doc, QuickElement anElement, Type aType, String aStage, String aText,
		Throwable anException,
		Object... params) {
		if(env == null)
			throw new NullPointerException("A Quick message cannot be created without an environment");
		if(aType == null)
			throw new NullPointerException("A Quick message cannot be created without a type");
		if(aStage == null)
			throw new NullPointerException("A Quick message cannot be created without a creation stage");
		if(aText == null)
			throw new NullPointerException("A Quick message cannnot be created without a description");
		environment = env;
		document = doc;
		element = anElement;
		type = aType;
		stage = aStage;
		text = aText;
		exception = anException;
		if(params != null && params.length > 0) {
			theParams = new java.util.HashMap<>();
			if(params.length % 2 != 0)
				throw new IllegalArgumentException("message params must be in format [name, value, name, value, ...]"
					+ "--odd argument count not allowed");
			theParams = new java.util.HashMap<>();
			for(int d = 0; d < params.length; d += 2) {
				if(!(params[d] instanceof String))
					throw new IllegalArgumentException("message params must be in format [name, value, name, value, ...]"
						+ "--even indices must be strings");
				theParams.put((String) params[d], params[d + 1]);
			}
		}
	}

	QuickMessage(QuickEnvironment env, Type aType, String aStage, String aText, Throwable anException, Object... params) {
		this(env, null, null, aType, aStage, aText, anException, params);
	}

	QuickMessage(QuickDocument doc, Type aType, String aStage, String aText, Throwable anException, Object... params) {
		this(doc.getEnvironment(), doc, null, aType, aStage, aText, anException, params);
	}

	QuickMessage(QuickElement anElement, Type aType, String aStage, String aText, Throwable anException, Object... params) {
		this(anElement.getDocument().getEnvironment(), anElement.getDocument(), anElement, aType, aStage, aText, anException, params);
	}

	/**
	 * @param param The name of the parameter to get
	 * @return The value of the given parameter
	 */
	public Object getParam(String param) {
		if(theParams == null)
			return null;
		return theParams.get(param);
	}

	/** @return All parameter names with values in this message */
	public String [] getParams() {
		if(theParams == null)
			return new String[0];
		return theParams.keySet().toArray(new String[theParams.size()]);
	}

	@Override
	public final boolean equals(Object o) {
		return super.equals(o);
	}

	@Override
	public final int hashCode() {
		return super.hashCode();
	}

	/** Prints out a representation of the path to the problem element and the description of the error */
	@Override
	public String toString() {
		QuickElement el = element;
		String ret = "";
		if (el != null) {
			ret = (el.getNamespace() != null ? el.getNamespace() + ":" : "") + el.getTagName();
			el = el.getParent().get();
			while (el != null) {
				ret = (el.getNamespace() != null ? el.getNamespace() + ":" : "") + el.getTagName() + "/" + ret;
				el = el.getParent().get();
			}
		}
		return ret + ": " + text;
	}

	/**
	 * @return A full description of the message, including the path to the problem element, the description of the error, any parameters in
	 *         the message, and the stack trace of the exception that caused the problem
	 */
	public String toFullString() {
		String ret = toString();
		if(theParams != null)
			for(java.util.Map.Entry<String, Object> param : theParams.entrySet())
				ret += "\n" + param.getKey() + "=" + param.getValue();
		if(exception != null)
			ret += "\n" + exception;
		return ret;
	}
}
