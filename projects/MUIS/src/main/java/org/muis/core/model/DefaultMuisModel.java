package org.muis.core.model;

import java.util.HashMap;
import java.util.Map;

import org.muis.rx.ObservableValue;

/** The default (typically XML-specified) implementation for MuisAppModel */
public class DefaultMuisModel implements MuisAppModel, Cloneable, prisms.util.Sealable {
	private boolean isSealed;

	private Map<String, MuisAppModel> theSubModels;

	private Map<String, MuisWidgetModel> theWidgetModels;

	private Map<String, ObservableValue<?>> theValues;

	private Map<String, AggregateActionListener> theActions;

	/** Creates the model */
	public DefaultMuisModel() {
		theSubModels = new HashMap<>(2);
		theWidgetModels = new HashMap<>(2);
		theValues = new HashMap<>(2);
		theActions = new HashMap<>(2);
	}

	@Override
	public boolean isSealed() {
		return isSealed;
	}

	@Override
	public void seal() {
		theSubModels = java.util.Collections.unmodifiableMap(theSubModels);
		theWidgetModels = java.util.Collections.unmodifiableMap(theWidgetModels);
		theValues = java.util.Collections.unmodifiableMap(theValues);
		isSealed = true;
	}

	/** @return The sub model map for this model. This map is modifiable if this model has not been sealed yet. */
	public Map<String, MuisAppModel> subModels() {
		return theSubModels;
	}

	/** @return The widget model map for this model. This map is modifiable if this model has not been sealed yet. */
	public Map<String, MuisWidgetModel> widgetModels() {
		return theWidgetModels;
	}

	/** @return The value map for this model. This map is modifiable if this model has not been sealed yet. */
	public Map<String, ObservableValue<?>> values() {
		return theValues;
	}

	/**
	 * @param action The action to listen for
	 * @param listener The listen to be notified when the action occurs
	 */
	public void addActionListener(String action, MuisActionListener listener) {
		if(isSealed)
			throw new SealedException(this);
		if(listener == null)
			return;
		AggregateActionListener agg = theActions.get(action);
		if(agg == null) {
			agg = new AggregateActionListener();
			theActions.put(action, agg);
		}
		agg.addListener(listener);
	}

	/**
	 * @param action The action to remove the listener for
	 * @param listener The listener to stop notification for
	 */
	public void removeActionListener(String action, MuisActionListener listener) {
		if(isSealed)
			throw new SealedException(this);
		if(listener == null)
			return;
		AggregateActionListener agg = theActions.get(action);
		if(agg == null)
			return;
		agg.removeListener(listener);
	}

	@Override
	public MuisAppModel getSubModel(String name) {
		return theSubModels.get(name);
	}

	@Override
	public <T extends MuisWidgetModel> T getWidgetModel(String name, Class<T> modelType) {
		MuisWidgetModel model = theWidgetModels.get(name);
		if(model == null)
			return null;
		if(!modelType.isInstance(model))
			throw new ClassCastException("Widget model \"" + name + "\" is of type " + model.getClass().getName() + ", not "
				+ modelType.getName());
		return modelType.cast(model);
	}

	@Override
	public <T> ObservableValue<? extends T> getValue(String name, Class<T> type) {
		ObservableValue<?> value = theValues.get(name);
		if(type != null && !value.getType().canAssignTo(type))
			throw new ClassCastException("Value \"" + name + "\" is type \"" + value.getType().getName() + "\", not \"" + type.getName()
				+ "\"");
		return (ObservableValue<? extends T>) value;
	}

	@Override
	public MuisActionListener getAction(String name) {
		return theActions.get(name);
	}

	@Override
	public DefaultMuisModel clone() {
		DefaultMuisModel ret;
		try {
			ret = (DefaultMuisModel) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
		ret.isSealed = false;
		ret.theSubModels = new HashMap<>(theSubModels);
		ret.theWidgetModels = new HashMap<>(theWidgetModels);
		ret.theValues = new HashMap<>(theValues);
		ret.theActions = new HashMap<>(theActions);
		return ret;
	}
}
