/* Created Feb 23, 2009 by Andrew Butler */
package org.quick.core;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.qommons.ArrayUtils;
import org.quick.core.event.*;
import org.quick.core.mgr.QuickMessageCenter;
import org.quick.core.prop.DefaultExpressionContext;
import org.quick.core.prop.ExpressionContext;
import org.quick.core.style.BackgroundStyle;
import org.quick.core.style.DocumentStyleSheet;

import com.google.common.reflect.TypeToken;

/** Contains all data pertaining to a Quick application */
public class QuickDocument implements QuickParseEnv {
	/** The different policies this document can take with regards to scrolling events */
	public static enum ScrollPolicy {
		/**
		 * With this policy, scroll events will fire as if the event came from the mouse pointer. The position fields will represent the
		 * position of the pointer when the scroll event was fired.
		 */
		MOUSE,
		/**
		 * With this policy, scroll events will fire as if the event came from the upper-left corner of the currently focused widget. The
		 * position fields will represent the (0, 0) position relative to that widget.
		 */
		FOCUS,
		/**
		 * With this policy, scroll events generated from a mouse wheel will fire the same as {@link #MOUSE}, while scroll events generated
		 * from the keyboard will fire the same as {@link #FOCUS}.
		 */
		MIXED;
	}

	/** Allows a Quick document to retrieve graphics to draw itself on demand */
	public interface GraphicsGetter {
		/** @return The graphics object that this document should use at the moment */
		java.awt.Graphics2D getGraphics();

		/** Informs this getter that its graphics have been updated */
		void updated();

		/** @param cursor The cursor to set over the document */
		void setCursor(Cursor cursor);
	}

	/** A listener to be notified when the rendering changes for a Quick document */
	public interface RenderListener {
		/** @param doc The Quick document whose rendering was changed */
		void renderUpdate(QuickDocument doc);
	}

	private final QuickEnvironment theEnvironment;

	private final java.net.URL theLocation;

	private final ExpressionContext theContext;

	private final Observable<?> theDispose;

	private QuickClassView theClassView;

	private java.awt.Toolkit theAwtToolkit;

	private QuickHeadSection theHead;

	private BodyElement theRoot;

	private QuickMessageCenter theMessageCenter;

	private org.quick.core.style.DocumentStyleSheet theDocumentStyle;

	private ScrollPolicy theScrollPolicy;

	private QuickElement theFocus;
	private ObservableValue<QuickElement> theObservableFocus;
	private Observer<ObservableValueEvent<QuickElement>> theFocusController;

	private QuickEventPositionCapture theMouseTarget;
	private ObservableValue<QuickEventPositionCapture> theObservableTarget;
	private Observer<ObservableValueEvent<QuickEventPositionCapture>> theTargetController;

	private boolean hasMouse;

	private int theMouseX;

	private int theMouseY;

	private Set<MouseEvent.ButtonType> thePressedButtons;

	private Set<KeyBoardEvent.KeyCode> thePressedKeys;

	private final Object theButtonsLock;

	private final Object theKeysLock;

	private volatile QuickRendering theRendering;

	private GraphicsGetter theGraphics;

	private GraphicsGetter theDebugGraphics;

	private java.util.Collection<RenderListener> theRenderListeners;

	/**
	 * Creates a document
	 *
	 * @param env The environment for the document
	 * @param location The location of the file that this document was generated from
	 * @param head The head section for this document
	 * @param classView The class view for the document
	 * @param dispose The observable that will fire to dispose of this document
	 */
	public QuickDocument(QuickEnvironment env, java.net.URL location, QuickHeadSection head, QuickClassView classView,
		Observable<?> dispose) {
		theEnvironment = env;
		theLocation = location;
		theHead = head;
		theClassView = classView;
		theDispose = dispose;

		theContext = DefaultExpressionContext.build().withParent(env.getContext())//
			.withValueGetter(name -> {
				Object model= getHead().getModel(name);
				if(model instanceof ObservableValue)
					return (ObservableValue<?>) model;
				else if (model != null)
					return ObservableValue.constant(model);
				else
					return null;
			})//
			.build();
		theAwtToolkit = java.awt.Toolkit.getDefaultToolkit();
		theMessageCenter = new QuickMessageCenter(env, this, null);
		theDocumentStyle = DocumentStyleSheet.build(this);
		theScrollPolicy = ScrollPolicy.MOUSE;
		thePressedButtons = new org.qommons.ConcurrentHashSet<>();
		thePressedKeys = new org.qommons.ConcurrentHashSet<>();
		theButtonsLock = new Object();
		theKeysLock = new Object();
		theRoot = new BodyElement();
		theRenderListeners = new java.util.concurrent.ConcurrentLinkedQueue<>();

		theObservableFocus = new org.observe.DefaultObservableValue<QuickElement>() {
			@Override
			public TypeToken<QuickElement> getType() {
				return TypeToken.of(QuickElement.class);
			}

			@Override
			public QuickElement get() {
				return theFocus;
			}
		};
		theFocusController = ((org.observe.DefaultObservableValue<QuickElement>) theObservableFocus).control(null);
		theObservableTarget = new org.observe.DefaultObservableValue<QuickEventPositionCapture>() {
			@Override
			public TypeToken<QuickEventPositionCapture> getType() {
				return TypeToken.of(QuickEventPositionCapture.class);
			}

			@Override
			public QuickEventPositionCapture get() {
				return theMouseTarget;
			}
		};
		theTargetController = ((org.observe.DefaultObservableValue<QuickEventPositionCapture>) theObservableTarget).control(null);
		ObservableValue
			.flatten(theObservableTarget
				.mapV(target -> target == null ? null : target.getTarget().getElement().getStyle().get(BackgroundStyle.cursor)))
			.act(event -> {
				if (event.getValue() != null && theGraphics != null)
					theGraphics.setCursor(event.getValue());
			});
	}

	/** @return An observable that will fire when this document is disposed */
	public Observable<?> getDispose() {
		return theDispose;
	}

	/** @param graphics The getter for graphics to be redrawn when the rendering is updated */
	public void setGraphics(GraphicsGetter graphics) {
		theGraphics = graphics;
	}

	/** @return Graphics to be updated on repaint. May be null. */
	public java.awt.Graphics2D getGraphics() {
		return theGraphics == null ? null : theGraphics.getGraphics();
	}

	/** Informs the provider of this document's graphics that it has been updated */
	public void graphicsUpdated() {
		if (theGraphics != null)
			theGraphics.updated();
	}

	/** @param graphics The getter for graphics to be used in debugging (drawn piece-wise instead of in batches) */
	public void setDebugGraphics(GraphicsGetter graphics) {
		theDebugGraphics = graphics;
	}

	/** @return The graphics to be used in debugging */
	public java.awt.Graphics2D getDebugGraphics() {
		return theDebugGraphics == null ? null : theDebugGraphics.getGraphics();
	}

	/** Informs the provider of this document's debug graphics that it has been updated */
	public void debugGraphicsUpdated() {
		if (theDebugGraphics != null)
			theDebugGraphics.updated();
	}

	/** @param listener The listener to be notified when the rendering of this document changes */
	public void addRenderListener(RenderListener listener) {
		if(listener != null)
			theRenderListeners.add(listener);
	}

	/** @param listener The listener to stop notifying */
	public void removeRenderListener(RenderListener listener) {
		theRenderListeners.remove(listener);
	}

	/** @return The environment that this document was created in */
	public QuickEnvironment getEnvironment() {
		return theEnvironment;
	}

	@Override
	public QuickClassView cv() {
		return getClassView();
	}

	@Override
	public ExpressionContext getContext() {
		return theContext;
	}

	/** @return The class map that applies to the whole document */
	public QuickClassView getClassView() {
		return theClassView;
	}

	/** @return The location of the file that this document was generated from */
	public java.net.URL getLocation() {
		return theLocation;
	}

	/** @return The head section of this document */
	public QuickHeadSection getHead() {
		return theHead;
	}

	/** @return The style sheet for this document */
	public DocumentStyleSheet getStyle() {
		return theDocumentStyle;
	}

	/** @return The root element of the document */
	public BodyElement getRoot() {
		return theRoot;
	}

	/** Called to initialize the document after all the parsing and linking has been performed */
	public void postCreate() {
		theRoot.postCreate();
	}

	/** @return This document's message center */
	public QuickMessageCenter getMessageCenter() {
		return theMessageCenter;
	}

	/**
	 * Short-hand for {@link #getMessageCenter()}
	 *
	 * @return This document's message center
	 */
	@Override
	public QuickMessageCenter msg() {
		return getMessageCenter();
	}

	/** @return The element that has focus in this document */
	public ObservableValue<QuickElement> getFocus() {
		return theObservableFocus;
	}

	/** @return The policy that this document uses to dispatch scroll events */
	public ScrollPolicy getScrollPolicy() {
		return theScrollPolicy;
	}

	/** @param policy The policy that this document should use to dispatch scroll events */
	public void setScrollPolicy(ScrollPolicy policy) {
		theScrollPolicy = policy;
	}

	/**
	 * Sets the size that this document can render its content in
	 *
	 * @param width The width of the document size
	 * @param height The height of the document size
	 */
	public void setSize(int width, int height) {
		QuickEventQueue.get().scheduleEvent(new QuickEventQueue.ReboundEvent(theRoot, new java.awt.Rectangle(0, 0, width, height)), true);
	}

	/** @return The most recent rendering of this document */
	public QuickRendering getRender() {
		return theRendering;
	}

	void setRender(QuickRendering render) {
		theRendering = render;
		for(RenderListener listener : theRenderListeners)
			listener.renderUpdate(this);
	}

	/**
	 * Renders this Quick document in a graphics context
	 *
	 * @param graphics The graphics context to render in
	 */
	public void paint(java.awt.Graphics2D graphics) {
		QuickRendering rendering = theRendering;
		if(rendering != null)
			graphics.drawImage(rendering.getImage(), 0, 0, null);
	}

	/** @return Whether the mouse is over this document */
	public boolean hasMouse() {
		return hasMouse;
	}

	/**
	 * @return The x-coordinate of either the mouse's current position relative to the document (if {@link #hasMouse()} is true) or the
	 *         mouse's position where it exited the document (if {@link #hasMouse()} is false).
	 */
	public int getMouseX() {
		return theMouseX;
	}

	/**
	 * @return The y-coordinate of either the mouse's current position relative to the document (if {@link #hasMouse()} is true) or the
	 *         mouse's position where it exited the document (if {@link #hasMouse()} is false).
	 */
	public int getMouseY() {
		return getMouseY();
	}

	/**
	 * @param button The mouse button to check
	 * @return Whether the mouse button is currently pressed
	 */
	public boolean isButtonPressed(MouseEvent.ButtonType button) {
		return ArrayUtils.contains(thePressedButtons, button);
	}

	/** @return All mouse buttons that are currently pressed */
	public Set<MouseEvent.ButtonType> getPressedButtons() {
		return java.util.Collections.unmodifiableSet(thePressedButtons);
	}

	/**
	 * @param code The key code to check
	 * @return Whether the key with the given code is currently pressed
	 */
	public boolean isKeyPressed(KeyBoardEvent.KeyCode code) {
		return ArrayUtils.contains(thePressedKeys, code);
	}

	/** @return All key codes whose keys are currently pressed */
	public Set<KeyBoardEvent.KeyCode> getPressedKeys() {
		return java.util.Collections.unmodifiableSet(thePressedKeys);
	}

	/** @return Whether a shift button is currently pressed */
	public boolean isShiftPressed() {
		return isKeyPressed(KeyBoardEvent.KeyCode.SHIFT_LEFT) || isKeyPressed(KeyBoardEvent.KeyCode.SHIFT_RIGHT);
	}

	/** @return Whether a control button is currently pressed */
	public boolean isControlPressed() {
		return isKeyPressed(KeyBoardEvent.KeyCode.CTRL_LEFT) || isKeyPressed(KeyBoardEvent.KeyCode.CTRL_RIGHT);
	}

	/** @return Whether an alt button is currently pressed */
	public boolean isAltPressed() {
		return isKeyPressed(KeyBoardEvent.KeyCode.ALT_LEFT) || isKeyPressed(KeyBoardEvent.KeyCode.ALT_RIGHT);
	}

	/** @return Whether caps lock is toggled on at the moment */
	public boolean isCapsLocked() {
		return theAwtToolkit.getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK);
	}

	/** @return Whether num lock is toggled on at the moment */
	public boolean isNumLocked() {
		return theAwtToolkit.getLockingKeyState(java.awt.event.KeyEvent.VK_NUM_LOCK);
	}

	/** @return Whether scroll lock is toggled on at the moment */
	public boolean isScrollLocked() {
		return theAwtToolkit.getLockingKeyState(java.awt.event.KeyEvent.VK_SCROLL_LOCK);
	}

	/** @return Whether kana lock is toggled on at the moment (for Japanese keyboard layout) */
	public boolean isKanaLocked() {
		return theAwtToolkit.getLockingKeyState(java.awt.event.KeyEvent.VK_KANA_LOCK);
	}

	/**
	 * Emulates a mouse event on the document
	 *
	 * @param x The x-coordinate where the event occurred
	 * @param y The y-coordinate where the event occurred
	 * @param type The type of the event
	 * @param buttonType The button that caused the event
	 * @param clickCount The click count for the event
	 */
	public void mouse(int x, int y, MouseEvent.MouseEventType type, MouseEvent.ButtonType buttonType, int clickCount) {
		boolean oldHasMouse = hasMouse;
		hasMouse = type != MouseEvent.MouseEventType.exited;
		theMouseX = x;
		theMouseY = y;
		QuickRendering rendering = theRendering;
		if(rendering == null)
			return;
		QuickEventPositionCapture newCapture = rendering.capture(x, y);
		QuickEventPositionCapture oldCapture = theMouseTarget;
		theMouseTarget = newCapture;
		theTargetController.onNext(theObservableTarget.createChangeEvent(oldCapture, newCapture, null));
		MouseEvent evt = new MouseEvent(this, newCapture.getTarget().getElement(), type, buttonType, clickCount, thePressedButtons,
			thePressedKeys, newCapture);

		ArrayList<QuickEventQueue.Event> events = new ArrayList<>();

		switch (type) {
		case moved:
			// This means it moved within the document. We have to determine any elements that it might have exited or entered.
			if(oldHasMouse)
				mouseMove(oldCapture, newCapture, events);
			else {
				evt = new MouseEvent(this, newCapture.getTarget().getElement(), MouseEvent.MouseEventType.entered, buttonType, clickCount,
					thePressedButtons, thePressedKeys, newCapture);
				events.add(new QuickEventQueue.PositionQueueEvent(theRoot, evt, true));
			}
			break;
		case pressed:
			synchronized(theButtonsLock) {
				if(!thePressedButtons.contains(buttonType))
					thePressedButtons.add(buttonType);
			}
			focusByMouse(newCapture, evt, events);
			events.add(new QuickEventQueue.PositionQueueEvent(theRoot, evt, false));
			break;
		case released:
			synchronized(theButtonsLock) {
				thePressedButtons.remove(buttonType);
			}
			events.add(new QuickEventQueue.PositionQueueEvent(theRoot, evt, false));
			break;
		case clicked:
		case exited:
			events.add(new QuickEventQueue.PositionQueueEvent(theRoot, new MouseEvent(this, oldCapture.getTarget().getElement(), type,
				buttonType, clickCount, thePressedButtons, thePressedKeys, oldCapture), false));
			break;
		case entered:
			events.add(new QuickEventQueue.PositionQueueEvent(theRoot, evt, true));
			break;
		}
		for(QuickEventQueue.Event event : events)
			QuickEventQueue.get().scheduleEvent(event, true);
	}

	/** Checks the mouse's current position, firing necessary mouse events if it has moved relative to any elements */
	private void mouseMove(QuickEventPositionCapture oldCapture, QuickEventPositionCapture newCapture,
		java.util.List<QuickEventQueue.Event> events) {
		LinkedHashSet<QuickEventPositionCapture> oldSet = new LinkedHashSet<>();
		LinkedHashSet<QuickEventPositionCapture> newSet = new LinkedHashSet<>();
		LinkedHashSet<QuickEventPositionCapture> common = new LinkedHashSet<>();
		if(oldCapture != null)
			for(QuickEventPositionCapture mec : oldCapture.iterate(true))
				oldSet.add(mec);
		for(QuickEventPositionCapture mec : newCapture.iterate(true))
			if(oldSet.remove(mec))
				common.add(mec);
			else
				newSet.add(mec);
		// Remove child elements
		java.util.Iterator<QuickEventPositionCapture> iter = oldSet.iterator();
		while(iter.hasNext()) {
			QuickEventPositionCapture mec = iter.next();
			if(oldSet.contains(mec.getParent()))
				iter.remove();
		}
		iter = newSet.iterator();
		while(iter.hasNext()) {
			QuickEventPositionCapture mec = iter.next();
			if(newSet.contains(mec.getParent()))
				iter.remove();
		}
		// Fire exit events
		for(QuickEventPositionCapture mec : oldSet) {
			MouseEvent exit = new MouseEvent(this, mec.getTarget().getElement(), MouseEvent.MouseEventType.exited, null, 0,
				thePressedButtons, thePressedKeys, mec);
			events.add(new QuickEventQueue.PositionQueueEvent(exit.getElement(), exit, false));
		}
		for(QuickEventPositionCapture mec : common) {
			MouseEvent move = new MouseEvent(this, mec.getTarget().getElement(), MouseEvent.MouseEventType.moved, null, 0,
				thePressedButtons, thePressedKeys, mec);
			events.add(new QuickEventQueue.PositionQueueEvent(move.getElement(), move, false));
		}
		// Fire enter events
		for(QuickEventPositionCapture mec : newSet) {
			MouseEvent enter = new MouseEvent(this, mec.getTarget().getElement(), MouseEvent.MouseEventType.entered, null, 0,
				thePressedButtons, thePressedKeys, mec);
			QuickEventQueue.get().scheduleEvent(new QuickEventQueue.PositionQueueEvent(enter.getElement(), enter, false), true);
		}
	}

	private void focusByMouse(QuickEventPositionCapture capture, UserEvent cause, java.util.List<QuickEventQueue.Event> events) {
		for(QuickEventPositionCapture mec : capture.iterate(true))
			if(mec.getElement().isFocusable()) {
				setFocus(mec.getElement(), cause);
				return;
			}
	}

	/**
	 * Sets the document's focused element. This method does not invoke {@link QuickElement#isFocusable()}, so this will work on any element.
	 *
	 * @param toFocus The element to give the focus to
	 * @param cause The user event triggering the focus change, if any
	 */
	public void setFocus(QuickElement toFocus, UserEvent cause) {
		ArrayList<QuickEventQueue.Event> events = new ArrayList<>();
		setFocus(toFocus, cause, events);
		for(QuickEventQueue.Event event : events)
			QuickEventQueue.get().scheduleEvent(event, true);
	}

	private void setFocus(QuickElement focus, UserEvent cause, java.util.List<QuickEventQueue.Event> events) {
		QuickElement oldFocus = theFocus;
		theFocus = focus;
		if(oldFocus != theFocus) {
			if(oldFocus != null)
				events.add(new QuickEventQueue.UserQueueEvent(
					new FocusEvent(this, oldFocus, thePressedButtons, thePressedKeys, false, cause), false));
			if(theFocus != null)
				events.add(new QuickEventQueue.UserQueueEvent(
					new FocusEvent(this, theFocus, thePressedButtons, thePressedKeys, true, cause), false));
			theFocusController.onNext(theObservableFocus.createChangeEvent(oldFocus, theFocus, null));
		}
	}

	/**
	 * Moves this document's focus to the focusable widget previous to the currently focused widget
	 *
	 * @param cause The user event triggering the focus change, if any
	 */
	public void backupFocus(UserEvent cause) {
		if(theFocus == null)
			return;
		if(searchFocus(theFocus, cause, false))
			return;
		/* If we get here, then there was no previous focusable element. We must wrap around to the last focusable element. */
		QuickElement deepest = getDeepestElement(theRoot, false);
		searchFocus(deepest, cause, false);
	}

	/**
	 * Moves this document's focus to the focusable widget after the currently focused widget
	 *
	 * @param cause The user event triggering the focus change, if any
	 */
	public void advanceFocus(UserEvent cause) {
		if(theFocus == null)
			return;
		if(searchFocus(theFocus, cause, true))
			return;
		/* If we get here, then there was no next focusable element. We must wrap around to the first focusable element. */
		QuickElement deepest = getDeepestElement(theRoot, true);
		searchFocus(deepest, cause, true);
	}

	boolean searchFocus(QuickElement el, UserEvent cause, boolean forward) {
		QuickElement lastChild = theFocus;
		QuickElement parent = theFocus.getParent().get();
		while(parent != null) {
			QuickElement[] children = parent.getPhysicalChildren().sortByZ().toArray();
			if(!forward) {
				ArrayUtils.reverse(children);
			}
			boolean foundLastChild = false;
			for(int c = 0; c < children.length; c++)
				if(foundLastChild) {
					QuickElement deepest = getDeepestElement(children[c], forward);
					if(deepest != children[c]) {
						// Do searchFocus from this deep element
						parent = deepest;
						break;
					} else if(children[c].isFocusable()) {
						setFocus(children[c], cause);
						return true;
					}
				} else if(children[c] == lastChild)
					foundLastChild = true;
			if(parent.isFocusable()) {
				setFocus(parent, cause);
				return true;
			}
			lastChild = parent;
			parent = parent.getParent().get();
		}
		return false;
	}

	static QuickElement getDeepestElement(QuickElement root, boolean first) {
		while(!root.ch().isEmpty())
			if(first)
				root = root.ch().get(0);
			else
				root = root.ch().last();
		return root;
	}

	/**
	 * Emulates a scroll event on the document
	 *
	 * @param x The x-coordinate where the event occurred
	 * @param y The y-coordinate where the event occurred
	 * @param amount The amount that the mouse wheel was scrolled
	 */
	public void scroll(int x, int y, int amount) {
		ScrollEvent evt = null;
		QuickElement element = null;
		QuickRendering rendering = theRendering;
		if(rendering == null)
			return;
		switch (theScrollPolicy) {
		case MOUSE:
		case MIXED:
			QuickEventPositionCapture capture = rendering.capture(x, y);
			evt = new ScrollEvent(this, element, ScrollEvent.ScrollType.UNIT, true, amount, null, thePressedButtons, thePressedKeys,
				capture, null);
			break;
		case FOCUS:
			element = theFocus;
			if(element == null)
				element = theRoot;
			evt = new ScrollEvent(this, element, ScrollEvent.ScrollType.UNIT, true, amount, null, thePressedButtons, thePressedKeys, null,
				null);
			break;
		}
		QuickEventQueue.get().scheduleEvent(new QuickEventQueue.PositionQueueEvent(theRoot, evt, false), true);
	}

	/**
	 * Emulates a key event on this document
	 *
	 * @param code The key code of the event
	 * @param pressed Whether the key was pressed or released
	 */
	public void keyed(KeyBoardEvent.KeyCode code, boolean pressed) {
		final QuickRendering rendering = theRendering;

		synchronized (theKeysLock) {
			if (pressed) {
				if (!thePressedKeys.add(code))
					return;
			} else {
				if (!thePressedKeys.remove(code))
					return;
			}
		}
		final KeyBoardEvent evt;
		if(theFocus != null)
			evt = new KeyBoardEvent(this, theFocus, code, thePressedButtons, thePressedKeys, pressed);
		else
			evt = new KeyBoardEvent(this, theRoot, code, thePressedButtons, thePressedKeys, pressed);
		if(theFocus != null)
			QuickEventQueue.get().scheduleEvent(new QuickEventQueue.UserQueueEvent(evt, false, () -> {
				if(!evt.isUsed())
					scroll(evt, rendering);
			}), true);
		else
			scroll(evt, rendering);
	}

	private void scroll(KeyBoardEvent evt, QuickRendering rendering) {
		QuickEventPositionCapture capture = null;
		if(!evt.isUsed()) {
			QuickElement scrollElement = null;
			switch (theScrollPolicy) {
			case MOUSE:
				if(hasMouse && rendering != null) {
					capture = rendering.capture(theMouseX, theMouseY);
					scrollElement = capture.getTarget().getElement();
				} else
					scrollElement = null;
				break;
			case FOCUS:
			case MIXED:
				if(theFocus != null)
					scrollElement = theFocus;
				else
					scrollElement = theRoot;
				break;
			}
			if(scrollElement != null) {
				ScrollEvent.ScrollType scrollType = null;
				boolean vertical = true, downOrRight = true;
				switch (evt.getKeyCode()) {
				case LEFT_ARROW:
					scrollType = ScrollEvent.ScrollType.UNIT;
					vertical = false;
					downOrRight = false;
					break;
				case RIGHT_ARROW:
					scrollType = ScrollEvent.ScrollType.UNIT;
					vertical = false;
					downOrRight = true;
					break;
				case UP_ARROW:
					scrollType = ScrollEvent.ScrollType.UNIT;
					vertical = true;
					downOrRight = false;
					break;
				case DOWN_ARROW:
					scrollType = ScrollEvent.ScrollType.UNIT;
					vertical = true;
					downOrRight = true;
					break;
				case PAGE_UP:
					scrollType = ScrollEvent.ScrollType.BLOCK;
					vertical = true;
					downOrRight = false;
					break;
				case PAGE_DOWN:
					scrollType = ScrollEvent.ScrollType.BLOCK;
					vertical = true;
					downOrRight = true;
					break;
				default:
					scrollType = null;
				}
				if(scrollType != null) {
					ScrollEvent scrollEvt = new ScrollEvent(this, scrollElement, scrollType, vertical, downOrRight ? 1 : -1, evt,
						thePressedButtons, thePressedKeys, capture, evt);
					if(capture != null)
						QuickEventQueue.get().scheduleEvent(new QuickEventQueue.PositionQueueEvent(scrollElement, scrollEvt, false), true);
					else
						QuickEventQueue.get().scheduleEvent(new QuickEventQueue.UserQueueEvent(scrollEvt, false), true);
				}
			}

			if(evt.getKeyCode() == KeyBoardEvent.KeyCode.TAB)
				if(isShiftPressed())
					backupFocus(evt);
				else
					advanceFocus(evt);
		}
	}

	/**
	 * Emulates textual input to the document
	 *
	 * @param c The character that was input
	 */
	public void character(char c) {
		org.quick.core.event.CharInputEvent evt = null;
		if(theFocus != null)
			evt = new org.quick.core.event.CharInputEvent(this, theFocus, thePressedButtons, thePressedKeys, c);
		else
			evt = new org.quick.core.event.CharInputEvent(this, theRoot, thePressedButtons, thePressedKeys, c);
		QuickEventQueue.get().scheduleEvent(new QuickEventQueue.UserQueueEvent(evt, false), true);
	}
}
