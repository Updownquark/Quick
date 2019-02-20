package org.quick.core;

import org.qommons.Transaction;
import org.quick.core.layout.Orientation;
import org.quick.core.layout.SizeGuide;
import org.quick.core.mgr.AttributeManager2.AttributeValue;
import org.quick.core.prop.QuickAttribute;
import org.quick.core.prop.QuickPropertyType;

/** A simple container element that lays its children out using an implementation of {@link QuickLayout} */
public class LayoutContainer extends QuickElement {
	/** The attribute that specifies the layout type for a layout container */
	public static QuickAttribute<QuickLayout> LAYOUT_ATTR = QuickAttribute
		.build("layout", QuickPropertyType.forTypeInstance(QuickLayout.class, null)).build();

	/** Creates a layout container */
	public LayoutContainer() {
		QuickLayout defLayout = getDefaultLayout();
		AttributeValue<QuickLayout> layoutAtt;
		try {
			layoutAtt = atts().accept(LAYOUT_ATTR, this, a -> a.init(defLayout).required());
			life().runWhen(() -> {
				layoutAtt.value().act(layout -> layout.install(LayoutContainer.this, layoutAtt.changes().noInit()));
			}, QuickConstants.CoreStage.STARTUP.toString(), -1);
		} catch (IllegalArgumentException e) {
			msg().error("Could not set default layout", e, "layout", defLayout);
		}
	}

	/**
	 * Allows types to specify their default layout
	 *
	 * @return The default layout for this container. Null by default.
	 */
	protected QuickLayout getDefaultLayout() {
		return null;
	}

	/** @return The QuickLayout that lays out this container's children */
	public QuickLayout getLayout() {
		return atts().get(LAYOUT_ATTR).get();
	}

	@Override
	public SizeGuide getSizer(Orientation orientation) {
		QuickLayout layout = getLayout();
		if (layout != null) {
			try (Transaction t = getPhysicalChildren().lock(false, null)) {
				return layout.getSizer(this, getPhysicalChildren(), orientation);
			}
		} else
			return super.getSizer(orientation);
	}

	@Override
	public void doLayout() {
		if (bounds().isEmpty())
			return;
		QuickLayout layout = getLayout();
		if (layout != null) {
			try (Transaction t = getPhysicalChildren().lock(false, null)) {
				layout.layout(this, getPhysicalChildren());
			}
		}
		super.doLayout();
	}
}
