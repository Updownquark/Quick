package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.KeyCode;
import org.observe.quick.Positionable;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickKeyListener;
import org.observe.quick.QuickMouseListener;
import org.observe.quick.QuickMouseListener.MouseButton;
import org.observe.quick.QuickMouseListener.MouseMoveEventType;
import org.observe.quick.QuickMouseListener.QuickMouseButtonListener;
import org.observe.quick.QuickSize;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.Sizeable;
import org.observe.quick.draw.LightWeightShapeCollection;
import org.observe.quick.draw.QuickBorderedShape;
import org.observe.quick.draw.QuickCanvas;
import org.observe.quick.draw.QuickChart;
import org.observe.quick.draw.QuickChart.ChartAxis;
import org.observe.quick.draw.QuickChart.ChartAxis.Interpreted;
import org.observe.quick.draw.QuickDrawText;
import org.observe.quick.draw.QuickEllipse;
import org.observe.quick.draw.QuickFlexLine;
import org.observe.quick.draw.QuickLine;
import org.observe.quick.draw.QuickLinearShape;
import org.observe.quick.draw.QuickPoint;
import org.observe.quick.draw.QuickPolygon;
import org.observe.quick.draw.QuickRectangle;
import org.observe.quick.draw.QuickRotated;
import org.observe.quick.draw.QuickShape;
import org.observe.quick.draw.QuickShapeCollection;
import org.observe.quick.draw.QuickShapeContainer;
import org.observe.quick.draw.QuickShapePublisher;
import org.observe.quick.draw.QuickShapeView;
import org.observe.quick.draw.QuickSimpleShape;
import org.observe.quick.draw.Rotate;
import org.observe.quick.draw.Scale;
import org.observe.quick.draw.StrokeDashing;
import org.observe.quick.draw.ToCoords;
import org.observe.quick.draw.TransformOp;
import org.observe.quick.draw.Translate;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.BiTuple;
import org.qommons.Colors;
import org.qommons.FloatList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transformer;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MappedList;
import org.qommons.fn.FloatConsumer;
import org.qommons.fn.TriConsumer;
import org.qommons.io.ErrorReporting;

/** Swing implementation for Quick-Draw */
public class QuickDrawSwing implements QuickInterpretation {
	/**
	 * The transformation type required for a {@link QuickShapePublisher} to be drawn by {@link QuickDrawSwing}
	 *
	 * @param <P> The type of shape publisher
	 */
	public interface InterpretedQuickShapePublisher<P extends QuickShapePublisher> {
		/**
		 * @param element The instantiated publisher element
		 * @return The instantiated shape publisher
		 * @throws ModelInstantiationException If the publisher cannot be instantiated
		 */
		QuickDrawShapePublisher interpret(P element) throws ModelInstantiationException;
	}

	/** Instantiated shape publisher class provided and understood by {@link QuickDrawSwing} implementations */
	public interface QuickDrawShapePublisher {
		/** @return The set of shapes this publisher exposes */
		ObservableCollection<QuickShapeInterpretation> getShapes();
	}

	/**
	 * An interpreted transformation operation
	 *
	 * @param <T> The type of the Quick tranformation this interpretation is for
	 */
	public interface InterpretedTransformOp<T extends TransformOp> {
		/**
		 * @param element The instantiated Quick transformation
		 * @return The transformation implementation
		 * @throws ModelInstantiationException If the transformation could not be understood
		 */
		QuickDrawTransform interpret(T element) throws ModelInstantiationException;
	}

	/** Implementation of a Quick transformation in this interpretation */
	public interface QuickDrawTransform {
		/** @param source The transform to modify with this operation */
		void transform(AffineTransform source);

		/** @return Any change sources in this transform */
		Observable<?> update();
	}

	/** A qualitative representation of opacity */
	public enum Opacity {
		/** Completely transparent */
		None,
		/** Partially transparent */
		Partial,
		/** Completely opaque */
		Full;

		/**
		 * @param other Another opacity
		 * @return The maximum of the two opacities
		 */
		public Opacity or(Opacity other) {
			return QommonsUtils.max(this, other);
		}
	}

	/** An individual shape implementation for {@link QuickDrawSwing} */
	public interface QuickShapeInterpretation {
		/** @return An observable that will cause this shape to be redrawn */
		Observable<?> update();

		/**
		 * Draws this shape to a graphics configuration
		 *
		 * @param gfx The graphics to draw to
		 * @param screen The screen bounds
		 */
		void draw(Graphics2D gfx, Rectangle2D.Float screen);

		/**
		 * @param containerPoint The point in the container to test
		 * @return The corresponding location in this shape, or null if this shape does not contain the given point
		 */
		Point2D.Float hit(Point2D.Float containerPoint);

		/**
		 * @param point The point in this shape
		 * @return This shape's opacity at the given point
		 */
		Opacity getOpacity(Point2D.Float point);

		boolean isMouseListening();

		/**
		 * @param e The mouse event
		 * @return The deepest-level shape at the event's location
		 */
		QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return The deepest-level shape at the event's location
		 */
		QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point);

		/** @param e The mouse event */
		void mouseDragged(MouseEvent e, Point2D.Float point);

		/** @param e The mouse event */
		void mouseExited(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mousePressed(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mouseReleased(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mouseClicked(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point);

		/**
		 * @param e The key event
		 * @return Whether this shape was able to use the event
		 */
		boolean keyPressed(KeyEvent e);

		/**
		 * @param e The key event
		 * @return Whether this shape was able to use the event
		 */
		boolean keyReleased(KeyEvent e);

		/**
		 * @param e The key event
		 * @return Whether this shape was able to use the event
		 */
		boolean keyTyped(KeyEvent e);

		/** @return This shape's tooltip at the most recent hovered location */
		String getTooltip();

		/**
		 * @param hovered Whether the mouse cursor is currently hovered over this shape
		 * @param focused Whether this shape is the focus
		 * @param pressed Whether the left mouse button is currently pressed over this shape
		 * @param rightPressed Whether the right mouse button is currently pressed over this shape
		 */
		void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed);
	}

	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		tx.with(QuickCanvas.Interpreted.class, QuickSwingPopulator.class, QuickCanvasPopulator::new);
		tx.with(QuickShapeCollection.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedShapeCollection::new);
		tx.with(LightWeightShapeCollection.Interpreted.class, InterpretedQuickShapePublisher.class,
			InterpretedLightWeightShapeCollection::new);
		tx.with(QuickRectangle.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedRectangle::new);
		tx.with(QuickEllipse.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedEllipse::new);
		tx.with(QuickPolygon.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedPolygon::new);
		tx.with(QuickDrawText.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedText::new);
		tx.with(QuickLine.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedLine::new);
		tx.with(QuickFlexLine.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedFlexLine::new);
		tx.with(QuickShapeView.Interpreted.class, InterpretedShapeView.class, InterpretedShapeView::new);
		tx.with(Translate.Interpreted.class, InterpretedTransformOp.class, InterpretedTranslate::new);
		tx.with(Scale.Interpreted.class, InterpretedTransformOp.class, InterpretedScale::new);
		tx.with(Rotate.Interpreted.class, InterpretedTransformOp.class, InterpretedRotate::new);
		tx.with(ToCoords.Interpreted.class, InterpretedTransformOp.class, InterpretedToCoords::new);

		tx.with(QuickChart.Interpreted.class, InterpretedChart.class, InterpretedChart::new);
		tx.with(QuickChart.ChartAxis.Interpreted.class, InterpretedChartAxis.class, InterpretedChartAxis::new);
	}

	static class InterpretedShapeContainer {
		private final Map<Object, InterpretedQuickShapePublisher<?>> thePublishers;

		public InterpretedShapeContainer(ExElement.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			thePublishers = new HashMap<>();
			for (QuickShapePublisher.Interpreted<?> shape : interpreted.getAddOn(QuickShapeContainer.Interpreted.class).getShapes()) {
				thePublishers.put(shape.getIdentity(), tx.transform(shape, InterpretedQuickShapePublisher.class));
			}
		}

		public List<QuickDrawShapePublisher> getPublishers(ExElement quick) throws ModelInstantiationException {
			List<QuickDrawShapePublisher> publishers = new ArrayList<>(thePublishers.size());
			for (QuickShapePublisher p : quick.getAddOn(QuickShapeContainer.class).getShapes()) {
				InterpretedQuickShapePublisher<?> interpP = thePublishers.get(p.getIdentity());
				if (interpP != null)
					publishers.add(((InterpretedQuickShapePublisher<QuickShapePublisher>) interpP).interpret(p));
			}
			return publishers;
		}
	}

	static abstract class SimpleShapeContainer implements QuickShapeInterpretation, QuickDrawShapePublisher {
		private final ObservableCollection<QuickShapeInterpretation> theContents;
		private final Observable<?> theUpdate;
		private final BetterSet<ElementId> theHovered;
		private boolean isPressed;
		private boolean isRightPressed;
		private boolean isDrawing;

		SimpleShapeContainer(List<QuickDrawShapePublisher> publishers, Observable<?> until) {
			theContents = ObservableCollection.of(publishers).flow()//
				.flatMap(pub -> pub.getShapes().flow())//
				.refreshEach(shape -> shape.update().filter(__ -> !isDrawing))//
				.collectActive(until);
			theUpdate = Observable.onRootFinish(theContents.simpleChanges());
			theHovered = BetterHashSet.build().build();
		}

		protected abstract ListElement<QuickShapeInterpretation> getFocus();

		protected abstract void setFocus(ListElement<QuickShapeInterpretation> shape);

		protected boolean isFocused(ListElement<QuickShapeInterpretation> shape) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			return focus != null && shape.getElementId().equals(focus.getElementId());
		}

		public boolean isPressed() {
			return isPressed;
		}

		public boolean isRightPressed() {
			return isRightPressed;
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return getContents();
		}

		public ObservableCollection<QuickShapeInterpretation> getContents() {
			return theContents;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			isDrawing = true;
			try {
				for (ListElement<QuickShapeInterpretation> shape = theContents.getTerminalElement(true); shape != null; shape = shape
					.getAdjacent(true)) {
					setState(shape);
					shape.get().draw(gfx, screen);
				}
			} finally {
				isDrawing = false;
			}
		}

		@Override
		public boolean isMouseListening() {
			return theContents.stream().anyMatch(QuickShapeInterpretation::isMouseListening);
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point) {
			return mouseHover(e, point);
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point) {
			return mouseHover(e, point);
		}

		protected void setState(ListElement<QuickShapeInterpretation> shapeEl) {
			boolean hovered = theHovered.contains(shapeEl.getElementId());
			shapeEl.get().setState(hovered, isFocused(shapeEl), hovered && isPressed, hovered && isRightPressed);
		}

		protected QuickShapeInterpretation mouseHover(MouseEvent e, Point2D.Float point) {
			theHovered.removeIf(el -> !el.isPresent());
			QuickShapeInterpretation first = null;
			boolean done = false;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); el != null; el = el.getAdjacent(false)) {
				setState(el);
				Point2D.Float hit = el.get().hit(point);
				if (done && theHovered.remove(el.getElementId())) {
					setState(el);
					el.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), hit);
				} else if (hit != null) {
					boolean newHover = theHovered.add(el.getElementId());
					setState(el);
					if (newHover) {
						QuickShapeInterpretation target = el.get().mouseEntered(asType(e, MouseEvent.MOUSE_ENTERED), hit);
						if (first == null)
							first = target;
					} else {
						QuickShapeInterpretation target = el.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED), hit);
						if (first == null)
							first = target;
					}
					done = el.get().getOpacity(hit) == Opacity.Full;
				} else if (theHovered.remove(el.getElementId())) {
					setState(el);
					el.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), point);
				}
			}
			return first;
		}

		protected MouseEvent asType(MouseEvent source, int newId) {
			if (source.getID() == newId)
				return source;
			return new MouseEvent(source.getComponent(), MouseEvent.MOUSE_EXITED, source.getWhen(), source.getModifiers(), source.getX(),
				source.getY(), source.getClickCount(), source.isPopupTrigger(), source.getButton());
		}

		@Override
		public void mouseDragged(MouseEvent e, Point2D.Float point) {
			mouseAction(e, point, QuickShapeInterpretation::mouseDragged);
		}

		@Override
		public void mouseExited(MouseEvent e, Point2D.Float point) {
			isPressed = isRightPressed = true;
			Iterator<ElementId> hovered = theHovered.iterator();
			while (hovered.hasNext()) {
				ElementId el = hovered.next();
				if (!el.isPresent())
					continue;
				ListElement<QuickShapeInterpretation> shape = theContents.getElement(el);
				setState(shape);
				Point2D.Float hit = shape.get().hit(point);
				hovered.remove();
				setState(shape);
				shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), hit == null ? point : hit);
			}
		}

		@Override
		public Opacity mousePressed(MouseEvent e, Point2D.Float point) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = true;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = true;
			ListElement<QuickShapeInterpretation> focus = null;
			Opacity opacity = Opacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				Point2D.Float hit = shape.hit(point);
				if (hit == null)
					continue;
				Opacity shapeOpacity = shape.mousePressed(e, hit);
				if (shapeOpacity != Opacity.None)
					focus = el;
				opacity = opacity.or(shapeOpacity);
			}
			setFocus(focus);
			return opacity;
		}

		@Override
		public Opacity mouseReleased(MouseEvent e, Point2D.Float point) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = false;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = false;
			return mouseAction(e, point, QuickShapeInterpretation::mouseReleased);
		}

		@Override
		public Opacity mouseClicked(MouseEvent e, Point2D.Float point) {
			return mouseAction(e, point, QuickShapeInterpretation::mouseClicked);
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			return mouseAction(e, point, QuickShapeInterpretation::mouseWheelMoved);
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyPressed(e);
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyReleased(e);
			return true;
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyTyped(e);
			return true;
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			return containerPoint;
		}

		@Override
		public Opacity getOpacity(Point2D.Float point) {
			Opacity opacity = Opacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (shape == null)
					continue;
				setState(el);
				Point2D.Float hit = shape.hit(point);
				if (hit == null)
					continue;
				opacity = opacity.or(shape.getOpacity(hit));
			}
			return opacity;
		}

		public <E extends MouseEvent> Opacity mouseAction(E e, Point2D.Float point,
			TriConsumer<QuickShapeInterpretation, E, Point2D.Float> action) {
			Opacity opacity = Opacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				if (point != null) {
					Point2D.Float hit = shape.hit(point);
					if (hit == null)
						continue;
					action.accept(shape, e, point);
					opacity = opacity.or(shape.getOpacity(hit));
				} else
					action.accept(shape, e, point);
			}
			return opacity;
		}

		@Override
		public String getTooltip() {
			theHovered.removeIf(el -> !el.isPresent());
			for (ElementId hovered : theHovered.reverse()) {
				ListElement<QuickShapeInterpretation> shape = getShapes().getElement(hovered);
				setState(shape);
				String tooltip = shape.get().getTooltip();
				if (tooltip != null)
					return tooltip;
			}
			return null;
		}

		@Override
		public void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed) {
			isPressed = pressed;
			isRightPressed = rightPressed;
			if (!focused)
				setFocus(null);
		}
	}

	/**
	 * @param point The point to transform
	 * @param transform The transformation
	 * @return The transformed point
	 */
	public static Point2D.Float transform(Point2D.Float point, AffineTransform transform) {
		if (transform == null)
			return point;
		return (Point2D.Float) transform.transform(new Point2D.Float(point.x, point.y), new Point2D.Float(0, 0));
	}

	static class QuickCanvasPopulator extends QuickSwingPopulator.Abstract<QuickCanvas> {
		private final InterpretedShapeContainer thePublishing;

		QuickCanvasPopulator(QuickCanvas.Interpreted canvas, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			thePublishing = new InterpretedShapeContainer(canvas, tx);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickCanvas quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addComponent(null, new QuickCanvasComponent(quick, thePublishing.getPublishers(quick), panel.getUntil()),
				component::accept);
		}
	}

	static class QuickCanvasComponent extends JComponent {
		private final QuickCanvas theCanvas;
		private final SimpleShapeContainer theContainer;
		private final Sizeable theWidth;
		private final Sizeable theHeight;
		private ListElement<QuickShapeInterpretation> theFocus;

		QuickCanvasComponent(QuickCanvas canvas, List<QuickDrawShapePublisher> publishers, Observable<?> until) {
			theCanvas = canvas;
			theContainer = new SimpleShapeContainer(publishers, canvas.onDestroy()) {
				@Override
				protected ListElement<QuickShapeInterpretation> getFocus() {
					return theFocus;
				}

				@Override
				protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
					theFocus = shape;
				}

				@Override
				public String getTooltip() {
					return null;
				}
			};
			theContainer.update().takeUntil(until).act(__ -> repaint());
			theWidth = canvas.getAddOn(Sizeable.Horizontal.class);
			theHeight = canvas.getAddOn(Sizeable.Vertical.class);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					publishSize();
				}
			});
			publishSize();
			class EventListener extends MouseAdapter implements KeyListener {
				@Override
				public void mouseEntered(MouseEvent e) {
					theContainer.setState(true, false, theCanvas.isPressed().get(), theCanvas.isRightPressed().get());
					QuickShapeInterpretation shape = theContainer.mouseEntered(e, toShapePoint(e.getPoint()));
					String tooltip = shape == null ? theCanvas.getTooltip().get() : shape.getTooltip();
					setToolTipText(tooltip);
				}

				Point2D.Float toShapePoint(Point awtPoint) {
					Point2D.Float point = new Point2D.Float();
					point.x = awtPoint.x;
					point.y = awtPoint.y;
					return point;
				}

				@Override
				public void mouseMoved(MouseEvent e) {
					theContainer.setState(true, false, theCanvas.isPressed().get(), theCanvas.isRightPressed().get());
					QuickShapeInterpretation shape = theContainer.mouseMoved(e, toShapePoint(e.getPoint()));
					String tooltip = shape == null ? theCanvas.getTooltip().get() : shape.getTooltip();
					setToolTipText(tooltip);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					theContainer.mouseDragged(e, toShapePoint(e.getPoint()));
				}

				@Override
				public void mouseExited(MouseEvent e) {
					theContainer.mouseExited(e, toShapePoint(e.getPoint()));
				}

				@Override
				public void mousePressed(MouseEvent e) {
					theContainer.mousePressed(e, toShapePoint(e.getPoint()));
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					theContainer.mouseReleased(e, toShapePoint(e.getPoint()));
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					theContainer.mouseClicked(e, toShapePoint(e.getPoint()));
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					theContainer.mouseWheelMoved(e, toShapePoint(e.getPoint()));
				}

				@Override
				public void keyPressed(KeyEvent e) {
					theContainer.keyPressed(e);
				}

				@Override
				public void keyReleased(KeyEvent e) {
					theContainer.keyReleased(e);
				}

				@Override
				public void keyTyped(KeyEvent e) {
					theContainer.keyTyped(e);
				}
			}
			EventListener listener = new EventListener();
			addMouseListener(listener);
			addMouseMotionListener(listener);
			addMouseWheelListener(listener);
		}

		@Override
		public Dimension getPreferredSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getPreferred().get();
			if (size != null)
				w = size.evaluateInt(getParent().getWidth());
			else
				w = 300;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getPreferred().get();
			if (size != null)
				h = size.evaluateInt(getParent().getHeight());
			else
				h = 300;
			return new Dimension(w, h);
		}

		@Override
		public Dimension getMinimumSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getMinimum().get();
			if (size != null)
				w = size.evaluateInt(getParent().getWidth());
			else
				w = 0;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getMinimum().get();
			if (size != null)
				h = size.evaluateInt(getParent().getHeight());
			else
				h = 0;
			return new Dimension(w, h);
		}

		@Override
		public Dimension getMaximumSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getMaximum().get();
			if (size != null)
				w = size.evaluateInt(getParent().getWidth());
			else
				w = Integer.MAX_VALUE;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getMaximum().get();
			if (size != null)
				h = size.evaluateInt(getParent().getHeight());
			else
				h = Integer.MAX_VALUE;
			return new Dimension(w, h);
		}

		void publishSize() {
			Integer preW = theCanvas.getPublishWidth().get();
			Integer preH = theCanvas.getPublishHeight().get();
			if ((preW == null || preW.intValue() != getWidth()) && theCanvas.getPublishWidth().isAcceptable(getWidth()) == null)
				theCanvas.getPublishWidth().set(getWidth());

			if ((preH == null || preH.intValue() != getHeight()) && theCanvas.getPublishHeight().isAcceptable(getHeight()) == null)
				theCanvas.getPublishHeight().set(getHeight());
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			theContainer.draw((Graphics2D) g, new Rectangle2D.Float(0, 0, getWidth(), getHeight()));
		}
	}

	static class InterpretedShapeCollection<T> extends InterpretedShapeContainer
	implements InterpretedQuickShapePublisher<QuickShapeCollection<T>> {
		InterpretedShapeCollection(QuickShapeCollection.Interpreted<T> collection, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(collection, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickShapeCollection<T> element) throws ModelInstantiationException {
			return new QuickShapeCollectionPublisher<>(element, getPublishers(element));
		}
	}

	static class QuickShapeCollectionPublisher<T> extends SimpleShapeContainer {
		private final QuickShapeCollection<T> theCollection;
		private ListElement<T> theFocusElement;
		private ListElement<QuickShapeInterpretation> theFocus;
		private ListElement<T> theCurrentValue;
		private final BetterSet<BiTuple<ElementId, ElementId>> theHovered;
		private boolean isInAction;

		QuickShapeCollectionPublisher(QuickShapeCollection<T> collection, List<QuickDrawShapePublisher> shapes) {
			super(shapes, collection.onDestroy());
			theCollection = collection;
			theHovered = BetterHashSet.build().build();
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Observable<?> update() {
			return Observable.or(super.update(), Observable.onRootFinish(theCollection.getValues().simpleChanges()));
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			isInAction = true;
			try {
				SettableValue<T> activeValue = theCollection.getActiveValue();
				SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
				int i = 0;
				for (ListElement<T> value = theCollection.getValues().getTerminalElement(true); //
					value != null; value = value.getAdjacent(true)) {
					theCurrentValue = value;
					activeValue.set(value.get());
					activeIndex.set(i);
					super.draw(gfx, screen);
					i++;
				}
			} finally {
				isInAction = false;
			}
		}

		@Override
		protected QuickShapeInterpretation mouseHover(MouseEvent e, Point2D.Float point) {
			theHovered.removeIf(el -> !el.getValue1().isPresent() || !el.getValue2().isPresent());
			QuickShapeInterpretation first = null;
			boolean done = false;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					for (CollectionElement<QuickShapeInterpretation> shapeEl = getContents().getTerminalElement(false); shapeEl != null; //
						shapeEl = shapeEl.getAdjacent(false)) {
						Point2D.Float hit = shapeEl.get().hit(point);
						if (done) {
							if (theHovered.remove(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
								shapeEl.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), hit);
							}
						} else if (hit != null) {
							if (theHovered.add(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
								QuickShapeInterpretation target = shapeEl.get().mouseEntered(asType(e, MouseEvent.MOUSE_ENTERED), hit);
								if (first == null)
									first = target;
							} else {
								QuickShapeInterpretation target = shapeEl.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED), hit);
								if (first == null)
									first = target;
							}
							done = shapeEl.get().getOpacity(hit) == Opacity.Full;
						} else if (theHovered.remove(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
							shapeEl.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), null);
						}
					}
					i--;
				}
			} finally {
				isInAction = false;
			}
			return first;
		}

		@Override
		public Opacity mousePressed(MouseEvent e, Point2D.Float point) {
			ListElement<T> focusEl = null;
			Opacity opacity = Opacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != Opacity.Full && valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					Opacity valueOpacity = super.mousePressed(e, point);
					if (valueOpacity != Opacity.None)
						focusEl = valueEl;
					opacity = opacity.or(valueOpacity);
					i--;
				}
			} finally {
				isInAction = false;
			}
			theFocusElement = focusEl;
			return opacity;
		}

		@Override
		public void mouseExited(MouseEvent e, Point2D.Float point) {
			Iterator<BiTuple<ElementId, ElementId>> hovered = theHovered.iterator();
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			while (hovered.hasNext()) {
				BiTuple<ElementId, ElementId> el = hovered.next();
				if (!el.getValue1().isPresent() || !el.getValue2().isPresent())
					continue;
				ListElement<T> valueEl = theCollection.getValues().getElement(el.getValue1());
				theCurrentValue = valueEl;
				activeValue.set(valueEl.get());
				activeIndex.set(valueEl.getElementsBefore());
				ListElement<QuickShapeInterpretation> shape = getContents().getElement(el.getValue2());
				setState(shape);
				Point2D.Float hit = shape.get().hit(point);
				hovered.remove();
				setState(shape);
				shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), hit);
			}
		}

		@Override
		public <E extends MouseEvent> Opacity mouseAction(E e, Point2D.Float point,
			TriConsumer<QuickShapeInterpretation, E, Point2D.Float> action) {
			Opacity opacity = Opacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != Opacity.Full && valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					opacity = opacity.or(super.mouseAction(e, point, action));
					i--;
				}
			} finally {
				isInAction = false;
			}
			return opacity;
		}

		@Override
		protected ListElement<QuickShapeInterpretation> getFocus() {
			if (!isInAction) {
				if (theFocusElement != null && !theFocusElement.getElementId().isPresent()) {
					theFocusElement = null;
					theFocus = null;
				}
				if (theFocusElement != null) {
					theCurrentValue = theFocusElement;
					theCollection.getActiveValue().set(theFocusElement.get());
					theCollection.getActiveValueIndex().set(theFocusElement.getElementsBefore());
				}
			}
			return theFocus;
		}

		@Override
		protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
			theFocus = shape;
		}

		@Override
		protected boolean isFocused(ListElement<QuickShapeInterpretation> shape) {
			return theFocusElement != null && theCurrentValue != null
				&& theFocusElement.getElementId().equals(theCurrentValue.getElementId()) && theFocus != null
				&& theFocus.getElementId().equals(shape.getElementId());
		}

		@Override
		public String getTooltip() {
			theHovered.removeIf(el -> !el.getValue1().isPresent() || !el.getValue2().isPresent());
			isInAction = true;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			try {
				for (BiTuple<ElementId, ElementId> hovered : theHovered.reverse()) {
					ListElement<T> valueEl = theCollection.getValues().getElement(hovered.getValue1());
					theCurrentValue = valueEl;
					QuickShapeInterpretation shape = getContents().getElement(hovered.getValue2()).get();
					activeValue.set(valueEl.get());
					activeIndex.set(valueEl.getElementsBefore());
					String tooltip = shape.getTooltip();
					if (tooltip != null)
						return tooltip;
				}
			} finally {
				isInAction = false;
			}
			return null;
		}

		@Override
		protected void setState(ListElement<QuickShapeInterpretation> shapeEl) {
			if (theCurrentValue != null) {
				boolean hovered = theHovered.contains(new BiTuple<>(theCurrentValue.getElementId(), shapeEl.getElementId()));
				shapeEl.get().setState(hovered, isFocused(shapeEl), hovered && isPressed(), hovered && isRightPressed());
			}
		}
	}

	static class InterpretedLightWeightShapeCollection<T> extends InterpretedShapeContainer
	implements InterpretedQuickShapePublisher<LightWeightShapeCollection<T>> {
		InterpretedLightWeightShapeCollection(LightWeightShapeCollection.Interpreted<T> collection,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			super(collection, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(LightWeightShapeCollection<T> element) throws ModelInstantiationException {
			return new LightWeightShapeCollectionPublisher<>(element, getPublishers(element));
		}
	}

	static class LightWeightShapeCollectionPublisher<T> extends SimpleShapeContainer {
		private final LightWeightShapeCollection<T> theCollection;
		private boolean isMouseListening;

		LightWeightShapeCollectionPublisher(LightWeightShapeCollection<T> collection, List<QuickDrawShapePublisher> shapes) {
			super(shapes, collection.onDestroy());
			theCollection = collection;
			isMouseListening = super.isMouseListening();
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public boolean isMouseListening() {
			return isMouseListening;
		}

		@Override
		public Observable<?> update() {
			return Observable.or(super.update(), Observable.onRootFinish(theCollection.getValueChanges()));
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = 0;
			for (T value : theCollection.getValues()) {
				activeValue.set(value);
				activeIndex.set(i);
				super.draw(gfx, screen);
				i++;
			}
		}

		@Override
		protected QuickShapeInterpretation mouseHover(MouseEvent e, Point2D.Float point) {
			if (!isMouseListening)
				return null;
			QuickShapeInterpretation first = null;
			boolean done = false;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			for (ListIterator<T> iter = theCollection.getValues().listIterator(i + 1); iter.hasPrevious();) {
				activeValue.set(iter.previous());
				activeIndex.set(i);
				for (CollectionElement<QuickShapeInterpretation> shapeEl = getContents().getTerminalElement(false); shapeEl != null; //
					shapeEl = shapeEl.getAdjacent(false)) {
					Point2D.Float hit = shapeEl.get().hit(point);
					if (done) { // We don't have enough info to support enter/exit
					} else if (hit != null) {
						QuickShapeInterpretation target = shapeEl.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED), hit);
						if (first == null)
							first = target;
						done = shapeEl.get().getOpacity(hit) == Opacity.Full;
					}
				}
				i--;
			}
			return first;
		}

		@Override
		public Opacity mousePressed(MouseEvent e, Point2D.Float point) {
			Opacity opacity = Opacity.None;
			if (!isMouseListening)
				return opacity;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			for (ListIterator<T> iter = theCollection.getValues().listIterator(i + 1); opacity != Opacity.Full && iter.hasPrevious();) {
				activeValue.set(iter.previous());
				activeIndex.set(i);
				Opacity valueOpacity = super.mousePressed(e, point);
				opacity = opacity.or(valueOpacity);
				i--;
			}
			return opacity;
		}

		@Override
		public <E extends MouseEvent> Opacity mouseAction(E e, Point2D.Float point,
			TriConsumer<QuickShapeInterpretation, E, Point2D.Float> action) {
			Opacity opacity = Opacity.None;
			if (!isMouseListening)
				return opacity;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			for (ListIterator<T> iter = theCollection.getValues().listIterator(i + 1); opacity != Opacity.Full && iter.hasPrevious();) {
				activeValue.set(iter.previous());
				activeIndex.set(i);
				opacity = opacity.or(super.mouseAction(e, point, action));
				i--;
			}
			return opacity;
		}

		@Override
		protected ListElement<QuickShapeInterpretation> getFocus() {
			return null;
		}

		@Override
		protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
		}
	}

	static abstract class QuickDrawSingleShape<S extends QuickShape> implements QuickDrawShapePublisher, QuickShapeInterpretation {
		static BiConsumer<QuickEventListener, InputEvent> EVENT_NO_CONFIG = (ql, evt) -> {
		};

		static <L extends QuickEventListener, E extends InputEvent> BiConsumer<L, E> noConfig() {
			return (BiConsumer<L, E>) EVENT_NO_CONFIG;
		}

		private final ObservableValue<Color> theColor;
		private final ObservableValue<Float> theOpacity;
		private final S theShape;
		private final QuickWithBackground.BackgroundContext theBgCtx;
		private final Observable<?> theUpdate;

		protected QuickDrawSingleShape(S shape) {
			theShape = shape;
			theBgCtx = new QuickWithBackground.BackgroundContext.Default();
			theShape.setContext(theBgCtx);
			theColor = shape.getStyle().getColor();
			theOpacity = shape.getStyle().getOpacity();
			theUpdate = Observable.or(theColor.noInitChanges(), theOpacity.noInitChanges());
		}

		public S getShape() {
			return theShape;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		public boolean isVisible() {
			return getShape().isVisible().get();
		}

		public Color getColor() {
			Color color = theColor.get();
			if (color == null)
				color = Color.black;
			return color;
		}

		public float getOpacity() {
			Float opacity = theOpacity.get();
			if (opacity == null)
				return 1.0f;
			return opacity;
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public boolean isMouseListening() {
			return theShape.getEventListeners().stream().anyMatch(el -> el instanceof QuickMouseListener);
		}

		@Override
		public Opacity getOpacity(Point2D.Float point) {
			if (!isVisible())
				return Opacity.None;
			Color color = theColor.get();
			Float opacity = theOpacity.get();
			if (color == null) {
				if (opacity == null || opacity.floatValue() >= 1.0f)
					return Opacity.Full;
				else if (opacity.floatValue() <= 0.0f)
					return Opacity.None;
				else
					return Opacity.Partial;
			}
			int alpha;
			if (opacity == null || opacity.floatValue() == 1.0f)
				alpha = color.getAlpha();
			else
				alpha = Math.round(color.getAlpha() * opacity.floatValue());
			if (alpha <= 0)
				return Opacity.None;
			else if (alpha >= 255)
				return Opacity.Full;
			else
				return Opacity.Partial;
		}

		protected <E extends InputEvent, L extends QuickEventListener> void input(Class<L> listenerType, Predicate<? super L> filter, E evt,
			BiConsumer<L, E> install) {
			for (QuickEventListener lstnr : theShape.getEventListeners()) {
				if (!listenerType.isInstance(lstnr))
					continue;

				L listener = (L) lstnr;
				if (filter != null && !filter.test(listener))
					continue;

				SettableValue<Boolean> altPressed = listener.isAltPressed();
				SettableValue<Boolean> ctrlPressed = listener.isCtrlPressed();
				SettableValue<Boolean> shiftPressed = listener.isShiftPressed();
				altPressed.set(evt.isAltDown(), evt);
				ctrlPressed.set(evt.isControlDown(), evt);
				shiftPressed.set(evt.isShiftDown(), evt);
				install.accept(listener, evt);
				if (listener.testFilter() && listener.getAction().isEnabled().get() == null)
					listener.getAction().act(evt);
			}
		}

		protected <L extends QuickMouseListener, E extends MouseEvent> Opacity mouse(Class<L> listenerType, Predicate<L> filter, E evt,
			Point2D.Float point, BiConsumer<L, E> install) {
			Point2D.Float hit = hit(point);
			if (hit == null)
				return Opacity.None;
			input(listenerType, filter, evt, install.andThen((listener, evt2) -> {
				SettableValue<Float> x = (SettableValue<Float>) listener.getEventX();
				SettableValue<Float> y = (SettableValue<Float>) listener.getEventY();
				x.set(point.x, evt2);
				y.set(point.y, evt2);
			}));
			return getOpacity(hit);
		}

		protected Opacity mouseMove(MouseMoveEventType eventType, MouseEvent evt, Point2D.Float point) {
			return mouse(QuickMouseListener.QuickMouseMoveListener.class, mml -> mml.getEventType() == eventType, evt, point, noConfig());
		}

		protected <L extends QuickMouseButtonListener> Opacity mouseButton(Class<L> listenerType, Predicate<? super L> filter,
			MouseEvent evt, Point2D.Float point, BiConsumer<L, MouseEvent> install) {
			return mouse(listenerType, lstnr -> {
				MouseButton button = QuickCoreSwing.checkMouseEventType(evt, lstnr.getButton());
				if (button == null)
					return false;
				lstnr.getEventButton().set(button, evt);
				if (filter != null && !filter.test(lstnr))
					return false;
				return true;
			}, evt, point, install);
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point) {
			if (mouseMove(MouseMoveEventType.Enter, e, point) != Opacity.None)
				return this;
			else
				return null;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point) {
			if (mouseMove(MouseMoveEventType.Move, e, point) != Opacity.None)
				return this;
			else
				return null;
		}

		@Override
		public void mouseDragged(MouseEvent e, Point2D.Float point) {
		}

		@Override
		public void mouseExited(MouseEvent e, Point2D.Float point) {
			mouseMove(MouseMoveEventType.Exit, e, point);
		}

		@Override
		public Opacity mousePressed(MouseEvent e, Point2D.Float point) {
			return mouseButton(QuickMouseListener.QuickMousePressedListener.class, null, e, point, noConfig());
		}

		@Override
		public Opacity mouseReleased(MouseEvent e, Point2D.Float point) {
			return mouseButton(QuickMouseListener.QuickMouseReleasedListener.class, null, e, point, noConfig());
		}

		@Override
		public Opacity mouseClicked(MouseEvent e, Point2D.Float point) {
			return mouseButton(QuickMouseListener.QuickMouseClickListener.class, lstnr -> {
				if (lstnr.getClickCount() > 0 && e.getClickCount() != lstnr.getClickCount())
					return false;
				return true;
			}, e, point, noConfig());
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			return mouse(QuickMouseListener.QuickScrollListener.class, null, e, point, (lstnr, evt) -> {
				lstnr.getScrollAmount().set(evt.getScrollAmount());
			});
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			input(QuickKeyListener.QuickKeyCodeListener.class, lstnr -> {
				if (!lstnr.isPressed())
					return false;
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (lstnr.getKeyCode() != null && lstnr.getKeyCode() != code)
					return false;
				lstnr.getEventKeyCode().set(code, e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			input(QuickKeyListener.QuickKeyCodeListener.class, lstnr -> {
				if (lstnr.isPressed())
					return false;
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (lstnr.getKeyCode() != null && lstnr.getKeyCode() != code)
					return false;
				lstnr.getEventKeyCode().set(code, e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			input(QuickKeyListener.QuickKeyTypedListener.class, lstnr -> {
				if (lstnr.getCharFilter() > 0 && lstnr.getCharFilter() != e.getKeyChar())
					return false;
				lstnr.getTypedChar().set(e.getKeyChar(), e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public String getTooltip() {
			return theShape.getTooltip().get();
		}

		@Override
		public void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed) {
			// System.out.println(theShape + ":" + (hovered ? " hovered" : "") + (focused ? " focused" : "") + (pressed ? " pressed" : "")
			// + (rightPressed ? "right-pressed" : ""));
			theBgCtx.isHovered().set(hovered);
			theBgCtx.isFocused().set(focused);
			theBgCtx.isPressed().set(pressed);
			theBgCtx.isRightPressed().set(rightPressed);
		}
	}

	static abstract class QuickDrawBorderedShape<S extends QuickBorderedShape> extends QuickDrawSingleShape<S> {
		private final ObservableValue<Integer> theBorderThickness;
		private final ObservableValue<Color> theBorderColor;
		private final ObservableValue<StrokeDashing> theStrokeDash;
		private final Observable<?> theUpdate;

		protected QuickDrawBorderedShape(S shape) {
			super(shape);
			QuickBorderedShape.QuickBorderedShapeStyle style = shape.getStyle();
			theBorderThickness = style.getBorderThickness();
			theBorderColor = style.getBorderColor();
			theStrokeDash = style.getStrokeDash();
			theUpdate = Observable.or(super.update(), theBorderThickness.noInitChanges(), theBorderColor.noInitChanges(),
				theStrokeDash.noInitChanges(), shape.getRepaint());
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		public int getBorderThickness() {
			Integer thick = theBorderThickness.get();
			return thick == null ? 0 : thick;
		}

		public Color getBorderColor() {
			Color color = theBorderColor.get();
			if (color == null)
				color = Color.black;
			return color;
		}

		public StrokeDashing getStrokeDash() {
			StrokeDashing dash = theStrokeDash.get();
			return dash == null ? StrokeDashing.full : dash;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}
	}

	/** Handles shapes with defined bounds and rotation */
	protected static class SimpleShapeHandling {
		private final QuickShape theShape;
		private final Rectangle2D.Float theFBounds;
		private final Rectangle theIBounds;
		private AffineTransform theTransform;
		private AffineTransform theTransformInverse;

		SimpleShapeHandling(QuickShape shape) {
			theShape = shape;
			theFBounds = new Rectangle2D.Float();
			theIBounds = new Rectangle();
		}

		Rectangle getBounds() {
			return theIBounds;
		}

		Rectangle2D.Float getFBounds() {
			return theFBounds;
		}

		/** @return The shape's rotation transform */
		public AffineTransform getTransform() {
			return theTransform;
		}

		/** @return The shape's inverse rotation transform */
		public AffineTransform getTransformInverse() {
			return theTransformInverse;
		}

		/**
		 * @param width The width of the shape
		 * @param height The height of the shape
		 * @param rotation The rotation of the shape
		 * @param screen The screen bounds
		 */
		public boolean updateBounds(QuickSize width, QuickSize height, double rotation, Rectangle2D.Float screen) {
			Positionable hPos = theShape.getAddOn(Positionable.Horizontal.class);
			Positionable vPos = theShape.getAddOn(Positionable.Vertical.class);

			// if (rotateFirst)
			// System.out.println(width + "x" + height + " " + hPos.getLeading().get() + ", " + vPos.getCenter().get());
			Point2D.Float anchor = new Point2D.Float();
			// For post-positioning rotation, the positioning determines the center of the rotation via the anchor.
			// E.g. If the user specifies the center of the shape, the center should stay where it is regardless of rotation.
			// Similarly if the specify the upper left corner, we should rotate around that.
			boolean debugging = theShape.getDebugPrint().get() != null;
			if (!evaluateH(hPos, width, anchor, screen, debugging)) {
				if (!debugging) {
					theTransform = theTransformInverse = null;
					return false;
				}
			}
			if (!evaluateV(vPos, height, anchor, screen, debugging)) {
				if (!debugging) {
					theTransform = theTransformInverse = null;
					return false;
				}
			}

			float scaleX = Scaling.getNeededScale(theFBounds.x, theFBounds.width);
			float scaleY = Scaling.getNeededScale(theFBounds.y, theFBounds.height);
			boolean scale = scaleX != 1.0f || scaleY != 1.0f;
			boolean rotate = rotation != 0.0;

			if (scale || rotate) { // Need transformation
				if (theTransform == null)
					theTransform = new AffineTransform();
				else
					theTransform.setToIdentity();

				theTransform.translate(theFBounds.x, theFBounds.y);
				theTransform.scale(scaleX, scaleY);
				if (rotate)
					theTransform.rotate(rotation, anchor.x - theFBounds.x, anchor.y - theFBounds.y);

				try {
					theTransformInverse = theTransform.createInverse();
				} catch (NoninvertibleTransformException e) {
					theShape.reporting().error(e.getMessage(), e);
				}

				theIBounds.x = theIBounds.y = 0;
				theIBounds.width = (int) (theFBounds.width / scaleX);
				theIBounds.height = (int) (theFBounds.height / scaleY);
			} else {
				theTransform = theTransformInverse = null;

				theIBounds.x = (int) theFBounds.x;
				theIBounds.y = (int) theFBounds.y;
				theIBounds.width = (int) theFBounds.width;
				theIBounds.height = (int) theFBounds.height;
			}

			return true;
		}

		private boolean evaluateH(Positionable hPos, QuickSize width, Point2D.Float anchor, Rectangle2D.Float screen, boolean debugging) {
			return evaluateDimension(x -> theFBounds.x = x, w -> theFBounds.width = w, a -> anchor.x += a, hPos, width, screen.x,
				screen.width, "width", debugging);
		}

		private boolean evaluateV(Positionable vPos, QuickSize height, Point2D.Float anchor, Rectangle2D.Float screen, boolean debugging) {
			return evaluateDimension(y -> theFBounds.y = y, h -> theFBounds.height = h, a -> anchor.y += a, vPos, height, screen.y,
				screen.height, "height", debugging);
		}

		private boolean evaluateDimension(FloatConsumer setPosition, FloatConsumer setSize, FloatConsumer setAnchor, Positionable positions,
			QuickSize specSize, float screenOffset, float screenSize, String sizeName, boolean debugging) {
			QuickSize leading = positions.getLeading().get();
			QuickSize center = positions.getCenter().get();
			QuickSize trailing = positions.getTrailing().get();
			float pos, size, anchor;
			// There are many ways of specifying the positioning and height of the shape here.
			// The user will get a warning if any of the attributes conflict,
			// but we still have to decide which attributes will take priority in that case.
			// We'll respect size first, then leading, then trailing, and center last.
			if (specSize != null) {
				size = specSize.evaluateFloat(screenSize);
				if (leading != null)
					anchor = pos = leading.evaluateFloat(screenSize);
				else if (trailing != null) {
					anchor = trailing.evaluateFloat(screenSize);
					pos = anchor - size;
				} else if (center != null) {
					anchor = center.evaluateFloat(screenSize);
					pos = anchor - size / 2;
				} else
					anchor = pos = 0;
			} else if (leading != null) {
				anchor = pos = leading.evaluateFloat(screenSize);
				if (trailing != null) {
					anchor = trailing.evaluateFloat(screenSize);
					size = anchor - pos;
				} else if (center != null) {
					anchor = center.evaluateFloat(screenSize);
					size = (anchor - pos) * 2;
				} else {
					theShape.reporting()
					.warn(StringUtils.capitalize(sizeName) + " is missing or null and cannot be deduced from positioning");
					return false;
				}
			} else if (center != null && trailing != null) { // Pretty weird, but we can deduce
				float centr = center.evaluateFloat(screenSize);
				float trail = trailing.evaluateFloat(screenSize);
				anchor = trail;
				pos = centr - (trail - centr);
				size = (trail - centr) * 2;
			} else {
				theShape.reporting().warn(StringUtils.capitalize(sizeName) + " is missing or null and cannot be deduced from positioning");
				return false;
			}
			boolean draw;
			if (size == 0)
				draw = false;
			else if (size < 0) {
				theShape.reporting().warn(StringUtils.capitalize(sizeName) + " evaluates negatively: " + size);
				draw = false;
			} else if (pos > screenOffset + screenSize || pos + size <= screenOffset)
				draw = false;
			else
				draw = true;

			if (draw || debugging) {
				setPosition.accept(pos);
				setSize.accept(size);
				setAnchor.accept(anchor);
			}
			return draw;
		}
	}

	static class Scaling {
		private static final float HI_SCALE_THRESH = 1E8f;
		private static final float NEG_HI_SCALE_THRESH = -HI_SCALE_THRESH;
		private static final float LO_SCALE_THRESH = 100.0f;
		private static float[] HIGH_SCALE_VALUES;
		private static float[] LOW_SCALE_VALUES;

		static float getNeededScale(float min, float size) {
			if (size == 0.0f)
				return 1.0f;
			else if (min < NEG_HI_SCALE_THRESH)
				return getHighScaleFor(-min);
			else if (min > HI_SCALE_THRESH || size > HI_SCALE_THRESH)
				return getHighScaleFor(min + size);
			else if (size < LO_SCALE_THRESH && (size != (int) size || (min < LO_SCALE_THRESH && min != (int) min)))
				return getLowScaleFor(size);
			else
				return 1.0f;
		}

		static {
			FloatList scales = new FloatList();
			float max = Float.MAX_VALUE / 1E3f;
			for (float scale = HI_SCALE_THRESH / 1E3f; scale < max; scale *= 1E3f)
				scales.add(scale);
			HIGH_SCALE_VALUES = scales.toArray();
			LOW_SCALE_VALUES = new float[HIGH_SCALE_VALUES.length];
			for (int i = 0; i < LOW_SCALE_VALUES.length; i++)
				LOW_SCALE_VALUES[i] = 1.0f / HIGH_SCALE_VALUES[HIGH_SCALE_VALUES.length - i - 1];
		}

		private static float getHighScaleFor(float value) {
			int index = Arrays.binarySearch(HIGH_SCALE_VALUES, value);
			if (index < 0)
				index = -index - 1;
			if (index > 1)
				index -= 2;
			else if (index > 0)
				index--;
			return HIGH_SCALE_VALUES[index];
		}

		private static float getLowScaleFor(float value) {
			int index = Arrays.binarySearch(LOW_SCALE_VALUES, value);
			if (index < 0)
				index = -index - 1;
			if (index > 0)
				index--;
			return LOW_SCALE_VALUES[index];
		}
	}

	static abstract class QuickDrawSimpleShape<S extends QuickSimpleShape> extends QuickDrawBorderedShape<S> {
		private final Observable<?> theUpdate;
		private Rectangle2D.Float theScreen;
		private final SimpleShapeHandling theBounds;
		private SettableValue<Double> theRotationValue;

		protected QuickDrawSimpleShape(S shape) {
			super(shape);
			theBounds = new SimpleShapeHandling(shape);
			theRotationValue = shape.getAddOn(QuickRotated.class).getRotation();
			Positionable h = shape.getAddOn(Positionable.Horizontal.class);
			Positionable v = shape.getAddOn(Positionable.Vertical.class);
			theUpdate = Observable.or(super.update(), //
				h.getLeading().noInitChanges(), h.getCenter().noInitChanges(), h.getTrailing().noInitChanges(), //
				v.getLeading().noInitChanges(), v.getCenter().noInitChanges(), v.getTrailing().noInitChanges(), //
				theRotationValue.noInitChanges(), shape.getWidth().noInitChanges(), shape.getHeight().noInitChanges());
		}

		public double getRotation() {
			Double rotation = theRotationValue.get();
			return rotation == null ? 0.0f : rotation.floatValue();
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		protected Rectangle getBounds() {
			return theBounds.getBounds();
		}

		protected SimpleShapeHandling getShapeHandling() {
			return theBounds;
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			if (!isVisible() || theScreen == null)
				return null;

			if (!theBounds.updateBounds(getShape().getWidth().get(), getShape().getHeight().get(), theRotationValue.get(), theScreen))
				return null;
			if (theBounds.getTransformInverse() != null) {
				Point2D.Float transformed = (Point2D.Float) theBounds.getTransformInverse()
					.transform(new Point2D.Float(containerPoint.x, containerPoint.y), new Point2D.Float(0, 0));
				containerPoint = transformed;
			}
			if (!theBounds.getBounds().contains(containerPoint))
				return null;
			return getHit(containerPoint);
		}

		protected abstract Point2D.Float getHit(Point2D.Float point);

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			if (!isVisible())
				return;

			theScreen = screen;
			if (this instanceof QuickDrawEllipse)
				QommonsUtils.doNothing();
			boolean draw = theBounds.updateBounds(//
				getShape().getWidth().get(), getShape().getHeight().get(), theRotationValue.get(), screen);
			String debugPrint = getShape().getDebugPrint().get();
			if (!draw && debugPrint == null)
				return;
			if (theBounds.getTransform() != null) {
				gfx.transform(theBounds.getTransform());
			}
			try {
				if (debugPrint != null) {
					AffineTransform txform = gfx.getTransform();
					StringBuilder msg = new StringBuilder();
					Rectangle b = theBounds.getBounds();
					Rectangle2D.Float fb = theBounds.getFBounds();
					msg.append(debugPrint).append(": Drawing at [")//
					.append(fb.getMinX()).append(", ").append(fb.getMinY()).append("] to [")//
					.append(fb.getMaxX()).append(", ").append(fb.getMaxY()).append(']');
					if (txform != null && !txform.isIdentity()) {
						Point2D pt = new Point2D.Double();
						pt.setLocation(b.getMinX(), b.getMinY());
						txform.transform(pt, pt);
						msg.append("\n\tTransformed to [").append(pt.getX()).append(", ").append(pt.getY()).append("] to [");
						pt.setLocation(b.getMaxX(), b.getMaxY());
						txform.transform(pt, pt);
						msg.append(pt.getX()).append(", ").append(pt.getY()).append(']');
					}
					getShape().reporting().info(msg.toString());
				}
				if (draw) {
					Rectangle intBounds = theBounds.getBounds();
					doDraw(gfx, intBounds);
					if (hasInnerContents()) {
						Graphics2D contentGfx = (Graphics2D) gfx.create(intBounds.x, intBounds.y, intBounds.width, intBounds.height);
						try {
							if (theBounds.getTransformInverse() != null) {
								contentGfx.transform(theBounds.getTransformInverse());
							}
							drawInnerContents(contentGfx, theBounds.getFBounds());
						} finally {
							contentGfx.dispose();
						}
					}
				}
			} finally {
				if (theBounds.getTransformInverse() != null) {
					gfx.transform(theBounds.getTransformInverse());
				}
			}
		}

		protected abstract void doDraw(Graphics2D gfx, Rectangle bounds);

		protected boolean hasInnerContents() {
			return false;
		}

		protected void drawInnerContents(Graphics2D gfx, Rectangle2D.Float bounds) {
		}
	}

	static class InterpretedRectangle extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickRectangle> {
		InterpretedRectangle(QuickRectangle.Interpreted<?> rectangle, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(rectangle, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickRectangle element) throws ModelInstantiationException {
			return new QuickDrawRectangle<>(element, getPublishers(element));
		}
	}

	static class QuickDrawRectangle<S extends QuickRectangle> extends QuickDrawSimpleShape<S> {
		private final SimpleShapeContainer theContainer;
		private final Observable<?> theUpdate;
		private ListElement<QuickShapeInterpretation> theSubFocus;

		QuickDrawRectangle(S rectangle, List<QuickDrawShapePublisher> contents) {
			super(rectangle);
			theContainer = new SimpleShapeContainer(contents, rectangle.onDestroy()) {
				@Override
				protected ListElement<QuickShapeInterpretation> getFocus() {
					return theSubFocus;
				}

				@Override
				protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
					theSubFocus = shape;
				}
			};
			theUpdate = Observable.or(super.update(), theContainer.update());
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		protected void doDraw(Graphics2D gfx, Rectangle bounds) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			// System.out
			// .println("Rect: " + screen.x + "," + screen.y + " " + screen.width + "x" + screen.height + ": " + Colors.toString(bg));
			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}

		}

		@Override
		protected boolean hasInnerContents() {
			return !theContainer.getShapes().isEmpty();
		}

		@Override
		protected void drawInnerContents(Graphics2D gfx, Rectangle2D.Float bounds) {
			theContainer.draw(gfx, bounds);
		}

		@Override
		protected Point2D.Float getHit(Point2D.Float point) {
			return point;
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return null;
			QuickShapeInterpretation hit = theContainer.mouseEntered(e, point);
			if (hit == null)
				hit = super.mouseEntered(e, point);
			return hit;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return null;
			QuickShapeInterpretation hit = theContainer.mouseMoved(e, point);
			if (hit == null)
				hit = super.mouseMoved(e, point);
			return hit;
		}

		@Override
		public void mouseDragged(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return;
			theContainer.mouseDragged(e, point);
			super.mouseDragged(e, point);
		}

		@Override
		public void mouseExited(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return;
			theContainer.mouseExited(e, point);
			super.mouseExited(e, point);
		}

		@Override
		public Opacity mousePressed(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mousePressed(e, point);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mousePressed(e, point));
			return opacity;
		}

		@Override
		public Opacity mouseReleased(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseReleased(e, point);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseReleased(e, point));
			return opacity;
		}

		@Override
		public Opacity mouseClicked(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseClicked(e, point);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseClicked(e, point));
			return opacity;
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseWheelMoved(e, point);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseWheelMoved(e, point));
			return opacity;
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyPressed(e))
				return true;
			return super.keyPressed(e);
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyReleased(e))
				return true;
			return super.keyReleased(e);
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyTyped(e))
				return true;
			return super.keyTyped(e);
		}

		@Override
		public String getTooltip() {
			String tooltip = theContainer.getTooltip();
			if (tooltip == null)
				tooltip = super.getTooltip();
			return tooltip;
		}
	}

	static class InterpretedEllipse implements InterpretedQuickShapePublisher<QuickEllipse> {
		InterpretedEllipse(QuickEllipse.Interpreted<?> ellipse, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickEllipse element) throws ModelInstantiationException {
			return new QuickDrawEllipse(element);
		}
	}

	static class QuickDrawEllipse extends QuickDrawSimpleShape<QuickEllipse> {
		public QuickDrawEllipse(QuickEllipse shape) {
			super(shape);
		}

		@Override
		protected Point2D.Float getHit(Point2D.Float point) {
			Rectangle bounds = getBounds();
			float hRad = bounds.width * 0.5f;
			float vRad = bounds.height * 0.5f;
			float xd = point.x - bounds.x - hRad;
			float yd = point.y - bounds.y - vRad;
			if (((xd * xd) / (hRad * hRad)) + ((yd * yd) / (vRad * vRad)) <= 1)
				return point;
			return null;
		}

		@Override
		protected void doDraw(Graphics2D gfx, Rectangle bounds) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillOval(bounds.x, bounds.y, bounds.width, bounds.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawOval(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
	}

	static class InterpretedPolygon<V> implements InterpretedQuickShapePublisher<QuickPolygon<V>> {
		InterpretedPolygon(QuickPolygon.Interpreted<V> polygon, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickPolygon<V> element) throws ModelInstantiationException {
			return new QuickDrawPolygon<>(element);
		}
	}

	interface VertexedShape extends QuickShapeInterpretation {
		float[][] getVertices();

		@Override
		default void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			float[][] points = getVertices();
			if (points == null)
				return;

			// If the point magnitudes are too large or too small, we may need to scale in order to render them correctly
			float[][] bounds = new float[][] { //
				{ points[0][0], points[0][0] }, { points[0][1], points[0][1] }//
			};

			for (int p = 1; p < points.length; p++) {
				float x = points[p][0];
				if (x < bounds[0][0])
					bounds[0][0] = x;
				else if (x > bounds[0][1])
					bounds[0][1] = x;
				float y = points[p][1];
				if (y < bounds[1][0])
					bounds[1][0] = y;
				else if (y > bounds[1][1])
					bounds[1][1] = y;
			}
			float xScale = Scaling.getNeededScale(bounds[0][0], bounds[0][1] - bounds[0][0]);
			float yScale = Scaling.getNeededScale(bounds[1][0], bounds[1][1] - bounds[1][0]);
			boolean scaled = xScale != 1.0f || yScale != 1.0f;

			int[][] gfxPoints = new int[2][points.length];
			if (scaled) {
				for (int p = 0; p < points.length; p++) {
					gfxPoints[0][p] = (int) (points[0][p] / xScale);
					gfxPoints[1][p] = (int) (points[1][p] / yScale);
				}
				gfx.scale(xScale, yScale);
			} else {
				for (int p = 0; p < points.length; p++) {
					gfxPoints[0][p] = (int) (points[0][p]);
					gfxPoints[1][p] = (int) (points[1][p]);
				}
			}

			try {
				draw(gfx, gfxPoints);
			} finally {
				gfx.scale(1.0f / xScale, 1.0f / yScale);
			}
		}

		void draw(Graphics2D gfx, int[][] points);
	}

	static class QuickDrawPolygon<V> extends QuickDrawBorderedShape<QuickPolygon<V>> implements VertexedShape {
		public QuickDrawPolygon(QuickPolygon<V> shape) {
			super(shape);
		}

		@Override
		public float[][] getVertices() {
			QuickPolygon<V> poly = getShape();
			if (poly.getVertices().size() < 3)
				return null;
			List<V> vertices = QommonsUtils.unmodifiableCopy(poly.getVertices());
			float[][] points = new float[2][vertices.size()];
			for (int v = 0; v < vertices.size(); v++) {
				poly.getActiveVertex().set(vertices.get(v));
				points[0][v] = poly.getVertexX().get();
				points[1][v] = poly.getVertexY().get();
			}
			return points;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			if (isVisible())
				VertexedShape.super.draw(gfx, screen);
		}

		@Override
		public void draw(Graphics2D gfx, int[][] points) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillPolygon(points[0], points[1], points[0].length);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawPolygon(points[0], points[1], points[0].length);
			}
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			float[][] points = getVertices();
			if (points[0].length < 3)
				return null;

			int hits = 0;
			for (int i = 0; i < points[0].length; i++) {
				if (lineSegHit(points, i, containerPoint))
					hits++;
			}
			return hits % 2 == 1 ? containerPoint : null;
		}

		private boolean lineSegHit(float[][] points, int i, Point2D.Float containerPoint) {
			int next = i + 1;
			if (next == points[0].length)
				next = 0;
			if (isBetween(points[1][i], points[1][next], containerPoint.y)) { // cp.y is between the 2 vertex y values
				float dx = points[0][next] - points[0][i];
				float pXdy = (containerPoint.y - points[1][i]);
				float dy = points[1][next] - points[1][i];
				float intersectX = points[0][i] + dx * pXdy / dy;
				return isBetween(points[0][i], points[0][next], intersectX) && intersectX <= containerPoint.x;
			}
			return false;
		}

		private boolean isBetween(float v0, float v1, float p) {
			if (p >= v0)
				return p < v1;
			else
				return p >= v1;
		}
	}

	static class InterpretedText implements InterpretedQuickShapePublisher<QuickDrawText> {
		private final Map<Object, InterpretedText> theSubTexts;

		InterpretedText(QuickDrawText.Interpreted text, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theSubTexts = new HashMap<>();
			for (QuickDrawText.Interpreted subText : text.getSubTexts()) {
				theSubTexts.put(subText.getIdentity(), tx.transform(subText, InterpretedText.class));
			}
		}

		@Override
		public QuickDrawSwingText interpret(QuickDrawText element) throws ModelInstantiationException {
			return new QuickDrawSwingText(element,
				QommonsUtils.filterMapE(element.getSubTexts(), null, st -> theSubTexts.get(st.getIdentity()).interpret(st)));
		}
	}

	static class QuickDrawSwingText extends QuickDrawSingleShape<QuickDrawText> {
		private final List<QuickDrawSwingText> theSubTexts;
		private final Observable<?> theUpdate;
		private FontAdjuster theFontAdjuster;
		private Font theParentFont;
		private Font theFont;
		private String theText;
		private final SimpleShapeHandling theBounds;
		private SettableValue<Double> theRotationValue;

		private FontRenderContext theRenderContext;
		private Rectangle2D theTextBounds;

		QuickDrawSwingText(QuickDrawText shape, List<QuickDrawSwingText> subTexts) {
			super(shape);
			theSubTexts = subTexts;
			theUpdate = Observable.or(//
				Observable.onRootFinish(Observable.or(shape.getValue().noInitChanges(), shape.getStyle().changes())), //
				Observable.or(new MappedList<>(subTexts, QuickDrawSwingText::update)));
			theFontAdjuster = new FontAdjuster();
			theRotationValue = shape.getAddOnValue(QuickRotated.class, QuickRotated::getRotation);
			theBounds = new SimpleShapeHandling(shape);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			if (!isVisible())
				return;
			theParentFont = gfx.getFont();
			Rectangle bounds = updateBounds(gfx.getFontRenderContext());
			if (bounds == null)
				return;
			double rotation = theRotationValue == null ? 0.0 : theRotationValue.get();
			if (!theBounds.updateBounds(QuickSize.ofPixels(bounds.width), QuickSize.ofPixels(bounds.height), rotation, screen))
				return;
			// System.out.println(theText + " bounds=" + bounds + " screen=" + screen + " rot=" + (rotation / Math.PI * 180) + " tx="
			// + theBounds.getBounds());
			theBounds.getBounds().y += bounds.height;
			if (theBounds.getTransform() != null)
				gfx.transform(theBounds.getTransform());
			try {
				drawText(gfx, theBounds.getBounds().x, theBounds.getBounds().y);
			} finally {
				if (theBounds.getTransformInverse() != null)
					gfx.transform(theBounds.getTransformInverse());
			}
		}

		protected void drawText(Graphics2D gfx, int x, int y) {
			if (theText != null && !theText.isEmpty()) {
				Color preColor = gfx.getColor();
				try {
					gfx.setFont(theFont);
					Color color = getShape().getStyle().getFontColor().get();
					if (color == null)
						color = Color.black;
					gfx.setColor(color);
					gfx.drawString(theText, x, y);
					x += theTextBounds.getWidth();
				} finally {
					gfx.setFont(theParentFont);
					gfx.setColor(preColor);
				}
			}
			for (QuickDrawSwingText subText : theSubTexts)
				subText.drawText(gfx, x, y);
		}

		protected Rectangle updateBounds(FontRenderContext renderCtx) {
			if (renderCtx == null) {
				if (theText != null)
					System.err.println(Integer.toHexString(hashCode()) + " (" + theText + "): Null render context");
				return null;
			}
			theRenderContext = renderCtx;
			theText = getShape().getValue().get();
			if (theText != null && !theText.isEmpty()) {
				QuickCoreSwing.adjustFont(theFontAdjuster, getShape().getStyle());
				theFont = theFontAdjuster.adjust(theParentFont);
				theTextBounds = theFont.getStringBounds(theText, theRenderContext);
			} else {
				theFont = null;
				theTextBounds = null;
			}

			if (theTextBounds == null && theSubTexts.isEmpty())
				return null;
			Rectangle bounds = new Rectangle();
			if (theTextBounds != null) {
				bounds.x = (int) Math.round(theTextBounds.getX());
				bounds.y = (int) Math.round(theTextBounds.getY());
				bounds.width = (int) Math.round(theTextBounds.getWidth());
				bounds.height = (int) Math.round(theTextBounds.getHeight());
			}
			for (QuickDrawSwingText subText : theSubTexts) {
				Rectangle subBounds = subText.updateBounds(renderCtx);
				if (subBounds != null) {
					if (bounds.width > 0)
						subBounds.setLocation(subBounds.x + bounds.width, subBounds.y);
					bounds.add(subBounds);
				}
			}
			return bounds;
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			if (!isVisible())
				return null;
			Rectangle bounds = updateBounds(theRenderContext);
			if (bounds == null)
				return null;
			Point2D.Float txPoint = transform(containerPoint, theBounds.getTransformInverse());
			return bounds.contains(txPoint) ? containerPoint : null;
		}

		@Override
		public Opacity getOpacity(Point2D.Float point) {
			return Opacity.Partial;
		}
	}

	static abstract class QuickDrawLinearShape<S extends QuickLinearShape> extends QuickDrawSingleShape<S> {
		private final ObservableValue<Double> theThickness;
		private final ObservableValue<StrokeDashing> theStrokeDash;
		private final Observable<?> theUpdate;

		protected QuickDrawLinearShape(S shape) {
			super(shape);
			theThickness = shape.getStyle().getThickness();
			theStrokeDash = shape.getStyle().getStrokeDash();
			theUpdate = Observable.or(super.update(), theThickness.noInitChanges(), theStrokeDash.noInitChanges(), shape.getRepaint());
		}

		public double getThickness() {
			Double thick = theThickness.get();
			return thick == null ? 1 : thick;
		}

		public StrokeDashing getStrokeDash() {
			StrokeDashing dash = theStrokeDash.get();
			return dash == null ? StrokeDashing.full : dash;
		}

		static int toInt(double value) {
			return (int) Math.round(value);
		}

		static class HitData {
			/** A number between 0 and 1, where zero represents a hit at p0 and 1 represents a hit at p1 */
			final double p;
			/** The distance of the hit away from the line */
			final double pDist;
			/** The distance between the two points on the line */
			final double d;
			/** The distance between the first point on the line and the point on the line closest to the hit */
			final double pd;
			/** The x coordinate of the point on the line closest to the hit */
			final double x;
			/** The y coordinate of the point on the line closest to the hit */
			final double y;

			public HitData(double p, double pDist, double d, double pd, double x, double y) {
				this.p = p;
				this.pDist = pDist;
				this.d = d;
				this.pd = pd;
				this.x = x;
				this.y = y;
			}
		}

		HitData getHit(double x0, double y0, double x1, double y1, float x, float y, double dLimit) {
			double dx = x1 - x0;
			double dy = y1 - y0;
			double dx0 = x - x0;
			double dy0 = y - y0;
			double dx1 = x - x1;
			double dy1 = y - y1;

			double d = Math.sqrt(dx * dx + dy * dy);
			double d02 = dx0 * dx0 + dy0 * dy0;
			double d0 = Math.sqrt(d02);
			double d1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);

			double theta = Math.atan2(d1, d);
			double pd = d0 * Math.cos(theta);
			double pDist2 = d02 - pd * pd;
			if (pDist2 > dLimit * dLimit)
				return null;
			double p = pd / d;
			double px = x0 + p * dx;
			double py = y0 + p * dy;
			return new HitData(p, Math.sqrt(pDist2), d, pd, px, py);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}
	}

	static class InterpretedLine implements InterpretedQuickShapePublisher<QuickLine> {
		InterpretedLine(QuickLine.Interpreted line, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickLine element) throws ModelInstantiationException {
			return new QuickDrawLine(element);
		}
	}

	static class QuickDrawLine extends QuickDrawLinearShape<QuickLine> implements VertexedShape {
		private final Observable<?> theUpdate;

		QuickDrawLine(QuickLine shape) {
			super(shape);
			theUpdate = Observable.onRootFinish(Observable.or(shape.getStyle().changes(), //
				Observable.or(shape.getPoints().stream().flatMap(pt -> Stream.of(pt.getX().noInitChanges(), pt.getY().noInitChanges()))
					.collect(Collectors.toList()))));
		}

		@Override
		public float[][] getVertices() {
			List<QuickPoint> points = getShape().getPoints();
			float[][] vertices = new float[2][points.size()];
			int v = 0;
			for (QuickPoint point : points) {
				vertices[0][v] = point.getX().get().floatValue();
				vertices[1][v] = point.getY().get().floatValue();
				v++;
			}
			return vertices;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			if (isVisible())
				VertexedShape.super.draw(gfx, screen);
		}

		@Override
		public void draw(Graphics2D gfx, int[][] points) {
			Color color = getColor();
			float opacity = getOpacity();
			double thickness = getThickness();
			if (thickness <= 0 || opacity <= 0 || color.getAlpha() == 0)
				return;
			StrokeDashing dash = getStrokeDash();

			if (opacity < 1 && color.getAlpha() > 0)
				color = Colors.transluce(color, opacity);
			if (color.getAlpha() == 0)
				return;
			gfx.setColor(color);
			dash.apply(gfx, (float) thickness, false);

			boolean first = true;
			int prevX = 0, prevY = 0;
			// System.out.print("Drawing " + Colors.toString(color));
			for (int p = 0; p < points[0].length; p++) {
				int x = points[0][p];
				int y = points[1][p];
				// System.out.print(" (" + x + ", " + y + ")");
				if (first)
					first = false;
				else
					gfx.drawLine(prevX, prevY, x, y);

				prevX = x;
				prevY = y;
			}
			// System.out.println();
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			if (!isVisible())
				return null;
			double thickness = getThickness();
			if (thickness <= 0)
				return null;

			QuickPoint prev = null;
			for (QuickPoint point : getShape().getPoints()) {
				if (prev == null) {
					prev = point;
					continue;
				}

				if (getHit(prev.getX().get(), prev.getY().get(), //
					point.getX().get(), point.getY().get(), //
					containerPoint.x, containerPoint.y, thickness) != null)
					return containerPoint;

				prev = point;
			}
			return null;
		}
	}

	static class InterpretedFlexLine<T> implements InterpretedQuickShapePublisher<QuickFlexLine<T>> {
		InterpretedFlexLine(QuickFlexLine.Interpreted<T> line, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickFlexLine<T> element) throws ModelInstantiationException {
			return new QuickDrawFlexLine<>(element);
		}
	}

	static class QuickDrawFlexLine<T> extends QuickDrawLinearShape<QuickFlexLine<T>> implements VertexedShape {
		private final Observable<?> theUpdate;

		public QuickDrawFlexLine(QuickFlexLine<T> shape) {
			super(shape);
			theUpdate = Observable.onRootFinish(Observable.or(shape.getStyle().changes(), shape.getPoints().simpleChanges()));
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public float[][] getVertices() {
			QuickFlexLine<T> line = getShape();
			List<T> points = line.getPoints();
			float[][] vertices = new float[2][points.size()];
			int v = 0;
			for (T point : points) {
				line.getActivePointAs().set(point);
				line.getPointIndexAs().set(v);
				vertices[0][v] = line.getPointX().get().floatValue();
				vertices[1][v] = line.getPointY().get().floatValue();
				v++;
			}
			return vertices;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			if (isVisible())
				VertexedShape.super.draw(gfx, screen);
		}

		@Override
		public void draw(Graphics2D gfx, int[][] points) {
			// Don't query the style info now, as it may vary by point, or even along each segment

			QuickFlexLine<T> line = getShape();
			int prevX = 0, prevY = 0;
			int index = 0;
			for (T point : line.getPoints()) {
				int x = points[0][index];
				int y = points[1][index];
				if (index > 0) {
					line.getActivePointAs().set(point);
					line.getPointIndexAs().set(index);
					renderSegment(line, prevX, prevY, x, y, gfx);
				}
				prevX = x;
				prevY = y;
				index++;
			}
		}

		private void renderSegment(QuickFlexLine<T> line, int prevX, int prevY, int x, int y, Graphics2D gfx) {
			int dx, dy;
			double d;
			if (line.isDistanceNeeded() || line.isStyleDynamic()) {
				dx = x - prevX;
				dy = y - prevY;
				d = Math.sqrt(dx * dx + dy * dy);
				line.getPointDistanceAs().set(d);
			} else {
				dx = dy = 0;
				d = 0;
			}
			if (!line.isStyleDynamic()) { // Constant style
				renderConstStyleSegment(prevX, prevY, x, y, gfx);
				return;
			}
			Double svd = line.getStyleVarianceDistance().get();
			if (svd == null)
				svd = 1.0;
			int divs = toInt(d / svd);
			if (divs <= 1) {
				renderConstStyleSegment(prevX, prevY, dx, dy, gfx);
				return;
			}

			int x0 = prevX, y0 = prevY;
			for (int div = 0; div < divs; div++) {
				double p = div * 1.0 / divs;
				int x1 = (int) Math.round(prevX + p * dx);
				int y1 = (int) Math.round(prevY + p * dy);
				line.getLinearPAs().set(p);
				renderConstStyleSegment(x0, y0, x1, y1, gfx);
				x0 = x1;
				y0 = y1;
			}
		}

		private void renderConstStyleSegment(int prevX, int prevY, int x, int y, Graphics2D gfx) {
			float opacity = getOpacity();
			Color color = getColor();
			double thickness = getThickness();
			if (opacity <= 0 || color.getAlpha() == 0 || thickness <= 0)
				return;
			if (opacity < 1) {
				color = Colors.transluce(color, opacity);
				if (color.getAlpha() == 0)
					return;
			}
			StrokeDashing dashing = getStrokeDash();
			dashing.apply(gfx, (float) thickness, true);
			gfx.drawLine(prevX, prevY, x, y);
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			if (!isVisible())
				return null;

			// Don't query the style info now, as it may vary by point, or even along each segment

			QuickFlexLine<T> line = getShape();
			double prevX = Double.NaN, prevY = Double.NaN;
			int index = 0;
			for (T point : line.getPoints()) {
				line.getActivePointAs().set(point);
				line.getPointIndexAs().set(index);
				double x = line.getPointX().get();
				double y = line.getPointY().get();
				if (!Double.isNaN(prevX) && !Double.isNaN(prevY) && !Double.isNaN(x) && !Double.isNaN(y)) {
					double dx, dy, d;
					if (line.isDistanceNeeded() || line.isThicknessDynamic()) {
						dx = x - prevX;
						dy = y - prevY;
						d = Math.sqrt(dx * dx + dy * dy);
						line.getPointDistanceAs().set(d);
					} else
						dx = dy = d = 0;
					if (!line.isThicknessDynamic()) {
						Point2D.Float hit = getConstThicknessHit(line, prevX, prevY, x, y, containerPoint);
						if (hit != null)
							return hit;
					} else {
						Double svd = line.getStyleVarianceDistance().get();
						if (svd == null)
							svd = 1.0;
						int divs = toInt(d / svd);
						if (divs <= 1)
							return getConstThicknessHit(line, prevX, prevY, x, y, containerPoint);

						double x0 = prevX, y0 = prevY;
						for (int div = 0; div < divs; div++) {
							double p = div * 1.0 / divs;
							double x1 = prevX + p * dx;
							double y1 = prevY + p * dy;
							line.getLinearPAs().set(p);
							Point2D.Float hit = getConstThicknessHit(line, x0, y0, x1, y1, containerPoint);
							if (hit != null)
								return hit;
							x0 = x1;
							y0 = y1;
						}
					}
				}
				prevX = x;
				prevY = y;
				index++;
			}
			return null;
		}

		private Point2D.Float getConstThicknessHit(QuickFlexLine<T> line, double prevX, double prevY, double x, double y,
			Point2D.Float containerPoint) {
			HitData hit = getHit(prevX, prevY, x, y, containerPoint.x, containerPoint.y, getThickness());
			if (hit != null) {
				line.getLinearPAs().set(hit.p);
				return new Point2D.Float((float) hit.x, (float) hit.y);
			}
			return null;
		}
	}

	static class InterpretedShapeView extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickShapeView> {
		private final Map<Object, InterpretedTransformOp<?>> theTransformations;

		InterpretedShapeView(QuickShapeView.Interpreted shapeView, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(shapeView, tx);
			theTransformations = new HashMap<>();
			for (TransformOp.Interpreted<?> transform : shapeView.getTransformations())
				theTransformations.put(transform.getIdentity(), tx.transform(transform, InterpretedTransformOp.class));
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickShapeView element) throws ModelInstantiationException {
			List<QuickDrawTransform> transformations = new ArrayList<>(element.getTransformations().size());
			for (int i = 0; i < element.getTransformations().size(); i++) {
				TransformOp tx = element.getTransformations().get(element.getTransformations().size() - i - 1);
				transformations.add(((InterpretedTransformOp<TransformOp>) theTransformations.get(tx.getIdentity())).interpret(tx));
			}
			return new QuickDrawShapeView(element, getPublishers(element), Collections.unmodifiableList(transformations));
		}
	}

	static class QuickDrawShapeView extends SimpleShapeContainer {
		private final List<QuickDrawTransform> theTransformations;
		private final Observable<?> theUpdate;
		private ListElement<QuickShapeInterpretation> theFocus;

		private AffineTransform theTransform;
		private AffineTransform theReverseTransform;

		public QuickDrawShapeView(QuickShapeView shapeView, List<QuickDrawShapePublisher> publishers,
			List<QuickDrawTransform> transformations) {
			super(publishers, shapeView.onDestroy());
			theTransformations = transformations;
			Observable<?>[] txUpdates = new Observable[theTransformations.size() + 1];
			txUpdates[0] = super.update();
			for (int i = 0; i < theTransformations.size(); i++)
				txUpdates[i + 1] = theTransformations.get(i).update();
			theUpdate = Observable.or(txUpdates);
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		protected ListElement<QuickShapeInterpretation> getFocus() {
			return theFocus;
		}

		@Override
		protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
			theFocus = shape;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		private void updateTransform() {
			theTransform = new AffineTransform();
			for (QuickDrawTransform tx : theTransformations)
				tx.transform(theTransform);
			try {
				theReverseTransform = theTransform.createInverse();
			} catch (NoninvertibleTransformException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle2D.Float screen) {
			updateTransform();
			if (theTransform.isIdentity()) {
				super.draw(gfx, screen);
				return;
			}
			gfx.transform(theTransform);
			Point2D.Float ul = transform(new Point2D.Float(screen.x, screen.y), theReverseTransform);
			Point2D.Float ur = transform(new Point2D.Float(screen.x + screen.width, screen.y), theReverseTransform);
			Point2D.Float ll = transform(new Point2D.Float(screen.x, screen.y + screen.height), theReverseTransform);
			Point2D.Float lr = transform(new Point2D.Float(screen.x + screen.width, screen.y + screen.height), theReverseTransform);
			float minX = min(ul.x, ur.x, ll.x, lr.x);
			float minY = min(ul.y, ur.y, ll.y, lr.y);
			float maxX = max(ul.x, ur.x, ll.x, lr.x);
			float maxY = max(ul.y, ur.y, ll.y, lr.y);
			Rectangle2D.Float txScreen = new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
			try {
				super.draw(gfx, txScreen);
			} finally {
				if (theReverseTransform != null)
					gfx.transform(theReverseTransform);
			}
		}

		private static float min(float x1, float x2, float x3, float x4) {
			float min = x1;
			if (x2 < min)
				min = x2;
			if (x3 < min)
				min = x3;
			if (x4 < min)
				min = x4;
			return min;
		}

		private static float max(float x1, float x2, float x3, float x4) {
			float max = x1;
			if (x2 > max)
				max = x2;
			if (x3 > max)
				max = x3;
			if (x4 > max)
				max = x4;
			return max;
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			updateTransform();
			if (theReverseTransform != null) {
				containerPoint = transform(containerPoint, theReverseTransform);
			}
			return super.hit(containerPoint);
		}
	}

	static class InterpretedTranslate implements InterpretedTransformOp<Translate> {
		InterpretedTranslate(Translate.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(Translate element) throws ModelInstantiationException {
			return new QuickDrawTranslate(element);
		}
	}

	static class QuickDrawTranslate implements QuickDrawTransform {
		private final SettableValue<Double> theX;
		private final SettableValue<Double> theY;

		QuickDrawTranslate(Translate element) {
			theX = element.getX();
			theY = element.getY();
		}

		@Override
		public void transform(AffineTransform source) {
			double x = theX.get();
			double y = theY.get();
			source.translate(x, y);
		}

		@Override
		public Observable<?> update() {
			return Observable.onRootFinish(Observable.or(theX.noInitChanges(), theY.noInitChanges()));
		}
	}

	static class InterpretedScale implements InterpretedTransformOp<Scale> {
		InterpretedScale(Scale.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(Scale element) throws ModelInstantiationException {
			return new QuickDrawScale(element);
		}
	}

	static class QuickDrawScale implements QuickDrawTransform {
		private final SettableValue<Double> theX;
		private final SettableValue<Double> theY;
		private final ErrorReporting theReporting;

		QuickDrawScale(Scale element) {
			theX = element.getX();
			theY = element.getY();
			theReporting = element.reporting();
		}

		@Override
		public void transform(AffineTransform source) {
			double x = theX.get();
			double y = theY.get();
			if (x == 0 || y == 0) {
				theReporting.error("Scale cannot be ==0 (" + x + ", " + y + ")");
				if (x == 0)
					x = 1;
				if (y == 0)
					y = 1;
			}
			source.scale(x, y);
		}

		@Override
		public Observable<?> update() {
			return Observable.onRootFinish(Observable.or(theX.noInitChanges(), theY.noInitChanges()));
		}
	}

	static class InterpretedRotate implements InterpretedTransformOp<Rotate> {
		InterpretedRotate(Rotate.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(Rotate element) throws ModelInstantiationException {
			return new QuickDrawRotate(element);
		}
	}

	static class QuickDrawRotate implements QuickDrawTransform {
		private final SettableValue<Double> theAnchorX;
		private final SettableValue<Double> theAnchorY;
		private final SettableValue<Double> theRadians;

		QuickDrawRotate(Rotate element) {
			theAnchorX = element.getAnchorX();
			theAnchorY = element.getAnchorY();
			theRadians = element.getRadians();
		}

		@Override
		public void transform(AffineTransform source) {
			double radians = theRadians.get();
			source.rotate(radians, theAnchorX.get(), theAnchorY.get());
		}

		@Override
		public Observable<?> update() {
			return Observable
				.onRootFinish(Observable.or(theAnchorX.noInitChanges(), theAnchorY.noInitChanges(), theRadians.noInitChanges()));
		}
	}

	static class InterpretedToCoords implements InterpretedTransformOp<ToCoords> {
		InterpretedToCoords(ToCoords.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(ToCoords element) throws ModelInstantiationException {
			return new QuickDrawToCoords(element);
		}
	}

	static class QuickDrawToCoords implements QuickDrawTransform {
		private final SettableValue<Boolean> isActive;
		private final SettableValue<Double> theSourceWidth;
		private final SettableValue<Double> theSourceHeight;
		private final boolean isFlipY;
		private final SettableValue<Double> theTargetMinX;
		private final SettableValue<Double> theTargetMinY;
		private final SettableValue<Double> theTargetWidth;
		private final SettableValue<Double> theTargetHeight;

		QuickDrawToCoords(ToCoords element) {
			isActive = element.isActive();
			theSourceWidth = element.getSourceWidth();
			theSourceHeight = element.getSourceHeight();
			isFlipY = element.isFlipY();
			theTargetMinX = element.getTargetMinX();
			theTargetMinY = element.getTargetMinY();
			theTargetWidth = element.getTargetWidth();
			theTargetHeight = element.getTargetHeight();
		}

		@Override
		public void transform(AffineTransform source) {
			if (isActive.get()) {
				double screenWidth = theSourceWidth.get();
				double screenHeight = theSourceHeight.get();
				double targetMinX = theTargetMinX.get();
				double targetMinY = theTargetMinY.get();
				double targetWidth = theTargetWidth.get();
				double targetHeight = theTargetHeight.get();
				if (screenWidth <= 0 || screenHeight <= 0 || targetWidth <= 0 || targetHeight <= 0)
					return;

				double translateX = -targetMinX;
				double translateY = -targetMinY;
				double scaleX = screenWidth / targetWidth;
				double scaleY = screenHeight / targetHeight;
				if (isFlipY) {
					translateY -= targetHeight * 2;
					scaleY = -scaleY;
				}
				source.scale(scaleX, scaleY);
				source.translate(translateX, translateY);
			}
		}

		@Override
		public Observable<?> update() {
			return Observable.onRootFinish(Observable.or(//
				isActive.noInitChanges(), theSourceWidth.noInitChanges(), theSourceHeight.noInitChanges(), //
				theTargetMinX.noInitChanges(), theTargetMinY.noInitChanges(), //
				theTargetWidth.noInitChanges(), theTargetHeight.noInitChanges()));
		}
	}

	static class InterpretedChart extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickChart> {
		private InterpretedChartAxis<?> theHAxis;
		private InterpretedChartAxis<?> theVAxis;

		InterpretedChart(QuickChart.Interpreted<?> chart, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(chart, tx);
			theHAxis = tx.transform(chart.getHAxis(), InterpretedChartAxis.class);
			theVAxis = tx.transform(chart.getVAxis(), InterpretedChartAxis.class);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickChart element) throws ModelInstantiationException {
			return new QuickDrawChart(element, //
				((InterpretedChartAxis<Object>) theHAxis).interpret((QuickChart.ChartAxis<Object>) element.getHAxis()), //
				((InterpretedChartAxis<Object>) theVAxis).interpret((QuickChart.ChartAxis<Object>) element.getVAxis()),
				getPublishers(element));
		}
	}

	static class QuickDrawChart extends QuickDrawRectangle<QuickChart> {
		private final QuickDrawChartAxis<?> theHAxis;
		private final QuickDrawChartAxis<?> theVAxis;

		QuickDrawChart(QuickChart chart, QuickDrawChartAxis<?> hAxis, QuickDrawChartAxis<?> vAxis, List<QuickDrawShapePublisher> contents) {
			super(chart, contents);
			theHAxis = hAxis;
			theVAxis = vAxis;
		}

	}

	static class InterpretedChartAxis<T> {
		private final QuickChart.ChartAxis.Interpreted<T> theAxis;

		InterpretedChartAxis(Interpreted<T> axis, Transformer<ExpressoInterpretationException> tx) {
			theAxis = axis;
		}

		QuickDrawChartAxis<T> interpret(QuickChart.ChartAxis<T> axis) {
			return new QuickDrawChartAxis<>(axis);
		}
	}

	static class QuickDrawChartAxis<T> {
		private final QuickChart.ChartAxis<T> theAxis;

		QuickDrawChartAxis(ChartAxis<T> axis) {
			theAxis = axis;
		}

		float getLabelSectionSize(boolean vertical) {
			return 0;
		}

		void drawLabelSection(Graphics2D gfx, Rectangle2D.Float bounds) {
		}

		void drawChartSection(Graphics2D gfx, Rectangle2D.Float bounds) {
		}
	}
}
