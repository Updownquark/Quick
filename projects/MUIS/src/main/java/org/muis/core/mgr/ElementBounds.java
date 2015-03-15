package org.muis.core.mgr;

import java.awt.Rectangle;

import org.muis.core.MuisElement;
import org.muis.core.event.BoundsChangedEvent;
import org.muis.core.layout.Orientation;
import org.muis.core.layout.SizeGuide;
import org.muis.rx.ObservableValueEvent;
import org.muis.rx.Observer;

import prisms.lang.Type;

/** Bounds for an element. Contains some extra methods for easy access. */
public class ElementBounds extends org.muis.rx.DefaultObservableValue<Rectangle> implements org.muis.core.layout.Bounds {
	private final MuisElement theElement;

	private ElementBoundsDimension theHorizontalBounds;
	private ElementBoundsDimension theVerticalBounds;

	private int theX;
	private int theY;
	private int theW;
	private int theH;

	private final Observer<ObservableValueEvent<Rectangle>> theController;
	private volatile int theStackChecker;

	/** @param element The element to create the bounds for */
	public ElementBounds(MuisElement element) {
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

	/** @return {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#getPosition() getPosition()} */
	public int getX() {
		return theX;
	}

	/** @param x See {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#setPosition(int) setPosition(int)} */
	public void setX(int x) {
		if(theX == x)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theX = x;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
		theHorizontalBounds.setPosition(x);
	}

	/** @return {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#getPosition() getPosition()} */
	public int getY() {
		return theY;
	}

	/** @param y See {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#setPosition(int) setPosition(int)} */
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
	 * @param x See {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 * @param y See {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 */
	public void setPosition(int x, int y) {
		if(theX == x && theY == y)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theX = x;
		theY = y;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#getSize() getSize()} */
	public int getWidth() {
		return theW;
	}

	/** @param width See {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#setSize(int) setSize(int)} */
	public void setWidth(int width) {
		if(theW == width)
			return;
		Rectangle preBounds = new Rectangle(theX, theY, theW, theH);
		theW = width;
		fire(preBounds, new Rectangle(theX, theY, theW, theH));
	}

	/** @return {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#getSize() getSize()} */
	public int getHeight() {
		return theH;
	}

	/** @param height See {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#setSize(int) setSize(int)} */
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
	 * @param width See {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#setSize(int) setSize(int)}
	 * @param height See {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#setSize(int) setSize(int)}
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

	@Override
	public Type getType() {
		return new Type(Rectangle.class);
	}

	/** @return An observable value for this bounds' x-coordinate. Equivalent to <code>mapV(bounds->{return bounds.x;})</code>. */
	public org.muis.rx.ObservableValue<Integer> observeX() {
		return mapV(bounds -> {
			return bounds.x;
		});
	}

	/** @return An observable value for this bounds' y-coordinate. Equivalent to <code>mapV(bounds->{return bounds.y;})</code>. */
	public org.muis.rx.ObservableValue<Integer> observeY() {
		return mapV(bounds -> {
			return bounds.y;
		});
	}

	/** @return An observable value for this bounds' width. Equivalent to <code>mapV(bounds->{return bounds.width;})</code>. */
	public org.muis.rx.ObservableValue<Integer> observeW() {
		return mapV(bounds -> {
			return bounds.width;
		});
	}

	/** @return An observable value for this bounds' height. Equivalent to <code>mapV(bounds->{return bounds.height;})</code>. */
	public org.muis.rx.ObservableValue<Integer> observeH() {
		return mapV(bounds -> {
			return bounds.height;
		});
	}

	/**
	 *
	 * @param x See {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 * @param y See {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#setPosition(int) setPosition(int)}
	 * @param width See {@link #getHorizontal()}.{@link org.muis.core.layout.BoundsDimension#setSize(int) setSize(int)}
	 * @param height See {@link #getVertical()}.{@link org.muis.core.layout.BoundsDimension#setSize(int) setSize(int)}
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
		return new StringBuilder(theElement.getTagName()).append(" bounds=(").append(theX).append(',').append(theY).append("){")
			.append(theW).append(',').append(theH).append('}')
			.toString();
	}

	private final void fire(Rectangle preBounds, Rectangle newBounds) {
		theStackChecker++;
		final int stackCheck = theStackChecker;
		BoundsChangedEvent evt = new BoundsChangedEvent(theElement, this, preBounds, newBounds, null) {
			@Override
			public boolean isOverridden() {
				return stackCheck != theStackChecker;
			}
		};
		theController.onNext(evt);
		theElement.events().fire(evt);
	}

	/** A BoundsDimension for an element along one axis */
	public class ElementBoundsDimension implements org.muis.core.layout.BoundsDimension {
		private boolean isVertical;

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
