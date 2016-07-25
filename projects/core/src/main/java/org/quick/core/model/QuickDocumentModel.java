package org.quick.core.model;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import org.qommons.Transaction;
import org.quick.core.model.QuickDocumentModel.StyledSequence;
import org.quick.core.style.QuickStyle;

/** Stores and displays text in Quick */
public interface QuickDocumentModel extends CharSequence, Iterable<StyledSequence> {
	/**
	 * A piece of text with style attributes. It is important that every implementation of this interface implement {@link #toString()} as
	 * specified by {@link CharSequence#toString()}.
	 */
	public static interface StyledSequence extends CharSequence {
		/** @return The style for this sequence */
		QuickStyle getStyle();
	}

	/** A piece of text with style attributes and metrics information. The content of a StyledSequenceMetrics never contains a line break. */
	public static interface StyledSequenceMetric extends StyledSequence {
		/** @return The width of this piece of text */
		float getWidth();

		/** @return The height of this piece of text */
		float getHeight();

		/** @return The baseline of this piece of text */
		float getBaseline();

		/** @return Whether this sequence is the first of a new line. This may return false for the first line. */
		boolean isNewLine();

		@Override
		StyledSequenceMetric subSequence(int start, int end);

		/** @return The top position of this sequence in the document */
		float getTop();

		/** @return The left position of this sequence in the document */
		float getLeft();

		/**
		 * @param advance The x-coordinate, in pixels, relative to the left edge of this sequence
		 * @return The position, in characters, along this sequence that the given advance hits
		 */
		float getHitPosition(float advance);

		/**
		 * @param position The position, in characters, along this sequence
		 * @return The x-coordinate, in pixels, relative to the beginning of this sequence of the given position
		 */
		float getLocation(float position);

		/**
		 * Draws this sequence
		 *
		 * @param graphics The graphics to draw the text sequence to
		 * @param x The x-coordinate of the top left corner of the graphics to draw the text to
		 * @param y The y-coordinate of the top left corner of the graphics to draw the text to
		 */
		void draw(Graphics2D graphics, float x, float y);
	}

	/** Fired when a document model's content changes */
	public static interface ContentChangeEvent {
		/** @return The document model whose content changed */
		QuickDocumentModel getModel();

		/** @return The document model's content after the change */
		String getValue();

		/** @return The section of content that was added or removed */
		String getChange();

		/** @return The index of the addition or removal */
		int getIndex();

		/** @return Whether this change represents a removal or an addition */
		boolean isRemove();

		/** @return The event or other thing that caused this event */
		Object getCause();
	}

	/** Fired when a document model's style changes for any portion of its content */
	public static interface StyleChangeEvent {
		/** @return The document model whose style changed */
		QuickDocumentModel getModel();

		/** @return The start index (inclusive) of the interval for which the style changed */
		int getStart();

		/** @return The end index (exclusive) of the interval for which the style changed */
		int getEnd();

		/** @return The styles of the document before the style change. This may be null if the information is not available. */
		Iterable<StyledSequence> styleBefore();

		/** @return The styles of the document after the style change. This may be null if the information is not available. */
		Iterable<StyledSequence> styleAfter();

		/** @return The event or other thing that caused this event */
		Object getCause();
	}

	/** Listens for changes to a document's content */
	public static interface ContentListener {
		/** @param evt The event containing information about the content change */
		void contentChanged(ContentChangeEvent evt);
	}

	/** Listens for changes to a document's style */
	public static interface StyleListener {
		/** @param evt The event containing information about the style change */
		void styleChanged(StyleChangeEvent evt);
	}

	/**
	 * @param position The position to get the style for
	 * @return The style at the given position
	 */
	QuickStyle getStyleAt(int position);

	/**
	 * @param position The position to begin iteration from
	 * @return This document's content starting from the given position
	 */
	Iterable<StyledSequence> iterateFrom(int position);

	/**
	 * @param start The position to begin iteration from
	 * @param end The position to end iteration at
	 * @return This document's content starting from the given position
	 */
	Iterable<StyledSequence> iterateFrom(int start, int end);

	/**
	 * @param start The starting position within the document
	 * @param breakWidth The width, in pixels, to break lines off at in the document
	 * @return The metrics for this document. The first metric's {@link StyledSequenceMetric#isNewLine() newLine} attribute may be false
	 *         even if a new line occurred at position start-1.
	 */
	Iterable<StyledSequenceMetric> metrics(int start, float breakWidth);

	/**
	 * @param x The x-coordinate, in pixels, relative to this document's top left corner
	 * @param y The y-coordinate, in pixels, relative to this document's top left corner
	 * @param breakWidth The width, in pixels, to break lines off at in the document
	 * @return The character position at the given point in the document
	 */
	float getPositionAt(float x, float y, int breakWidth);

	/**
	 * @param position The character position in the document
	 * @param breakWidth The width, in pixels, to break lines off at in the document
	 * @return The position in the document at the given character at the top of the line
	 */
	Point2D getLocationAt(float position, int breakWidth);

	/**
	 * Draws a portion of this document
	 *
	 * @param graphics The graphics to draw on
	 * @param window The window within this document to draw
	 * @param breakWidth The width, in pixels, to break lines off at in the document
	 */
	void draw(Graphics2D graphics, Rectangle window, int breakWidth);

	/** @param listener The listener to be notified when this model's content changes */
	void addContentListener(ContentListener listener);

	/** @param listener The listener to stop notification for */
	void removeContentListener(ContentListener listener);

	/** @param listener The listener to be notified when this model's style changes */
	void addStyleListener(StyleListener listener);

	/** @param listener The listener to stop notification for */
	void removeStyleListener(StyleListener listener);

	/** @param run The listener to invoke when either this document's content or its style changes */
	default void onContentStyleChange(Runnable run) {
		addContentListener(evt -> {
			run.run();
		});
		addStyleListener(evt -> {
			run.run();
		});
	}

	/** @return A transaction that prevents any other threads from modifying this document model until the transaction is closed */
	Transaction holdForRead();
}
