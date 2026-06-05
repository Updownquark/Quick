package org.observe.quick.swing;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import org.observe.Observable;
import org.qommons.Causable;

public interface QuickCustomDrawer {
	void draw(QuickDrawScreen screen, Rectangle graphicsBounds);

	DrawOpacity getOpacity(Point2D.Float point);

	Observable<? extends Causable> getUpdate();
}
