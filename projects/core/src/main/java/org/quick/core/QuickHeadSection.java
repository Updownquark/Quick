package org.quick.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qommons.Sealable;
import org.quick.core.model.QuickAppModel;
import org.quick.core.style.sheet.ParsedStyleSheet;

/** Metadata for a MUIS document */
public class QuickHeadSection implements Sealable {
	private String theTitle;

	private List<ParsedStyleSheet> theStyleSheets;

	private Map<String, QuickAppModel> theModels;

	private boolean isSealed;

	/** Creates a head section */
	public QuickHeadSection() {
		theStyleSheets = new java.util.ArrayList<>();
		theModels = new HashMap<>(2);
	}

	@Override
	public boolean isSealed() {
		return isSealed;
	}

	@Override
	public void seal() {
		if(!isSealed)
			theStyleSheets = java.util.Collections.unmodifiableList(theStyleSheets);
		isSealed = true;
	}

	/** @return The title for the MUIS document */
	public String getTitle() {
		return theTitle;
	}

	/** @param title The title for the document */
	public void setTitle(String title) {
		if(isSealed)
			throw new SealedException(this);
		theTitle = title;
	}

	/** @return All style sheets specified in this head section */
	public List<ParsedStyleSheet> getStyleSheets() {
		return theStyleSheets;
	}

	/**
	 * @param name The name of the model to get
	 * @return The model of the given name specified in this head section, or null if no so-named model was specified
	 */
	public QuickAppModel getModel(String name) {
		return theModels.get(name);
	}

	/**
	 * @param name The name of the model to add
	 * @param model The model specified in this head section under the given name
	 */
	public void addModel(String name, QuickAppModel model) {
		if(isSealed)
			throw new SealedException(this);
		theModels.put(name, model);
	}

	/** @return The names of all models in this document */
	public String [] getModels() {
		return theModels.keySet().toArray(new String[theModels.size()]);
	}
}
