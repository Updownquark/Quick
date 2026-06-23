package org.observe.quick.swing;

import java.awt.Component;

import org.observe.Observable;
import org.observe.quick.QuickWidgetExport;

public interface SwingWidgetExport<E extends QuickWidgetExport> {
	void initialize(E export, Component c, Observable<?> until);
}
