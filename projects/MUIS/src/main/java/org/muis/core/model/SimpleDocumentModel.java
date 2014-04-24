package org.muis.core.model;

import static org.muis.core.MuisConstants.States.TEXT_SELECTION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;

import org.muis.core.style.MuisStyle;
import org.muis.core.style.StyleAttributeEvent;
import org.muis.core.style.stateful.InternallyStatefulStyle;
import org.muis.core.style.stateful.StateChangedEvent;
import org.muis.core.style.stateful.StatefulStyle;

/** A very simple document model that uses a single style and keeps a single, mutable set of content and supports single interval selection */
public class SimpleDocumentModel extends AbstractMuisDocumentModel implements MutableSelectableDocumentModel, Appendable {
	private final InternallyStatefulStyle theParentStyle;

	private final MuisStyle theNormalStyle;

	private final MuisStyle theSelectedStyle;

	private final StringBuilder theContent;

	private int theCursor;

	private int theSelectionAnchor;

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	private Collection<MuisDocumentModel.ContentListener> theContentListeners;
	private Collection<MuisDocumentModel.StyleListener> theStyleListeners;

	private Collection<SelectableDocumentModel.SelectionListener> theSelectionListeners;

	/**
	 * @param parentStyle The parent style for this document
	 * @param text The initial text for this field
	 */
	public SimpleDocumentModel(InternallyStatefulStyle parentStyle, String text) {
		this(parentStyle);
		theContent.append(text);
	}

	/** @param parentStyle The parent style for this document */
	public SimpleDocumentModel(InternallyStatefulStyle parentStyle) {
		theParentStyle = parentStyle;
		theNormalStyle = new SelectionStyle(parentStyle, false);
		theSelectedStyle = new SelectionStyle(parentStyle, true);
		theContent = new StringBuilder();
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theContentListeners = new java.util.concurrent.ConcurrentLinkedQueue<>();
		theStyleListeners = new java.util.concurrent.ConcurrentLinkedQueue<>();
		theSelectionListeners = new java.util.concurrent.ConcurrentLinkedQueue<>();
		/* Clear the metrics/rendering cache when the style changes.  Otherwise, style changes won't cause the document to re-render
		 * correctly because the cache may have the old color/size/etc */
		theNormalStyle.addListener(new org.muis.core.style.StyleListener() {
			@Override
			public void eventOccurred(StyleAttributeEvent<?> event) {
				int minSel = theCursor;
				int maxSel = theCursor;
				if(theSelectionAnchor < minSel)
					minSel = theSelectionAnchor;
				if(theSelectionAnchor > maxSel)
					maxSel = theSelectionAnchor;
				if(minSel == 0 && maxSel == length()) {
					return; // No unselected text
				}
				clearCache();
			}
		});
		theSelectedStyle.addListener(new org.muis.core.style.StyleListener() {
			@Override
			public void eventOccurred(StyleAttributeEvent<?> event) {
				if(theCursor == theSelectionAnchor) {
					return; // No selected text
				}
				clearCache();
			}
		});
	}

	/** @return This document's parent's style */
	public StatefulStyle getParentStyle() {
		return theParentStyle;
	}

	/** @return The style for text that is not selected */
	public MuisStyle getNormalStyle() {
		return theNormalStyle;
	}

	/** @return The style for text that is selected */
	public MuisStyle getSelectedStyle() {
		return theSelectedStyle;
	}

	@Override
	public void addContentListener(MuisDocumentModel.ContentListener listener) {
		if(listener != null)
			theContentListeners.add(listener);
	}

	@Override
	public void removeContentListener(MuisDocumentModel.ContentListener listener) {
		theContentListeners.remove(listener);
	}

	@Override
	public void addStyleListener(StyleListener listener) {
		if(listener != null)
			theStyleListeners.add(listener);
	}

	@Override
	public void removeStyleListener(StyleListener listener) {
		theStyleListeners.remove(listener);
	}

	@Override
	public void addSelectionListener(SelectableDocumentModel.SelectionListener listener) {
		if(listener != null)
			theSelectionListeners.add(listener);
	}

	@Override
	public void removeSelectionListener(SelectableDocumentModel.SelectionListener listener) {
		theSelectionListeners.remove(listener);
	}

	@Override
	public int getCursor() {
		return theCursor;
	}

	@Override
	public int getSelectionAnchor() {
		return theSelectionAnchor;
	}

	@Override
	public void setCursor(int cursor) {
		ArrayList<StyledSequence> before = new ArrayList<>();
		for(StyledSequence seq : this)
			before.add(seq);
		int oldAnchor = theSelectionAnchor;
		int oldCursor = theCursor;
		theCursor = cursor;
		theSelectionAnchor = cursor;
		ArrayList<StyledSequence> after = new ArrayList<>();
		for(StyledSequence seq : this)
			after.add(seq);
		fireSelectionEvent(oldAnchor, oldCursor, cursor, cursor, before, after);
	}

	@Override
	public void setSelection(int anchor, int cursor) {
		if(theSelectionAnchor == anchor && theCursor == cursor)
			return;
		ArrayList<StyledSequence> before = new ArrayList<>();
		for(StyledSequence seq : this)
			before.add(seq);
		int oldAnchor = theSelectionAnchor;
		int oldCursor = theCursor;
		theSelectionAnchor = anchor;
		theCursor = cursor;
		ArrayList<StyledSequence> after = new ArrayList<>();
		for(StyledSequence seq : this)
			after.add(seq);
		fireSelectionEvent(oldAnchor, oldCursor, anchor, cursor, before, after);
	}

	@Override
	public Iterator<StyledSequence> iterator() {
		final String content = toString();
		int anchor = theSelectionAnchor;
		int cursor = theCursor;
		ArrayList<StyledSequence> ret = new ArrayList<>();
		int div1 = anchor;
		int div2 = cursor;
		if(div1 > div2) {
			int temp = div1;
			div1 = div2;
			div2 = temp;
		}
		if(div1 == div2)
			ret.add(new SimpleStyledSequence(content, theNormalStyle));
		else {
			if(div1 > 0)
				ret.add(new SimpleStyledSequence(content.substring(0, div1), theNormalStyle));
			ret.add(new SimpleStyledSequence(content.substring(div1, div2), theSelectedStyle));
			if(div2 < content.length())
				ret.add(new SimpleStyledSequence(content.substring(div2), theNormalStyle));
		}
		return java.util.Collections.unmodifiableList(ret).iterator();
	}

	@Override
	public int length() {
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			return theContent.length();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public char charAt(int index) {
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			return theContent.charAt(index);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			return theContent.subSequence(start, end);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			return theContent.toString();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String getSelectedText() {
		int start = theSelectionAnchor;
		int end = theCursor;
		if(start == end)
			return "";
		if(start > end) {
			int temp = start;
			start = end;
			end = temp;
		}
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			if(end < 0 || start >= theContent.length())
				return "";
			if(start < 0)
				start = 0;
			if(end > theContent.length())
				end = theContent.length();
			return theContent.substring(start, end);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public SimpleDocumentModel append(CharSequence csq) {
		String value;
		int index;
		String change = csq.toString();
		boolean selChange = false;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor == theContent.length()) {
				selChange = true;
				theSelectionAnchor += change.length();
			}
			if(theCursor == theContent.length()) {
				selChange = true;
				theCursor += change.length();
			}
			index = theContent.length();
			theContent.append(change);
			value = theContent.toString();
		} finally {
			lock.unlock();
		}
		if(selChange)
			fireContentEvent(value, change, index, false, theSelectionAnchor, theCursor);
		else
			fireContentEvent(value, change, index, false, -1, -1);
		return this;
	}

	@Override
	public SimpleDocumentModel append(CharSequence csq, int start, int end) {
		append(csq.subSequence(start, end));
		return this;
	}

	@Override
	public SimpleDocumentModel append(char c) {
		String value;
		int index;
		String change = new String(new char[] {c});
		boolean selChange = false;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor == theContent.length()) {
				selChange = true;
				theSelectionAnchor++;
			}
			if(theCursor == theContent.length()) {
				selChange = true;
				theCursor++;
			}
			index = theContent.length();
			theContent.append(c);
			value = theContent.toString();
		} finally {
			lock.unlock();
		}
		if(selChange)
			fireContentEvent(value, change, index, false, theSelectionAnchor, theCursor);
		else
			fireContentEvent(value, change, index, false, -1, -1);
		return this;
	}

	@Override
	public SimpleDocumentModel insert(CharSequence csq) {
		String value;
		int index = theCursor;
		String change = csq.toString();
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor >= theCursor)
				theSelectionAnchor += change.length();
			theContent.insert(index, change);
			value = theContent.toString();
			theCursor += change.length();
		} finally {
			lock.unlock();
		}
		fireContentEvent(value, change, index, false, theSelectionAnchor, theCursor);
		return this;
	}

	@Override
	public SimpleDocumentModel insert(char c) {
		String value;
		int index = theCursor;
		String change = new String(new char[] {c});
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor >= theCursor)
				theSelectionAnchor++;
			theContent.insert(index, c);
			value = theContent.toString();
			theCursor++;
		} finally {
			lock.unlock();
		}
		fireContentEvent(value, change, index, false, theSelectionAnchor, theCursor);
		return this;
	}

	@Override
	public SimpleDocumentModel insert(int offset, CharSequence csq) {
		String value;
		String change = csq.toString();
		boolean selChange = false;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor >= offset) {
				selChange = true;
				theSelectionAnchor += csq.length();
			}
			if(theCursor >= offset) {
				selChange = true;
				theCursor += csq.length();
			}
			theContent.insert(offset, csq);
			value = theContent.toString();
		} finally {
			lock.unlock();
		}
		if(selChange)
			fireContentEvent(value, change, offset, false, theSelectionAnchor, theCursor);
		else
			fireContentEvent(value, change, offset, false, -1, -1);
		return this;
	}

	@Override
	public SimpleDocumentModel insert(int offset, char c) {
		String value;
		String change = new String(new char[] {c});
		boolean selChange = false;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor >= offset) {
				selChange = true;
				theSelectionAnchor++;
			}
			if(theCursor >= offset) {
				selChange = true;
				theCursor++;
			}
			theContent.insert(offset, c);
			value = theContent.toString();
		} finally {
			lock.unlock();
		}
		if(selChange)
			fireContentEvent(value, change, c, false, theSelectionAnchor, theCursor);
		else
			fireContentEvent(value, change, c, false, -1, -1);
		return this;
	}

	@Override
	public SimpleDocumentModel delete(int start, int end) {
		if(start > end) {
			int temp = start;
			start = end;
			end = temp;
		}
		String value;
		String change;
		boolean selChange = false;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			if(theSelectionAnchor >= start) {
				if(theSelectionAnchor >= end)
					theSelectionAnchor -= end - start;
				else
					theSelectionAnchor = start;
				selChange = true;
			}
			if(theCursor >= start) {
				if(theCursor >= end)
					theCursor -= end - start;
				else
					theCursor = start;
				selChange = true;
			}
			change = theContent.substring(start, end);
			theContent.delete(start, end);
			value = theContent.toString();
		} finally {
			lock.unlock();
		}
		if(selChange)
			fireContentEvent(value, change, start, true, theSelectionAnchor, theCursor);
		else
			fireContentEvent(value, change, start, true, -1, -1);
		return this;
	}

	@Override
	public SimpleDocumentModel setText(String text) {
		String oldValue;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			theSelectionAnchor = text.length();
			theCursor = text.length();
			int oldLen = theContent.length();
			oldValue = theContent.toString();
			theContent.setLength(0);
			theContent.append(text);
			if(oldLen - text.length() > 100 && oldLen - text.length() > text.length() / 2)
				theContent.trimToSize();
		} finally {
			lock.unlock();
		}
		fireContentEvent("", oldValue, 0, true, -1, -1);
		fireContentEvent(text, text, 0, false, theSelectionAnchor, theCursor);
		return this;
	}

	private void fireSelectionEvent(int oldAnchor, int oldCursor, int newAnchor, int newCursor, java.util.List<StyledSequence> before,
		java.util.List<StyledSequence> after) {
		clearCache();
		SelectableDocumentModel.SelectionChangeEvent contentEvt = new SelectionChangeEventImpl(this, newAnchor, newCursor);
		int oldMin = oldAnchor;
		int oldMax = oldCursor;
		if(oldMin > oldMax) {
			int temp = oldMin;
			oldMin = oldMax;
			oldMax = temp;
		}
		int newMin = newAnchor;
		int newMax = newCursor;
		if(newMin > newMax) {
			int temp = newMin;
			newMin = newMax;
			newMax = temp;
		}
		int start;
		int end;
		if(oldMin == oldMax) {
			start = newMin;
			end = newMax;
		} else if(newMin == newMax) {
			start = oldMin;
			end = oldMax;
		} else if(oldMax < newMin) {
			start = oldMin;
			end = newMax;
		} else if(newMax < oldMin) {
			start = newMin;
			end = oldMax;
		} else {
			start = Math.min(oldMin, newMin);
			end = Math.max(oldMax, newMax);
		}

		if(start < end) {
			MuisDocumentModel.StyleChangeEvent styleEvt = new StyleChangeEventImpl(this, start, end, before, after);
			for(MuisDocumentModel.StyleListener listener : theStyleListeners)
				listener.styleChanged(styleEvt);
		}

		for(SelectableDocumentModel.SelectionListener listener : theSelectionListeners)
			listener.selectionChanged(contentEvt);
	}

	private void fireContentEvent(String value, String change, int index, boolean remove, int anchor, int cursor) {
		clearCache();
		MuisDocumentModel.ContentChangeEvent evt;
		if(anchor < 0 && cursor < 0)
			evt= new ContentChangeEventImpl(this, value, change, index, remove);
		else
			evt = new ContentAndSelectionChangeEventImpl(this, value, change, index, remove, anchor, cursor);
		for(MuisDocumentModel.ContentListener listener : theContentListeners)
			listener.contentChanged(evt);
	}

	private static class ContentChangeEventImpl implements ContentChangeEvent {
		private final SimpleDocumentModel theModel;

		private final String theValue;

		private final String theChange;

		private final int theIndex;

		private final boolean isRemove;

		/**
		 * @param model The document model whose content changed
		 * @param value The document model's content after the change
		 * @param change The section of content that was added or removed
		 * @param index The index of the addition or removal
		 * @param remove Whether this change represents a removal or an addition
		 */
		ContentChangeEventImpl(SimpleDocumentModel model, String value, String change, int index, boolean remove) {
			theModel = model;
			theValue = value;
			theChange = change;
			theIndex = index;
			isRemove = remove;
		}

		@Override
		public SimpleDocumentModel getModel() {
			return theModel;
		}

		@Override
		public String getValue() {
			return theValue;
		}

		@Override
		public String getChange() {
			return theChange;
		}

		@Override
		public int getIndex() {
			return theIndex;
		}

		@Override
		public boolean isRemove() {
			return isRemove;
		}
	}

	private static class StyleChangeEventImpl implements StyleChangeEvent {
		private final SelectableDocumentModel theDocument;

		private final int theStart;
		private final int theEnd;

		private final Iterable<StyledSequence> theBeforeStyles;
		private final Iterable<StyledSequence> theAfterStyles;

		StyleChangeEventImpl(SelectableDocumentModel document, int start, int end, Iterable<StyledSequence> beforeStyles,
			Iterable<StyledSequence> afterStyles) {
			super();
			theDocument = document;
			theStart = start;
			theEnd = end;
			theBeforeStyles = prisms.util.ArrayUtils.immutableIterable(beforeStyles);
			theAfterStyles = prisms.util.ArrayUtils.immutableIterable(afterStyles);
		}

		@Override
		public MuisDocumentModel getModel() {
			return theDocument;
		}

		@Override
		public int getStart() {
			return theStart;
		}

		@Override
		public int getEnd() {
			return theEnd;
		}

		@Override
		public Iterable<StyledSequence> styleBefore() {
			return theBeforeStyles;
		}

		@Override
		public Iterable<StyledSequence> styleAfter() {
			return theAfterStyles;
		}
	}

	private static class SelectionChangeEventImpl implements SelectionChangeEvent {
		private final SimpleDocumentModel theModel;

		private final int theSelectionAnchor;

		private final int theCursor;

		/**
		 * @param model The document model whose selection changed
		 * @param anchor The location of the model's selection anchor
		 * @param cursor The location of the model's cursor
		 */
		SelectionChangeEventImpl(SimpleDocumentModel model, int anchor, int cursor) {
			theModel = model;
			theSelectionAnchor = anchor;
			theCursor = cursor;
		}

		/** @return The document model whose selection changed */
		@Override
		public SimpleDocumentModel getModel() {
			return theModel;
		}

		/** @return The location of the model's selection anchor */
		@Override
		public int getSelectionAnchor() {
			return theSelectionAnchor;
		}

		/** @return The location of the model's cursor */
		@Override
		public int getCursor() {
			return theCursor;
		}
	}

	private static class ContentAndSelectionChangeEventImpl extends ContentChangeEventImpl implements SelectionChangeEvent {
		private final int theAnchor;

		private final int theCursor;

		/**
		 * @param model The document model whose content changed
		 * @param value The document model's content after the change
		 * @param change The section of content that was added or removed
		 * @param index The index of the addition or removal
		 * @param remove Whether this change represents a removal or an addition
		 */
		ContentAndSelectionChangeEventImpl(SimpleDocumentModel model, String value, String change, int index, boolean remove, int cursor, int anchor) {
			super(model, value, change, index, remove);
			theCursor=cursor;
			theAnchor=anchor;
		}

		@Override
		public int getSelectionAnchor() {
			return theAnchor;
		}

		@Override
		public int getCursor() {
			return theCursor;
		}
	}

	private class SelectionStyle extends org.muis.core.style.stateful.AbstractInternallyStatefulStyle {
		SelectionStyle(InternallyStatefulStyle parent, final boolean selected) {
			addDependency(parent);
			// TODO Not 100% sure I need this listener--maybe the dependency handles it automatically but I don't think so
			parent.addStateChangeListener(new org.muis.core.style.stateful.StateChangeListener() {
				@Override
				public void stateChanged(StateChangedEvent evt) {
					setState(selected ? prisms.util.ArrayUtils.add(evt.getNewState(), TEXT_SELECTION) : evt.getNewState());
				}
			});
			setState(selected ? prisms.util.ArrayUtils.add(parent.getState(), TEXT_SELECTION) : parent.getState());
		}
	}
}
