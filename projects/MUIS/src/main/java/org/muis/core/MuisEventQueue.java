package org.muis.core;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

import org.muis.util.MuisUtils;

import prisms.util.ArrayUtils;
import prisms.util.ProgramTracker;

/** The event queue in MUIS which makes sure elements's states stay up-to-date */
public class MuisEventQueue {
	private static long DEBUG_TRACKING = 0;

	/** Represents an event that may be queued for handling by the MuisEventQueue */
	public static interface Event {
		/** @return The time at which this event was created */
		long getTime();

		/**
		 * Checks whether the event should be handled now or stay in the queue and be handled later
		 *
		 * @param time The time to use in determining whether this event should be handled now
		 * @return Whether the event should be handled now or stay in the queue and be handled later
		 */
		boolean shouldHandle(long time);

		/** Executes this event's action */
		void handle();

		/** Called when the event queue discards this event because it has been {@link #isSupersededBy(Event) superseded} */
		void discard();

		/** @return Whether this event's {@link #handle()} method is currently executing */
		boolean isHandling();

		/** @return Whether this event's {@link #handle()} method has finished executing or its {@link #discard()} method was called */
		boolean isFinished();

		/** @return Whether this event's {@link #discard()} method was called */
		boolean isDiscarded();

		/**
		 * Compares this event's functionality with that of another event. If this method returns true, it signifies that this event's
		 * action is completely contained by the functionality of the given event such that this event may be discarded and not handled.
		 * This method must obey a contract similar to {@link Comparable#compareTo(Object)}, in that if A supersedes B and B supersedes C,
		 * then A supersedes C.
		 *
		 * @param evt The event to compare with
		 * @return Whether this event's functionality encompasses the functionality of the given event such that the given event need not be
		 *         acted on
		 */
		boolean isSupersededBy(Event evt);

		/** @return This event's priority. This value must not change for the event. */
		int getPriority();

		/**
		 * Compares the priority of two events whose {@link #getPriority()} methods return the same value.
		 *
		 * @param evt The event to compare with
		 * @return The relative priority of this event compared to <code>o2</code>
		 */
		int comparePriority(Event evt);

		/** @param err The error that was thrown by this event */
		void handleError(Throwable err);
	}

	/** Implements the trivial parts of {@link Event} */
	public static abstract class AbstractEvent implements Event {
		private final long theTime;

		private final int thePriority;

		private volatile boolean isHandling;

		private volatile boolean isHandled;

		private volatile boolean isDiscarded;

		/** @param priority The priority of this event */
		public AbstractEvent(int priority) {
			theTime = System.currentTimeMillis();
			thePriority = priority;
		}

		@Override
		public long getTime() {
			return theTime;
		}

		@Override
		public boolean shouldHandle(long time) {
			return true;
		}

		@Override
		public final void handle() {
			isHandling = true;
			try {
				doHandleAction();
			} finally {
				isHandled = true;
				isHandling = false;
			}
		}

		/** Executes this event's action */
		protected abstract void doHandleAction();

		@Override
		public void discard() {
			isDiscarded = true;
		}

		@Override
		public boolean isHandling() {
			return isHandling;
		}

		@Override
		public boolean isFinished() {
			return isHandled || isDiscarded;
		}

		@Override
		public boolean isDiscarded() {
			return isDiscarded;
		}

		@Override
		public boolean isSupersededBy(Event evt) {
			return false;
		}

		@Override
		public int getPriority() {
			return thePriority;
		}

		@Override
		public int comparePriority(Event evt) {
			return 0;
		}
	}

	/** Represents an element's need to be redrawn */
	public static class PaintEvent extends AbstractEvent {
		/** The priority of paint events */
		public static final int PRIORITY = 0;

		private final MuisElement theElement;

		private final Rectangle theArea;

		private final boolean isNow;

		/**
		 * @param element The element that needs to be repainted
		 * @param area The area in the element that needs to be repainted, or null if the element needs to be repainted entirely
		 * @param now Whether the repaint should happen quickly or be processed in normal time
		 */
		public PaintEvent(MuisElement element, Rectangle area, boolean now) {
			super(PRIORITY);
			theElement = element;
			theArea = area;
			isNow = now;
		}

		/** @return The element that needs to be repainted */
		public MuisElement getElement() {
			return theElement;
		}

		/** @return The area in the element that needs to be repainted, or null if the element needs to be repainted entirely */
		public Rectangle getArea() {
			return theArea;
		}

		/** @return Whether this event is meant to happen immediately or in normal time */
		public boolean isNow() {
			return isNow;
		}

		@Override
		public boolean shouldHandle(long time) {
			return isNow || time >= theElement.getPaintDirtyTime() + MuisEventQueue.get().getPaintDirtyTolerance();
		}

		@Override
		protected void doHandleAction() {
			MuisDocument doc = theElement.getDocument();
			MuisElement element = theElement;
			Rectangle area = theArea;
			if(element != doc.getRoot() && element.isTransparent()) {
				Point docPos = element.getDocumentPosition();
				if(area == null)
					area = element.getBounds().getBounds();
				area.x += docPos.x;
				area.y += docPos.y;
				element = doc.getRoot();
			}
			if(element == doc.getRoot()) {
				MuisRendering render = new MuisRendering(element.bounds().getWidth(), element.bounds().getHeight());
				Graphics2D graphics = (Graphics2D) render.getImage().getGraphics();
				if(doc.getDebugGraphics() != null)
					graphics = new org.muis.util.AggregateGraphics(graphics, doc.getDebugGraphics());
				render.setRoot(element.paint(graphics, area));
				doc.setRender(render);
				Graphics2D docGraphics = doc.getGraphics();
				if(docGraphics != null)
					docGraphics.drawImage(render.getImage(), null, 0, 0);
				return;
			}
			MuisRendering render = element.getDocument().getRender();
			if(render == null)
				return;
			@SuppressWarnings("rawtypes")
			MuisElementCapture bound = render.getFor(element);
			if(bound == null) {
				// Hierarchy may have been restructured. Need to repaint everything.
				element.getDocument().getRoot().repaint(null, false);
				return;
			}
			MuisRendering newRender = render.clone();
			bound = newRender.getFor(element);
			Point trans = bound.getDocLocation();
			Graphics2D graphics = (Graphics2D) newRender.getImage().getGraphics();
			if(doc.getDebugGraphics() != null)
				graphics = new org.muis.util.AggregateGraphics(graphics, doc.getDebugGraphics());
			MuisElementCapture<?> newBound;
			graphics.translate(trans.x, trans.y);
			try {
				newBound = element.paint(graphics, area);
			} finally {
				graphics.translate(-trans.x, -trans.y);
			}
			Graphics2D docGraphics = doc.getGraphics();
			if(docGraphics != null)
				docGraphics.drawImage(newRender.getImage().getSubimage(trans.x, trans.y, newBound.getWidth(), newBound.getHeight()),
					trans.x, trans.y, null);
			if(bound.getParent() != null)
				bound.getParent().getChildren().set(bound.getParent().getChildren().indexOf(bound), newBound);
			else
				render.setRoot(newBound);
			element.getDocument().setRender(newRender);
		}

		@Override
		public boolean isSupersededBy(Event evt) {
			if(!isNow) {
				if(evt instanceof ReboundEvent && MuisUtils.isAncestor(((ReboundEvent) evt).getElement(), theElement))
					return true;
				if(evt instanceof LayoutEvent && MuisUtils.isAncestor(((LayoutEvent) evt).getElement(), theElement))
					return true;
			}
			if(!(evt instanceof PaintEvent))
				return false;
			PaintEvent paint = (PaintEvent) evt;

			if(!MuisUtils.isAncestor(paint.theElement, theElement))
				return false;
			if(paint.theArea == null)
				return true; // Element will be repainted with its ancestor

			Rectangle area = MuisUtils.relative(paint.theArea, paint.theElement, theElement);
			Rectangle area2 = theArea;
			if(area2 != null) {
				if(area.x <= area2.x && area.y <= area2.y && area.x + area.width >= area2.x + area2.width
					&& area.y + area.height >= area2.y + area2.height)
					return true; // Element's area will be repainted with its ancestor
			} else if(area.x <= 0 && area.y <= 0 && area.x + area.width >= theElement.bounds().getWidth()
				&& area.y + area.height >= theElement.bounds().getHeight())
				return true; // Element will be repainted with its ancestor
			return false;
		}

		@Override
		public void handleError(Throwable err) {
			theElement.msg().error("Rendering error", err);
		}

		@Override
		public String toString() {
			return "Paint event for " + theElement;
		}
	}

	/** Represents an element's need to lay out its children */
	public static class LayoutEvent extends AbstractEvent {
		/** The priority of layout events */
		public static final int PRIORITY = 10;

		private final MuisElement theElement;

		private final boolean isNow;

		/**
		 * @param element The element that needs to be layed out
		 * @param now Whether the layout should happen quickly or be processed in normal time
		 */
		public LayoutEvent(MuisElement element, boolean now) {
			super(PRIORITY);
			theElement = element;
			isNow = now;
		}

		/** @return The element that needs to be layed out */
		public MuisElement getElement() {
			return theElement;
		}

		/** @return Whether this event is meant to happen immediately or in normal time */
		public boolean isNow() {
			return isNow;
		}

		@Override
		public boolean shouldHandle(long time) {
			return isNow || time >= theElement.getLayoutDirtyTime() + MuisEventQueue.get().getLayoutDirtyTolerance();
		}

		@Override
		protected void doHandleAction() {
			theElement.doLayout();
		}

		@Override
		public boolean isSupersededBy(Event evt) {
			return evt instanceof LayoutEvent && ((LayoutEvent) evt).theElement == theElement;
		}

		@Override
		public int comparePriority(Event evt) {
			if(!(evt instanceof LayoutEvent))
				return super.comparePriority(evt);
			if(MuisUtils.isAncestor(theElement, ((LayoutEvent) evt).theElement))
				return 1;
			if(MuisUtils.isAncestor(((LayoutEvent) evt).theElement, theElement))
				return -1;
			return MuisUtils.getDepth(((LayoutEvent) evt).theElement) - MuisUtils.getDepth(theElement);
		}

		@Override
		public void handleError(Throwable err) {
			theElement.msg().error("Layout error", err);
		}

		@Override
		public String toString() {
			return "Layout event for " + theElement;
		}
	}

	/** Represents a request to set an element's bounds */
	public static class ReboundEvent extends AbstractEvent {
		/** The priority of rebound events */
		public static final int PRIORITY = 20;

		private final MuisElement theElement;

		private final Rectangle theBounds;

		/**
		 * @param element The element to set the bounds of
		 * @param bounds The bounds to set on the element
		 */
		public ReboundEvent(MuisElement element, Rectangle bounds) {
			super(PRIORITY);
			theElement = element;
			theBounds = bounds;
		}

		/** @return The element whose bounds need to be set */
		public MuisElement getElement() {
			return theElement;
		}

		/** @return The bounds that will be set on the element */
		public Rectangle getBounds() {
			return theBounds;
		}

		@Override
		protected void doHandleAction() {
			theElement.bounds().setBounds(theBounds.x, theBounds.y, theBounds.width, theBounds.height);
		}

		@Override
		public boolean isSupersededBy(Event evt) {
			return evt instanceof ReboundEvent && evt.getTime() > getTime();
		}

		@Override
		public boolean shouldHandle(long time) {
			return time - getTime() > 50;
		}

		@Override
		public void handleError(Throwable err) {
			theElement.msg().error("Rebound error", err);
		}

		@Override
		public String toString() {
			return "Bound event for " + theElement + " to " + theBounds;
		}
	}

	/** Represents a position event that was captured and needs to be propagated */
	public static class PositionQueueEvent extends AbstractEvent {
		/** The priority of mouse events */
		public static final int PRIORITY = 100;

		private final MuisElement theRoot;

		private final org.muis.core.event.PositionedUserEvent theEvent;

		private final boolean isDownward;

		/**
		 * @param root The root from which this event fires downward or to which it fires upward
		 * @param evt The position event to propagate
		 * @param downward Whether this event fires downward from the root to the deepest level or the reverse
		 */
		public PositionQueueEvent(MuisElement root, org.muis.core.event.PositionedUserEvent evt, boolean downward) {
			super(PRIORITY);
			theRoot = root;
			theEvent = evt;
			isDownward = downward;
		}

		/** @return The root from which this event fires downward or to which it fires upward */
		public MuisElement getRoot() {
			return theRoot;
		}

		/** @return The event to be propagated */
		public org.muis.core.event.PositionedUserEvent getEvent() {
			return theEvent;
		}

		/** @return Whether this event fires downward from the root to the deepest level or the reverse */
		public boolean isDownward() {
			return isDownward;
		}

		@Override
		protected void doHandleAction() {
			if(theEvent.getCapture() == null) // Non-positioned event
			{
				if(isDownward)
					for(MuisElement pathEl : MuisUtils.path(theEvent.getElement()))
						pathEl.fireEvent(theEvent, pathEl != theEvent.getElement(), false);
				else {
					MuisElement el = theEvent.getElement();
					while(el != null) {
						el.fireEvent(theEvent, el != theEvent.getElement(), false);
						el = el.getParent();
					}
				}
			} else
				for(MuisEventPositionCapture<?> el : theEvent.getCapture().iterate(!isDownward))
					el.getElement().fireEvent(theEvent.copyFor(el.getElement()), theEvent.isCanceled(), false);
		}

		@Override
		public void handleError(Throwable err) {
			theEvent.getElement().msg().error("User event error", err);
		}

		@Override
		public String toString() {
			return "User positioned event: " + theEvent;
		}
	}

	/** Represents a non-positioned user event that needs to be fired */
	public static class UserQueueEvent extends AbstractEvent {
		/** The priority of user events */
		public static int PRIORITY = 100;

		private final org.muis.core.event.UserEvent theEvent;

		private final boolean isDownward;

		/**
		 * @param evt The event to fire
		 * @param downward Whether the event should fire from root to deepest element or the reverse
		 */
		public UserQueueEvent(org.muis.core.event.UserEvent evt, boolean downward) {
			super(PRIORITY);
			theEvent = evt;
			isDownward = downward;
		}

		/** @return The event that needs to be fired */
		public org.muis.core.event.UserEvent getEvent() {
			return theEvent;
		}

		/** @return Whether the event will be fired from root to deepest element or the reverse */
		public boolean isDownward() {
			return isDownward;
		}

		@Override
		protected void doHandleAction() {
			if(isDownward)
				for(MuisElement pathEl : MuisUtils.path(theEvent.getElement()))
					pathEl.fireEvent(theEvent, pathEl != theEvent.getElement(), false);
			else {
				MuisElement el = theEvent.getElement();
				while(el != null) {
					el.fireEvent(theEvent, el != theEvent.getElement(), false);
					el = el.getParent();
				}
			}
		}

		@Override
		public void handleError(Throwable err) {
			theEvent.getElement().msg().error("User event error", err);
		}

		@Override
		public String toString() {
			return "User event: " + theEvent;
		}
	}

	private static final MuisEventQueue theInstance = new MuisEventQueue();

	/** @return The instance of the queue to use to schedule core events */
	public static MuisEventQueue get() {
		return theInstance;
	}

	/** @return Whether the current thread is the MUIS event queue thread */
	public static boolean isEventThread() {
		MuisEventQueue inst = theInstance;
		if(inst == null)
			return false;
		return Thread.currentThread() == inst.theThread;
	}

	private Event [] theEvents;

	private java.util.Comparator<Event> theComparator;

	final Object theLock;

	private volatile Thread theThread;

	private volatile boolean isShuttingDown;

	volatile boolean isInterrupted;

	volatile boolean hasNewEvent;

	private long theFrequency;

	private long thePaintDirtyTolerance;

	private long theLayoutDirtyTolerance;

	private boolean isPrioritized;

	private prisms.util.ProgramTracker theTracker;

	private MuisEventQueue() {
		theEvents = new Event[0];
		theComparator = new java.util.Comparator<Event>() {
			@Override
			public int compare(Event o1, Event o2) {
				int diff = o1.getPriority() - o2.getPriority();
				if(diff != 0)
					return -diff;
				diff = o1.comparePriority(o2);
				if(diff != 0)
					return -diff;
				diff = o2.comparePriority(o1);
				if(diff != 0)
					return diff;
				long timeDiff = o1.getTime() - o2.getTime();
				return timeDiff < 0 ? -1 : (timeDiff > 0 ? 1 : 0);
			}
		};
		theLock = new Object();
		theFrequency = 50;
		thePaintDirtyTolerance = 10;
		theLayoutDirtyTolerance = 10;
		isPrioritized = true;
		theTracker = new prisms.util.ProgramTracker("MUIS Events");
	}

	/**
	 * Schedules an event
	 *
	 * @param event The event that MUIS needs to take action on
	 * @param now Whether to take action on the event immediately or allow it to execute when the queue gets to it
	 */
	public void scheduleEvent(Event event, boolean now) {
		ProgramTracker.TrackNode schedule = null;
		if(DEBUG_TRACKING > 0 && isEventThread())
			schedule = theTracker.start("scheduleEvent");
		Event [] events = theEvents;
		for(Event evt : events) {
			if(evt.isHandling() || evt.isFinished())
				continue;
			if(event.isSupersededBy(evt)) {
				event.discard();
				if(schedule != null)
					theTracker.end(schedule);
				return;
			} else if(evt.isSupersededBy(event)) {
				evt.discard();
				remove(evt, schedule != null);
			}
		}
		addEvent(event, now);
		if(schedule != null)
			theTracker.end(schedule);
	}

	private void addEvent(Event event, boolean now) {
		ProgramTracker.TrackNode add = null;
		if(DEBUG_TRACKING > 0 && isEventThread())
			add = theTracker.start("addEvent");
		int spot;
		synchronized(theLock) {
			if(isPrioritized) {
				spot = java.util.Arrays.binarySearch(theEvents, event, theComparator);
				if(spot < 0)
					spot = -(spot + 1);
			} else
				spot = theEvents.length;
			theEvents = ArrayUtils.add(theEvents, event, spot);
		}
		hasNewEvent = true;
		start();
		if(now && spot == 0) {
			isInterrupted = true;
		}
		if(theThread != null)
			theThread.interrupt();
		if(add != null)
			theTracker.end(add);
	}

	void remove(Event event, boolean debug) {
		ProgramTracker.TrackNode remove = null;
		if(debug)
			remove = theTracker.start("removeEvent");
		synchronized(theLock) {
			theEvents = ArrayUtils.remove(theEvents, event);
		}
		if(remove != null)
			theTracker.end(remove);
	}

	Event [] getEvents() {
		return theEvents;
	}

	private void start() {
		if(theThread != null)
			return;
		new EventQueueThread().start();
	}

	/**
	 * Can't think why this should be called, but if it's needed we can change the modifier to public or provide some mechanism to access it
	 */
	@SuppressWarnings("unused")
	private void shutdown() {
		isShuttingDown = true;
	}

	/** @return Whether this event queue is currently running */
	public boolean isRunning() {
		return theThread != null;
	}

	/** @return Whether this event queue is shutting down. Not currently used. */
	public boolean isShuttingDown() {
		return isShuttingDown;
	}

	/** @return The frequency with which this event queue handles its events */
	public long getFrequency() {
		return theFrequency;
	}

	/**
	 * @return The amount of time for which this queue will let paint events rest until {@link MuisElement#repaint(Rectangle, boolean)}
	 *         stops being called repeatedly
	 */
	public long getPaintDirtyTolerance() {
		return thePaintDirtyTolerance;
	}

	/**
	 * @return The amount of time for which this queue will let layout events rest until {@link MuisElement#relayout(boolean)} stops being
	 *         called repeatedly
	 */
	public long getLayoutDirtyTolerance() {
		return theLayoutDirtyTolerance;
	}

	/** @return The program tracker to use for debugging */
	public prisms.util.ProgramTracker track() {
		return theTracker;
	}

	private class EventQueueThread extends Thread {
		private long theTrackMark;

		EventQueueThread() {
			super("MUIS Event Queue");
		}

		/**
		 * The queue thread�s action is to go through the events in the queue. When an event is come to, the dirty paint or layout state of
		 * the element is checked. If not dirty, remove the event and do nothing. If the dirty time is <=10ms ago, skip the event so that it
		 * is checked again the next 50ms. Heavy, multiple-op processes on elements from external threads will cause few layout/redraw
		 * actions this way, but after ops finish, layout/redraw will happen within 60ms, average 35ms.
		 */
		@Override
		public void run() {
			synchronized(theLock) {
				if(theThread != null)
					return;
				theThread = this;
			}
			if(DEBUG_TRACKING > 0)
				theTrackMark = System.currentTimeMillis();
			while(!isShuttingDown()) {
				processEvents();
				if(DEBUG_TRACKING > 0) {
					long now = System.currentTimeMillis();
					if(now - theTrackMark >= DEBUG_TRACKING) {
						theTrackMark = now;
						theTracker.printData(5);
						theTracker.clear();
					}
				}
			}
			theThread = null;
			isShuttingDown = false;
		}

		private void processEvents() {
			ProgramTracker.TrackNode processEvents = null;
			if(DEBUG_TRACKING > 0)
				processEvents = theTracker.start("processEvents");
			try {
				isInterrupted = false;
				ProgramTracker.TrackNode getEvents = null;
				if(DEBUG_TRACKING > 0)
					getEvents = theTracker.start("getEvents");
				Event [] events = getEvents();
				if(getEvents != null)
					theTracker.end(getEvents);
				hasNewEvent = false;
				boolean acted = false;
				long now = System.currentTimeMillis();
				for(Event evt : events) {
					if(isInterrupted) {
						break;
					}
					if(evt.isFinished())
						continue;
					if(evt.shouldHandle(now)) {
						acted = true;
						remove(evt, processEvents != null);
						ProgramTracker.TrackNode handle = null;
						if(DEBUG_TRACKING > 0)
							handle = theTracker.start("handleEvent " + evt);
						try{
							evt.handle();
						} catch(Throwable e){
							evt.handleError(e);
						}
						if(handle != null)
							theTracker.end(handle);
						now = System.currentTimeMillis(); // Update the time, since the action may have taken some
					}
				}
				if(processEvents != null)
					theTracker.end(processEvents);
				if(!acted && !hasNewEvent && !isInterrupted)
					Thread.sleep(getFrequency());
			} catch(InterruptedException e) {
			}
		}
	}
}
