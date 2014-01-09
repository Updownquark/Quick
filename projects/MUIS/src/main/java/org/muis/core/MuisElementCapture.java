package org.muis.core;

import java.util.Iterator;

import org.muis.util.MuisUtils;

/**
 * Represents a capture of an element's bounds and hierarchy at a point in time
 *
 * @param <C> The sub-type of the capture. Used to make subclasses go a little nicer.
 */
public class MuisElementCapture<C extends MuisElementCapture<C>> implements Cloneable, prisms.util.Sealable, Iterable<C> {
	private C theParent;

	private final MuisElement theElement;

	/** The x-coordinate of the screen point relative to this element */
	private final int theX;

	/** The y-coordinate of the screen point relative to this element */
	private final int theY;

	private final int theZ;

	private final int theWidth;

	private final int theHeight;

	private java.util.List<C> theChildren;

	private boolean isSealed;

	/**
	 * @param p This capture element's parent in the hierarchy
	 * @param el The MUIS element that this structure is a capture of
	 * @param xPos The x-coordinate of the element's upper-left corner
	 * @param yPos The y-coordinate of the element's upper-left corner
	 * @param zIndex The z-index of the element
	 * @param w The width of the element
	 * @param h The height of the element
	 */
	public MuisElementCapture(C p, MuisElement el, int xPos, int yPos, int zIndex, int w, int h) {
		theParent = p;
		theElement = el;
		theX = xPos;
		theY = yPos;
		theZ = zIndex;
		theWidth = w;
		theHeight = h;
		theChildren = new java.util.ArrayList<>(3);
	}

	/**
	 * @param child The child to add to this capture
	 * @throws SealedException If this capture has been sealed
	 */
	public void addChild(C child) throws SealedException {
		if(isSealed)
			throw new SealedException(this);
		theChildren.add(child);
	}

	/** @return The number of children this capture has--that is, the number of children this capture's element had at the moment of capture */
	public int getChildCount() {
		return theChildren.size();
	}

	/**
	 * @param index The index of the child to get
	 * @return This capture's child at the given index
	 */
	public C getChild(int index) {
		return theChildren.get(index);
	}

	/** @return An iterator over this capture's immediate children */
	public java.util.List<C> getChildren() {
		return theChildren;
	}

	/** @return An iterator of each end point (leaf node) in this hierarchy */
	public Iterable<C> getTargets() {
		if(theChildren.isEmpty())
			return new Iterable<C>() {
				@Override
				public Iterator<C> iterator() {
					return new SelfIterator();
				}
			};
		else
			return new Iterable<C>() {
				@Override
				public Iterator<C> iterator() {
					return new MuisCaptureIterator(false, true);
				}
			};
	}

	/** Performs a depth-first iteration of this capture structure */
	@Override
	public Iterator<C> iterator() {
		return new MuisCaptureIterator(true, true);
	}

	/**
	 * @param depthFirst Whether to iterate depth-first or breadth-first
	 * @return An iterable to iterate over every element in this hierarchy
	 */
	public Iterable<C> iterate(final boolean depthFirst) {
		return new Iterable<C>() {
			@Override
			public Iterator<C> iterator() {
				return new MuisCaptureIterator(true, depthFirst);
			}
		};
	}

	/**
	 * @param el The element to search for
	 * @return The capture of the given element in this hierarchy, or null if the given element was not located in this capture
	 */
	public C find(MuisElement el) {
		if(theParent != null)
			return getRoot().find(el);
		MuisElement [] path = MuisUtils.path(el);
		C ret = (C) this;
		int pathIdx;
		for(pathIdx = 1; pathIdx < path.length; pathIdx++) {
			boolean found = false;
			for(MuisElementCapture<?> child : ((MuisElementCapture<?>) ret).theChildren)
				if(child.getElement() == path[pathIdx]) {
					found = true;
					ret = (C) child;
					break;
				}
			if(!found)
				return null;
		}
		return ret;
	}

	/** @return The last end point (leaf node) in this hierarchy */
	public C getTarget() {
		if(theChildren.isEmpty())
			return (C) this;
		return theChildren.get(theChildren.size() - 1).getTarget();
	}

	@Override
	public boolean isSealed() {
		return isSealed;
	}

	@Override
	public void seal() {
		theChildren = java.util.Collections.unmodifiableList(theChildren);
		isSealed = true;
	}

	@Override
	public int hashCode() {
		return getElement().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof MuisElementCapture && ((MuisElementCapture<?>) obj).getElement().equals(getElement());
	}

	/** @return The root of this capture */
	public C getRoot() {
		C parent = theParent;
		while(parent.getParent() != null)
			parent = parent.getParent();
		return parent;
	}

	/** @return This capture element's parent in the hierarchy */
	public C getParent() {
		return theParent;
	}

	/** @param parent The parent element's capture */
	public void setParent(C parent) {
		if(isSealed)
			throw new SealedException(this);
		theParent = parent;
	}

	/** @return The element that this capture represents */
	public MuisElement getElement() {
		return theElement;
	}

	/** @return The x-coordinate of the element */
	public int getX() {
		return theX;
	}

	/** @return The y-coordinate of the element */
	public int getY() {
		return theY;
	}

	/** @return The z-index of the element */
	public int getZ() {
		return theZ;
	}

	/** @return The width of the element */
	public int getWidth() {
		return theWidth;
	}

	/** @return The height of the element */
	public int getHeight() {
		return theHeight;
	}

	/** @return The location of the top left corner of this element relative to the document's top left corner */
	public java.awt.Point getDocLocation() {
		java.awt.Point ret = new java.awt.Point(theX, theY);
		MuisElementCapture<C> parent = theParent;
		while(parent != null) {
			ret.x += parent.theX;
			ret.y += parent.theY;
			parent = parent.theParent;
		}
		return ret;
	}

	@Override
	protected MuisElementCapture<C> clone() {
		MuisElementCapture<C> ret;
		try {
			ret = (MuisElementCapture<C>) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
		ret.theChildren = new java.util.ArrayList<>();
		for(C child : theChildren) {
			C newChild = (C) child.clone();
			((MuisElementCapture<C>) newChild).theParent = (C) ret;
			ret.theChildren.add(newChild);
		}
		ret.isSealed = false;
		return ret;
	}

	private class SelfIterator implements Iterator<C> {
		private boolean hasReturned;

		SelfIterator() {
		}

		@Override
		public boolean hasNext() {
			return !hasReturned;
		}

		@Override
		public C next() {
			hasReturned = true;
			return (C) MuisElementCapture.this;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private class MuisCaptureIterator implements Iterator<C> {
		private int theIndex;

		private Iterator<C> theChildIter;

		private boolean isReturningSelf;

		private boolean isDepthFirst;

		private boolean hasReturnedSelf;

		MuisCaptureIterator(boolean returnSelf, boolean depthFirst) {
			isReturningSelf = returnSelf;
			isDepthFirst = depthFirst;
		}

		@Override
		public boolean hasNext() {
			if(isReturningSelf && !isDepthFirst && !hasReturnedSelf)
				return true;
			while(theIndex < getChildCount()) {
				if(theChildIter == null)
					theChildIter = getChild(theIndex).iterator();
				if(theChildIter.hasNext())
					return true;
				else
					theChildIter = null;
				theIndex++;
			}
			return isReturningSelf && !hasReturnedSelf;
		}

		@Override
		public C next() {
			if(isReturningSelf && !isDepthFirst && !hasReturnedSelf) {
				hasReturnedSelf = true;
				return (C) MuisElementCapture.this;
			}
			if(theChildIter != null)
				return theChildIter.next();
			if(isReturningSelf && isDepthFirst && !hasReturnedSelf) {
				hasReturnedSelf = true;
				return (C) MuisElementCapture.this;
			}
			return null;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
