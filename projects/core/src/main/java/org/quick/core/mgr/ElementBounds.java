package org.quick.core.mgr;

import java.awt.Rectangle;

import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.quick.core.QuickElement;
import org.quick.core.event.BoundsChangedEvent;
import org.quick.core.layout.Orientation;
import org.quick.core.layout.SizeGuide;

import com.google.common.reflect.TypeToken;

/** Bounds for an element. Contains some extra methods for easy access. */
public class ElementBounds extends org.observe.DefaultObservableValue<Rectangle> implements org.quick.core.layout.Bounds {
	private final QuickElement theElement;

	private ElementBoundsDimension theHorizontalBounds;
	private ElementBoundsDimension theVerticalBounds;

	private int theX;
	private int theY;
	private int theW;
	private int theH;

	private final Observer<ObservableValueEvent<Rectangle>> theController;
	private volatile int theStackChecker;

	/** @param element The element to create the bounds for */
	public ElementBounds(QuickElement element) {
		theController = control(null);
		theElement = element;
		theHorizontalBounds = new ElementBoundsDimension(false);
		theVerticalBounds = new ElementBoundsDimension(true);
	}

	/** @return The horizontal bounds */
	public ElementBoundsDimension getHorizontal() {
		return theHorizontalBounds;
	}

	/** @return The horizontal bounds */
	public ElementBoundsDimension h() {
		return theHorizontalBounds;
	}

	/** @return The vertical bounds */
	public ElementBoundsDimension getVertical() {
		return theVerticalBounds;
	}

	/** @return The vertical bounds */
	public ElementBoundsDimension v() {
		return theVerticalBounds;
	}

	@Override
	public ElementBoundsDimension get(Orientation orientation) {
		switch (orientation) {
		case horizontal:
			return theHorizontalBounds;
		case vertical:
			return theVerticalBounds;
		}
		throw new IllegalStateException("Unrecognized orientation: " + orientation);
	}

	/** @return {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#getPosition() getPosition()} */
	public int getX() {
		return theX;
	}

	/** @param x See {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#setPosition(int) setPosition(int)} */
	public void setX(int x) {
		if(theX == x)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theX = x;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
		theHorizontalBounds.setPosition(x);
	}

	/** @return {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#getPosition() getPosition()} */
	public int getY() {
		return theY;
	}

	/** @param y See {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#setPosition(int) setPosition(int)} */
	public void setY(int y) {
		if(theY == y)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theY = y;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return The element's 2-dimensional position */
	public java.awt.Point getPosition() {
		return new java.awt.Point(theX, theY);
	}

	/**
	 * @param x See {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 * @param y See {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 */
	public void setPosition(int x, int y) {
		if(theX == x && theY == y)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theX = x;
		theY = y;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#getSize() getSize()} */
	public int getWidth() {
		return theW;
	}

	/** @param width See {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#setSize(int) setSize(int)} */
	public void setWidth(int width) {
		if(theW == width)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theW = width;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#getSize() getSize()} */
	public int getHeight() {
		return theH;
	}

	/** @param height See {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#setSize(int) setSize(int)} */
	public void setHeight(int height) {
		if(theH == height)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theH = height;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return The element's 2-dimensional size */
	public java.awt.Dimension getSize() {
		return new java.awt.Dimension(theW, theH);
	}

	/**
	 * @param width See {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#setSize(int) setSize(int)}
	 * @param height See {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#setSize(int) setSize(int)}
	 */
	public void setSize(int width, int height) {
		if(theW == width && theH == height)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theW = width;
		theH = height;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return The element's rectangle bounds */
	public Rectangle getBounds() {
		return new Rectangle(theX, theY, theW, theH);
	}

	@Override
	public Rectangle get() {
		return getBounds();
	}

	/**
	 * @param x The x position relative to this bounds' coordinate system
	 * @param y The y position relative to this bounds' coordinate system
	 * @return Whether this bounds overlaps the given coordinate
	 */
	public boolean contains(int x, int y) {
		int xDiff = x - theX;
		if (xDiff < 0 || xDiff >= theW)
			return false;
		int yDiff = y - theY;
		if (yDiff < 0 || yDiff >= theH)
			return false;
		return true;
	}

	/** @return Whether this bounds has zero area */
	public boolean isEmpty(){
		return theW == 0 && theH == 0;
	}

	@Override
	public TypeToken<Rectangle> getType() {
		return TypeToken.of(Rectangle.class);
	}

	/** @return An observable value for this bounds' x-coordinate. Equivalent to <code>mapV(bounds->{return bounds.x;})</code>. */
	public org.observe.ObservableValue<Integer> observeX() {
		return mapV(bounds -> {
			return bounds.x;
		});
	}

	/** @return An observable value for this bounds' y-coordinate. Equivalent to <code>mapV(bounds->{return bounds.y;})</code>. */
	public org.observe.ObservableValue<Integer> observeY() {
		return mapV(bounds -> {
			return bounds.y;
		});
	}

	/** @return An observable value for this bounds' width. Equivalent to <code>mapV(bounds->{return bounds.width;})</code>. */
	public org.observe.ObservableValue<Integer> observeW() {
		return mapV(bounds -> {
			return bounds.width;
		});
	}

	/** @return An observable value for this bounds' height. Equivalent to <code>mapV(bounds->{return bounds.height;})</code>. */
	public org.observe.ObservableValue<Integer> observeH() {
		return mapV(bounds -> {
			return bounds.height;
		});
	}

	/**
	 *
	 * @param x See {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 * @param y See {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 * @param width See {@link #getHorizontal()}.{@link org.quick.core.layout.BoundsDimension#setSize(int) setSize(int)}
	 * @param height See {@link #getVertical()}.{@link org.quick.core.layout.BoundsDimension#setSize(int) setSize(int)}
	 */
	public void setBounds(int x, int y, int width, int height) {
		if(theX == x && theY == y && theW == width && theH == height)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theX = x;
		theY = y;
		theW = width;
		theH = height;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (theElement.getTagName() != null)
			builder.append(theElement.getTagName()).append(' ');
		return builder.append("bounds=(").append(theX).append(',').append(theY).append("){").append(theW).append(',').append(theH)
			.append('}').toString();
	}

	private final void fire(Rectangle preBounds, Rectangle newBounds) {
		theStackChecker++;
		final int stackCheck = theStackChecker;
		BoundsChangedEvent evt = new BoundsChangedEvent(theElement, this, false, preBounds, newBounds, null) {
			@Override
			public boolean isOverridden() {
				return stackCheck != theStackChecker;
			}
		};
		theController.onNext(evt);
		theElement.events().fire(evt);
	}

	/** A BoundsDimension for an element along one axis */
	public class ElementBoundsDimension implements org.quick.core.layout.BoundsDimension {
		private final boolean isVertical;

		ElementBoundsDimension(boolean vertical) {
			isVertical = vertical;
		}

		@Override
		public int getPosition() {
			return isVertical ? theY : theX;
		}

		@Override
		public void setPosition(int pos) {
			if(isVertical)
				setY(pos);
			else
				setX(pos);
		}

		@Override
		public int getSize() {
			return isVertical ? theH : theW;
		}

		@Override
		public void setSize(int size) {
			if(isVertical)
				setHeight(size);
			else
				setWidth(size);
		}

		@Override
		public SizeGuide getGuide() {
			return isVertical ? theElement.getHSizer() : theElement.getWSizer();
		}

		@Override
		public String toString() {
			return new StringBuilder(theElement.getTagName()).append(" ").append(isVertical ? "v" : "h").append("-bounds=(")
				.append(getPosition()).append("){").append(getSize()).append("}").toString();
		}
	}
}
