package org.muis.core;

import org.muis.core.layout.SizeGuide;

/** A simple container element that lays its children out using an implementation of {@link MuisLayout} */
public class LayoutContainer extends MuisElement {
	/** The attribute that specifies the layout type for a layout container */
	public static MuisAttribute<MuisLayout> LAYOUT_ATTR = new MuisAttribute<>("layout", new MuisProperty.MuisTypeInstanceProperty<>(
		MuisLayout.class));

	private MuisLayout theLayout;

	/** Creates a layout container */
	public LayoutContainer() {
		MuisLayout defLayout = getDefaultLayout();
		life().runWhen(() -> {
			try {
				atts().require(this, LAYOUT_ATTR, defLayout).act(event -> {
					setLayout(event.getValue());
				});
			} catch(MuisException e) {
				msg().error("Could not set default layout", e, "layout", defLayout);
			}
		}, MuisConstants.CoreStage.INIT_CHILDREN.toString(), 1);
	}

	/**
	 * Allows types to specify their default layout
	 *
	 * @return The default layout for this container. Null by default.
	 */
	protected MuisLayout getDefaultLayout() {
		return null;
	}

	/** @return The MuisLayout that lays out this container's children */
	public MuisLayout getLayout() {
		return theLayout;
	}

	/** @param layout The MuisLayout to lay out this container's children */
	public void setLayout(MuisLayout layout) {
		if(theLayout != null)
			theLayout.remove(this);
		theLayout = layout;
		if(theLayout != null)
			theLayout.initChildren(this, getChildren().toArray());
		relayout(false);
	}

	@Override
	public SizeGuide getWSizer() {
		if(theLayout != null)
			return theLayout.getWSizer(this, getChildren().toArray());
		else
			return super.getWSizer();
	}

	@Override
	public SizeGuide getHSizer() {
		if(theLayout != null)
			return theLayout.getHSizer(this, getChildren().toArray());
		else
			return super.getHSizer();
	}

	@Override
	public void doLayout() {
		if(theLayout != null)
			theLayout.layout(this, getChildren().toArray());
		super.doLayout();
	}

	@Override
	protected void registerChild(MuisElement child) {
		super.registerChild(child);
		if(theLayout != null)
			theLayout.childAdded(this, child);
	}

	@Override
	protected void unregisterChild(MuisElement child) {
		super.unregisterChild(child);
		if(theLayout != null)
			theLayout.childRemoved(this, child);
	}
}
