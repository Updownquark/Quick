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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SettableValue.Setter;
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
import org.observe.quick.draw.QuickCustomDraw;
import org.observe.quick.draw.QuickDrawText;
import org.observe.quick.draw.QuickEllipse;
import org.observe.quick.draw.QuickFlexLine;
import org.observe.quick.draw.QuickGradientPlot;
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
import org.observe.util.TypeTokens;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.BiTuple;
import org.qommons.Colors;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MappedList;
import org.qommons.fn.FloatConsumer;
import org.qommons.fn.TriConsumer;
import org.qommons.fn.TriFunction;
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

		void transformBounds(Point2D.Float bounds);

		/** @return Any change sources in this transform */
		Observable<?> update();
	}

	public interface QuickDrawSimpleTransform extends QuickDrawTransform {
		default boolean isSimple() {
			return true;
		}

		QuickDrawScreen transform(QuickDrawScreen screen);
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
		void draw(QuickDrawScreen screen);

		/**
		 * @param containerPoint The point in the container to test
		 * @return The corresponding location in this shape, or null if this shape does not contain the given point
		 */
		Point2D.Float hit(Point2D.Float containerPoint);

		/**
		 * @param point The point in this shape
		 * @return This shape's opacity at the given point
		 */
		DrawOpacity getOpacity(Point2D.Float point);

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
		DrawOpacity mousePressed(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		DrawOpacity mouseReleased(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		DrawOpacity mouseClicked(MouseEvent e, Point2D.Float point);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		DrawOpacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point);

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
		tx.with(QuickDrawText.Interpreted.class, InterpretedText.class, InterpretedText::new);
		tx.with(QuickLine.Interpreted.class, InterpretedLine.class, InterpretedLine::new);
		tx.with(QuickFlexLine.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedFlexLine::new);
		tx.with(QuickShapeView.Interpreted.class, InterpretedShapeView.class, InterpretedShapeView::new);
		tx.with(Translate.Interpreted.class, InterpretedTransformOp.class, InterpretedTranslate::new);
		tx.with(Scale.Interpreted.class, InterpretedTransformOp.class, InterpretedScale::new);
		tx.with(Rotate.Interpreted.class, InterpretedTransformOp.class, InterpretedRotate::new);
		tx.with(ToCoords.Interpreted.class, InterpretedTransformOp.class, InterpretedToCoords::new);

		tx.with(QuickChart.Interpreted.class, InterpretedChart.class, InterpretedChart::new);
		tx.with(QuickChart.ChartAxis.Interpreted.class, InterpretedChartAxis.class, InterpretedChartAxis::new);
		tx.with(QuickChart.TickLine.Interpreted.class, InterpretedChartLine.class, InterpretedChartLine::new);
		tx.with(QuickChart.GridLines.Interpreted.class, InterpretedGridLines.class, InterpretedGridLines::new);
		tx.with(QuickGradientPlot.Interpreted.class, InterpretedGradientPlot.class, InterpretedGradientPlot::new);

		tx.with(QuickCustomDraw.Interpreted.class, InterpretedCustomDraw.class, InterpretedCustomDraw::new);
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
		public void draw(QuickDrawScreen screen) {
			isDrawing = true;
			try {
				for (ListElement<QuickShapeInterpretation> shape = theContents.getTerminalElement(true); shape != null; shape = shape
					.getAdjacent(true)) {
					setState(shape);
					shape.get().draw(screen);
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
					done = el.get().getOpacity(hit) == DrawOpacity.Full;
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
		public DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = true;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = true;
			ListElement<QuickShapeInterpretation> focus = null;
			DrawOpacity opacity = DrawOpacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != DrawOpacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				Point2D.Float hit = shape.hit(point);
				if (hit == null)
					continue;
				DrawOpacity shapeOpacity = shape.mousePressed(e, hit);
				if (shapeOpacity != DrawOpacity.None)
					focus = el;
				opacity = opacity.or(shapeOpacity);
			}
			setFocus(focus);
			return opacity;
		}

		@Override
		public DrawOpacity mouseReleased(MouseEvent e, Point2D.Float point) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = false;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = false;
			return mouseAction(e, point, QuickShapeInterpretation::mouseReleased);
		}

		@Override
		public DrawOpacity mouseClicked(MouseEvent e, Point2D.Float point) {
			return mouseAction(e, point, QuickShapeInterpretation::mouseClicked);
		}

		@Override
		public DrawOpacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
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
		public DrawOpacity getOpacity(Point2D.Float point) {
			DrawOpacity opacity = DrawOpacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != DrawOpacity.Full && el != null; el = el.getAdjacent(false)) {
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

		public <E extends MouseEvent> DrawOpacity mouseAction(E e, Point2D.Float point,
			TriConsumer<QuickShapeInterpretation, E, Point2D.Float> action) {
			DrawOpacity opacity = DrawOpacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != DrawOpacity.Full && el != null; el = el.getAdjacent(false)) {
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

		private BufferedImage theBuffer;
		private boolean isBufferUpToDate;

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
			theContainer.update().takeUntil(until).act(__ -> {
				isBufferUpToDate = false;
				repaint();
			});
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
			Graphics2D repaintBuffer;
			if (theBuffer == null || getWidth() != theBuffer.getWidth() || getHeight() != theBuffer.getHeight()) {
				isBufferUpToDate = false;
				theBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
				repaintBuffer = theBuffer.createGraphics();
				repaintBuffer.setBackground(Colors.transparent);
			} else if (!isBufferUpToDate) {
				repaintBuffer = theBuffer.createGraphics();
				repaintBuffer.setBackground(Colors.transparent);
				repaintBuffer.clearRect(0, 0, theBuffer.getWidth(), theBuffer.getHeight());
			} else {
				repaintBuffer = null;
			}

			if (repaintBuffer != null) {
				theContainer.draw(new QuickDrawScreen.SimpleScreen(repaintBuffer, getWidth(), getHeight(), Transaction.NONE));
				isBufferUpToDate = true;
			}

			g.drawImage(theBuffer, 0, 0, null);
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
		public void draw(QuickDrawScreen screen) {
			isInAction = true;
			try {
				SettableValue<T> activeValue = theCollection.getActiveValue();
				SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
				try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
					Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
					Transaction ct = theCollection.getValues().lock(false)) {
					int i = 0;
					for (ListElement<T> value = theCollection.getValues().getTerminalElement(true); //
						value != null; value = value.getAdjacent(true)) {
						theCurrentValue = value;
						valueSetter.set(value.get());
						indexSetter.set(i);
						super.draw(screen);
						i++;
					}
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
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValues().lock(false)) {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					valueSetter.set(valueEl.get());
					indexSetter.set(i);
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
							done = shapeEl.get().getOpacity(hit) == DrawOpacity.Full;
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
		public DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			ListElement<T> focusEl = null;
			DrawOpacity opacity = DrawOpacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValues().lock(false)) {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != DrawOpacity.Full && valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					valueSetter.set(valueEl.get());
					indexSetter.set(i);
					DrawOpacity valueOpacity = super.mousePressed(e, point);
					if (valueOpacity != DrawOpacity.None)
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
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValues().lock(false)) {
				while (hovered.hasNext()) {
					BiTuple<ElementId, ElementId> el = hovered.next();
					if (!el.getValue1().isPresent() || !el.getValue2().isPresent())
						continue;
					ListElement<T> valueEl = theCollection.getValues().getElement(el.getValue1());
					theCurrentValue = valueEl;
					valueSetter.set(valueEl.get());
					indexSetter.set(valueEl.getElementsBefore());
					ListElement<QuickShapeInterpretation> shape = getContents().getElement(el.getValue2());
					setState(shape);
					Point2D.Float hit = shape.get().hit(point);
					hovered.remove();
					setState(shape);
					shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED), hit);
				}
			}
		}

		@Override
		public <E extends MouseEvent> DrawOpacity mouseAction(E e, Point2D.Float point,
			TriConsumer<QuickShapeInterpretation, E, Point2D.Float> action) {
			DrawOpacity opacity = DrawOpacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValues().lock(false)) {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != DrawOpacity.Full && valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					valueSetter.set(valueEl.get());
					indexSetter.set(i);
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
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValues().lock(false)) {
				for (BiTuple<ElementId, ElementId> hovered : theHovered.reverse()) {
					ListElement<T> valueEl = theCollection.getValues().getElement(hovered.getValue1());
					theCurrentValue = valueEl;
					QuickShapeInterpretation shape = getContents().getElement(hovered.getValue2()).get();
					valueSetter.set(valueEl.get());
					indexSetter.set(valueEl.getElementsBefore());
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
		public void draw(QuickDrawScreen screen) {
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValueChanges().lock(false)) {
				int i = 0;
				for (T value : theCollection.getValues()) {
					valueSetter.set(value);
					indexSetter.set(i);
					super.draw(screen);
					i++;
				}
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
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValueChanges().lock(false)) {
				int i = theCollection.getValues().size() - 1;
				for (ListIterator<T> iter = theCollection.getValues().listIterator(i + 1); iter.hasPrevious();) {
					valueSetter.set(iter.previous());
					indexSetter.set(i);
					for (CollectionElement<QuickShapeInterpretation> shapeEl = getContents().getTerminalElement(false); shapeEl != null; //
						shapeEl = shapeEl.getAdjacent(false)) {
						Point2D.Float hit = shapeEl.get().hit(point);
						if (done) { // We don't have enough info to support enter/exit
						} else if (hit != null) {
							QuickShapeInterpretation target = shapeEl.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED), hit);
							if (first == null)
								first = target;
							done = shapeEl.get().getOpacity(hit) == DrawOpacity.Full;
						}
					}
					i--;
				}
			}
			return first;
		}

		@Override
		public DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			DrawOpacity opacity = DrawOpacity.None;
			if (!isMouseListening)
				return opacity;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValueChanges().lock(false)) {
				int i = theCollection.getValues().size() - 1;
				for (ListIterator<T> iter = theCollection.getValues().listIterator(i + 1); opacity != DrawOpacity.Full
					&& iter.hasPrevious();) {
					valueSetter.set(iter.previous());
					indexSetter.set(i);
					DrawOpacity valueOpacity = super.mousePressed(e, point);
					opacity = opacity.or(valueOpacity);
					i--;
				}
			}
			return opacity;
		}

		@Override
		public <E extends MouseEvent> DrawOpacity mouseAction(E e, Point2D.Float point,
			TriConsumer<QuickShapeInterpretation, E, Point2D.Float> action) {
			DrawOpacity opacity = DrawOpacity.None;
			if (!isMouseListening)
				return opacity;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			try (Setter<T> valueSetter = activeValue.lockWrite(false, null); //
				Setter<Integer> indexSetter = activeIndex.lockWrite(false, null); //
				Transaction ct = theCollection.getValueChanges().lock(false)) {
				int i = theCollection.getValues().size() - 1;
				for (ListIterator<T> iter = theCollection.getValues().listIterator(i + 1); opacity != DrawOpacity.Full
					&& iter.hasPrevious();) {
					valueSetter.set(iter.previous());
					indexSetter.set(i);
					opacity = opacity.or(super.mouseAction(e, point, action));
					i--;
				}
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
			theUpdate = Observable.or(getShape().isVisible().noInitChanges(), theColor.noInitChanges(), theOpacity.noInitChanges());
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
			return getOpacity(1.0f);
		}

		protected float getOpacity(float defaultValue) {
			Float opacity = theOpacity.get();
			return opacity == null ? defaultValue : opacity.floatValue();
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
		public DrawOpacity getOpacity(Point2D.Float point) {
			if (!isVisible())
				return DrawOpacity.None;
			Color color = theColor.get();
			Float opacity = theOpacity.get();
			if (color == null) {
				if (opacity == null || opacity.floatValue() >= 1.0f)
					return DrawOpacity.Full;
				else if (opacity.floatValue() <= 0.0f)
					return DrawOpacity.None;
				else
					return DrawOpacity.Partial;
			}
			int alpha;
			if (opacity == null || opacity.floatValue() == 1.0f)
				alpha = color.getAlpha();
			else
				alpha = Math.round(color.getAlpha() * opacity.floatValue());
			if (alpha <= 0)
				return DrawOpacity.None;
			else if (alpha >= 255)
				return DrawOpacity.Full;
			else
				return DrawOpacity.Partial;
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

		protected <L extends QuickMouseListener, E extends MouseEvent> DrawOpacity mouse(Class<L> listenerType, Predicate<L> filter, E evt,
			Point2D.Float point, BiConsumer<L, E> install, boolean ifNoHit) {
			Point2D.Float hit = hit(point);
			if (hit == null) {
				if (ifNoHit)
					hit = point;
				else
					return DrawOpacity.None;
			}
			input(listenerType, filter, evt, install.andThen((listener, evt2) -> {
				SettableValue<Float> x = (SettableValue<Float>) listener.getEventX();
				SettableValue<Float> y = (SettableValue<Float>) listener.getEventY();
				x.set(point.x, evt2);
				y.set(point.y, evt2);
			}));
			return getOpacity(hit);
		}

		protected DrawOpacity mouseMove(MouseMoveEventType eventType, MouseEvent evt, Point2D.Float point, boolean ifNoHit) {
			return mouse(QuickMouseListener.QuickMouseMoveListener.class, mml -> mml.getEventType() == eventType, evt, point, noConfig(),
				ifNoHit);
		}

		protected <L extends QuickMouseButtonListener> DrawOpacity mouseButton(Class<L> listenerType, Predicate<? super L> filter,
			MouseEvent evt, Point2D.Float point, BiConsumer<L, MouseEvent> install) {
			return mouse(listenerType, lstnr -> {
				MouseButton button = QuickCoreSwing.checkMouseEventType(evt, lstnr.getButton());
				if (button == null)
					return false;
				lstnr.getEventButton().set(button, evt);
				if (filter != null && !filter.test(lstnr))
					return false;
				return true;
			}, evt, point, install, false);
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point) {
			if (mouseMove(MouseMoveEventType.Enter, e, point, false) != DrawOpacity.None)
				return this;
			else
				return null;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point) {
			if (mouseMove(MouseMoveEventType.Move, e, point, false) != DrawOpacity.None)
				return this;
			else
				return null;
		}

		@Override
		public void mouseDragged(MouseEvent e, Point2D.Float point) {
		}

		@Override
		public void mouseExited(MouseEvent e, Point2D.Float point) {
			mouseMove(MouseMoveEventType.Exit, e, point, true);
		}

		@Override
		public DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			return mouseButton(QuickMouseListener.QuickMousePressedListener.class, null, e, point, noConfig());
		}

		@Override
		public DrawOpacity mouseReleased(MouseEvent e, Point2D.Float point) {
			return mouseButton(QuickMouseListener.QuickMouseReleasedListener.class, null, e, point, noConfig());
		}

		@Override
		public DrawOpacity mouseClicked(MouseEvent e, Point2D.Float point) {
			return mouseButton(QuickMouseListener.QuickMouseClickListener.class, lstnr -> {
				if (lstnr.getClickCount() > 0 && e.getClickCount() != lstnr.getClickCount())
					return false;
				return true;
			}, e, point, noConfig());
		}

		@Override
		public DrawOpacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			return mouse(QuickMouseListener.QuickScrollListener.class, null, e, point, (lstnr, evt) -> {
				lstnr.getScrollAmount().set(evt.getScrollAmount());
			}, false);
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

		@Override
		public String toString() {
			return theShape.toString();
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

		interface FloatCompare {
			int compare(float f);
		}

		/**
		 * @param width The width of the shape
		 * @param height The height of the shape
		 * @param rotation The rotation of the shape
		 * @param screen The screen bounds
		 */
		public boolean updateBounds(QuickSize width, QuickSize height, double rotation, QuickDrawScreen screen) {
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

			boolean rotate = rotation != 0.0;

			if (rotate) { // Need transformation
				if (theTransform == null)
					theTransform = new AffineTransform();
				else
					theTransform.setToIdentity();

				theTransform.translate(theFBounds.x, theFBounds.y);
				if (rotate)
					theTransform.rotate(rotation, anchor.x - theFBounds.x, anchor.y - theFBounds.y);

				try {
					theTransformInverse = theTransform.createInverse();
				} catch (NoninvertibleTransformException e) {
					theShape.reporting().error(e.getMessage(), e);
				}

				theIBounds.x = theIBounds.y = 0;
				theIBounds.width = (int) theFBounds.width;
				theIBounds.height = (int) theFBounds.height;
			} else {
				theTransform = theTransformInverse = null;

				Point p = screen.tx(theFBounds.x, theFBounds.y);
				theIBounds.x = p.x;
				theIBounds.y = p.y;
				p = screen.tx(theFBounds.x + theFBounds.width, theFBounds.y + theFBounds.height);
				if (p.x >= theIBounds.x)
					theIBounds.width = p.x - theIBounds.x;
				else {
					theIBounds.width = theIBounds.x - p.x;
					theIBounds.x = p.x;
				}
				if (p.y >= theIBounds.y)
					theIBounds.height = p.y - theIBounds.y;
				else {
					theIBounds.height = theIBounds.y - p.y;
					theIBounds.y = p.y;
				}
			}

			return true;
		}

		private boolean evaluateH(Positionable hPos, QuickSize width, Point2D.Float anchor, QuickDrawScreen screen, boolean debugging) {
			return evaluateDimension(x -> theFBounds.x = x, w -> theFBounds.width = w, a -> anchor.x += a, hPos, width, screen.getWidth(),
				screen::containsX, "width", debugging);
		}

		private boolean evaluateV(Positionable vPos, QuickSize height, Point2D.Float anchor, QuickDrawScreen screen, boolean debugging) {
			return evaluateDimension(y -> theFBounds.y = y, h -> theFBounds.height = h, a -> anchor.y += a, vPos, height,
				screen.getHeight(), screen::containsY, "height", debugging);
		}

		private boolean evaluateDimension(FloatConsumer setPosition, FloatConsumer setSize, FloatConsumer setAnchor, Positionable positions,
			QuickSize specSize, float screenSize, FloatCompare contains, String sizeName, boolean debugging) {
			QuickSize leading, center, trailing;
			if (positions == null)
				leading = center = trailing = null;
			else {
				leading = positions.getLeading().get();
				center = positions.getCenter().get();
				trailing = positions.getTrailing().get();
			}
			float pos, size, anchor;
			// There are many ways of specifying the positioning and height of the shape here.
			// The user will get a warning if any of the attributes conflict,
			// but we still have to decide which attributes will take priority in that case.
			// We'll respect size first, then leading, then trailing, and center last.
			if (specSize != null) {
				size = specSize.evaluateFloat(screenSize);
				if (Float.isInfinite(size))
					return false;
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
			int containsMin = contains.compare(pos);
			if (containsMin != 0 && (containsMin > 0) == (size > 0))
				draw = false;
			else {
				int containsMax = contains.compare(pos + size);
				if (containsMax != 0 && (containsMax < 0) == size > 0)
					draw = false;
				else
					draw = true;
			}

			if (draw || debugging) {
				setPosition.accept(pos);
				setSize.accept(size);
				setAnchor.accept(anchor);
			}
			return draw;
		}
	}

	static abstract class QuickDrawSimpleShape<S extends QuickSimpleShape> extends QuickDrawBorderedShape<S> {
		private final Observable<?> theUpdate;
		private QuickDrawScreen theScreen;
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
			if (!theBounds.getFBounds().contains(containerPoint))
				return null;
			return getHit(containerPoint);
		}

		protected abstract Point2D.Float getHit(Point2D.Float point);

		@Override
		public void draw(QuickDrawScreen screen) {
			if (!isVisible())
				return;

			theScreen = screen;
			boolean draw = theBounds.updateBounds(//
				getShape().getWidth().get(), getShape().getHeight().get(), theRotationValue.get(), screen);
			String debugPrint = getShape().getDebugPrint().get();
			if (!draw && debugPrint == null)
				return;
			Rectangle2D.Float fb = theBounds.getFBounds();
			Rectangle intBounds = theBounds.getBounds();
			QuickDrawScreen myScreen;
			if (theBounds.getTransform() != null) {
				Graphics2D gfx = screen.getTransformedGraphics();
				gfx.transform(theBounds.getTransform());
				// We don't use the dimensions
				myScreen = new QuickDrawScreen.SimpleScreen(gfx, (float) fb.getWidth(), (float) fb.getHeight(), gfx::dispose);
			} else
				myScreen = screen;
			try {
				if (debugPrint != null) {
					StringBuilder msg = new StringBuilder();
					msg.append(debugPrint).append(": Drawing at [")//
					.append(fb.getMinX()).append(", ").append(fb.getMinY()).append("] to [")//
					.append(fb.getMaxX()).append(", ").append(fb.getMaxY()).append(']');
					if (theBounds.getTransform() != null) {
						Point2D.Float pt = new Point2D.Float();
						pt.setLocation(fb.getMinX(), fb.getMinY());
						try {
							theBounds.getTransform().inverseTransform(pt, pt);
							msg.append("\n\tTransformed to [").append(pt.getX()).append(", ").append(pt.getY()).append("] to [");
							pt.setLocation(fb.getMaxX(), fb.getMaxY());
							theBounds.getTransform().inverseTransform(pt, pt);
							msg.append(pt.getX()).append(", ").append(pt.getY()).append(']');
						} catch (NoninvertibleTransformException e) {
						}
					} else if (myScreen.isTransformed()) {
						Point pt = myScreen.transformToRoot(fb.x, fb.y);
						msg.append("\n\tTransformed to [").append(pt.getX()).append(", ").append(pt.getY()).append("] to [");
						pt = myScreen.transformToRoot(fb.x + fb.width, fb.y + fb.height);
						msg.append(pt.getX()).append(", ").append(pt.getY()).append(']');
					}
					getShape().reporting().info(msg.toString());
				}
				if (draw) {
					doDraw(myScreen, intBounds);
					if (hasInnerContents()) {
						try (QuickDrawScreen contentScreen = myScreen.subScreen(intBounds.x, intBounds.y, intBounds.width,
							intBounds.height)) {
							drawInnerContents(contentScreen);
						}
					}
				}
			} finally {
				if (myScreen != screen)
					myScreen.close();
			}
		}

		protected abstract void doDraw(QuickDrawScreen screen, Rectangle bounds);

		protected boolean hasInnerContents() {
			return false;
		}

		protected void drawInnerContents(QuickDrawScreen screen) {
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
		protected void doDraw(QuickDrawScreen screen, Rectangle bounds) {
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
					screen.gfx().setColor(bg);
					screen.gfx().fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				screen.gfx().setColor(borderColor);
				dash.apply(screen.gfx(), borderThickness, false);
				screen.gfx().drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}

		}

		@Override
		protected boolean hasInnerContents() {
			return !theContainer.getShapes().isEmpty();
		}

		@Override
		protected void drawInnerContents(QuickDrawScreen screen) {
			theContainer.draw(screen);
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
		public DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return DrawOpacity.None;
			DrawOpacity opacity = theContainer.mousePressed(e, point);
			if (opacity != DrawOpacity.Full)
				opacity = opacity.or(super.mousePressed(e, point));
			return opacity;
		}

		@Override
		public DrawOpacity mouseReleased(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return DrawOpacity.None;
			DrawOpacity opacity = theContainer.mouseReleased(e, point);
			if (opacity != DrawOpacity.Full)
				opacity = opacity.or(super.mouseReleased(e, point));
			return opacity;
		}

		@Override
		public DrawOpacity mouseClicked(MouseEvent e, Point2D.Float point) {
			if (!isVisible())
				return DrawOpacity.None;
			DrawOpacity opacity = theContainer.mouseClicked(e, point);
			if (opacity != DrawOpacity.Full)
				opacity = opacity.or(super.mouseClicked(e, point));
			return opacity;
		}

		@Override
		public DrawOpacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			if (!isVisible())
				return DrawOpacity.None;
			DrawOpacity opacity = theContainer.mouseWheelMoved(e, point);
			if (opacity != DrawOpacity.Full)
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
		protected void doDraw(QuickDrawScreen screen, Rectangle bounds) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					screen.gfx().setColor(bg);
					screen.gfx().fillOval(bounds.x, bounds.y, bounds.width, bounds.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				screen.gfx().setColor(borderColor);
				dash.apply(screen.gfx(), borderThickness, false);
				screen.gfx().drawOval(bounds.x, bounds.y, bounds.width, bounds.height);
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

		String getDebugPrint();

		ErrorReporting reporting();

		@Override
		default void draw(QuickDrawScreen screen) {
			float[][] vertices = getVertices();
			if (vertices == null)
				return;
			int[][] points = screen.tx(vertices);

			String debugPrint = getDebugPrint();

			if (debugPrint != null) {
				StringBuilder msg = new StringBuilder();
				msg.append(debugPrint).append(": Drawing at ");
				for (int p = 0; p < vertices[0].length; p++) {
					if (p > 0)
						msg.append(", ");
					msg.append('[').append(vertices[0][p]).append(", ").append(vertices[1][p]).append(']');
				}
				if (screen.isTransformed()) {
					msg.append("\n\tTransformed to ");
					for (int p = 0; p < vertices[0].length; p++) {
						Point pt = screen.transformToRoot(vertices[0][p], vertices[1][p]);
						if (p > 0)
							msg.append(", ");
						msg.append('[').append(pt.getX()).append(", ").append(pt.getY()).append(']');
					}
				}
				reporting().info(msg.toString());
			}

			draw(screen.gfx(), points);
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
		public void draw(QuickDrawScreen screen) {
			if (isVisible())
				VertexedShape.super.draw(screen);
		}

		@Override
		public String getDebugPrint() {
			return getShape().getDebugPrint().get();
		}

		@Override
		public ErrorReporting reporting() {
			return getShape().reporting();
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
		public void draw(QuickDrawScreen screen) {
			if (!isVisible())
				return;
			Rectangle2D.Float bounds = updateBounds(screen.gfx());
			if (bounds == null)
				return;
			double rotation = theRotationValue == null ? 0.0 : theRotationValue.get();
			if (!theBounds.updateBounds(QuickSize.ofPixels(bounds.width), QuickSize.ofPixels(bounds.height), rotation, screen))
				return;
			// System.out.println(theText + " bounds=" + bounds + " screen=" + screen + " rot=" + (rotation / Math.PI * 180) + " tx="
			// + theBounds.getBounds());
			theBounds.getBounds().y += bounds.height;
			if (theBounds.getTransform() != null) {
				Graphics2D gfx = screen.getTransformedGraphics();
				gfx.transform(theBounds.getTransform());
				try {
					drawText(gfx, theBounds.getBounds().x, theBounds.getBounds().y);
				} finally {
					gfx.dispose();
				}
			} else {
				drawText(screen.gfx(), theBounds.getBounds().x, theBounds.getBounds().y);
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

		public Rectangle2D.Float getRotatedBounds(QuickDrawScreen screen) {
			if (!isVisible())
				return null;
			Rectangle2D.Float bounds = updateBounds(screen.gfx());
			if (bounds == null)
				return null;
			double rotation = theRotationValue == null ? 0.0 : theRotationValue.get();
			if (!theBounds.updateBounds(//
				QuickSize.ofPixels(bounds.width), QuickSize.ofPixels(bounds.height), rotation, screen))
				return null;
			return theBounds.getFBounds();
		}

		protected Rectangle2D.Float updateBounds(Graphics2D gfx) {
			theParentFont = gfx.getFont();
			return updateBounds(gfx.getFontRenderContext());
		}

		protected Rectangle2D.Float updateBounds(FontRenderContext renderCtx) {
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
			Rectangle2D.Float bounds = new Rectangle2D.Float();
			if (theTextBounds != null) {
				bounds.x = (float) theTextBounds.getX();
				bounds.y = (float) theTextBounds.getY();
				bounds.width = (float) theTextBounds.getWidth();
				bounds.height = (float) theTextBounds.getHeight();
			}
			for (QuickDrawSwingText subText : theSubTexts) {
				Rectangle2D.Float subBounds = subText.updateBounds(renderCtx);
				if (subBounds != null) {
					if (bounds.width > 0)
						subBounds.x += bounds.width;
					bounds.add(subBounds);
				}
			}
			return bounds;
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			if (!isVisible())
				return null;
			Rectangle2D.Float bounds = updateBounds(theRenderContext);
			if (bounds == null)
				return null;
			Point2D.Float txPoint = transform(containerPoint, theBounds.getTransformInverse());
			return bounds.contains(txPoint) ? containerPoint : null;
		}

		@Override
		public DrawOpacity getOpacity(Point2D.Float point) {
			return DrawOpacity.Partial;
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

		public static HitData getHit(double x0, double y0, double x1, double y1, float x, float y, double dLimit) {
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

	static abstract class AbstractLine<L extends QuickLinearShape> extends QuickDrawLinearShape<L> implements VertexedShape {
		protected AbstractLine(L shape) {
			super(shape);
		}

		@Override
		public void draw(QuickDrawScreen screen) {
			if (isVisible())
				VertexedShape.super.draw(screen);
		}

		@Override
		public String getDebugPrint() {
			return getShape().getDebugPrint().get();
		}

		@Override
		public ErrorReporting reporting() {
			return getShape().reporting();
		}

		@Override
		public void draw(Graphics2D gfx, int[][] points) {
			drawLine(gfx, getColor(), getOpacity(), getThickness(), getStrokeDash(), points);
		}

		public static void drawLine(Graphics2D gfx, Color color, float opacity, double thickness, StrokeDashing dash, int[][] points) {
			if (thickness <= 0 || opacity <= 0 || color.getAlpha() == 0)
				return;

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
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			if (!isVisible())
				return null;
			double thickness = getThickness();
			if (thickness <= 0)
				return null;

			float[][] vertices = getVertices();
			for (int v = 1; v < vertices[0].length; v++) {
				if (getHit(vertices[0][v - 1], vertices[1][v - 1], //
					vertices[0][v], vertices[1][v], //
					containerPoint.x, containerPoint.y, thickness) != null)
					return containerPoint;
			}
			return null;
		}
	}

	static class QuickDrawLine extends AbstractLine<QuickLine> {
		private final Observable<?> theUpdate;

		QuickDrawLine(QuickLine shape) {
			super(shape);
			theUpdate = Observable.onRootFinish(Observable.or(getShape().isVisible().noInitChanges(), //
				Observable.or(shape.getStyle().changes(), //
					Observable.or(shape.getPoints().stream().flatMap(pt -> Stream.of(pt.getX().noInitChanges(), pt.getY().noInitChanges()))
						.collect(Collectors.toList())))
					.filterP(__ -> isVisible())));
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
		public void draw(QuickDrawScreen screen) {
			if (isVisible())
				VertexedShape.super.draw(screen);
		}

		@Override
		public String getDebugPrint() {
			return getShape().getDebugPrint().get();
		}

		@Override
		public ErrorReporting reporting() {
			return getShape().reporting();
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
		public void draw(QuickDrawScreen screen) {
			boolean simple = true;
			for (QuickDrawTransform txform : theTransformations) {
				if (!(txform instanceof QuickDrawSimpleTransform) || !((QuickDrawSimpleTransform) txform).isSimple()) {
					simple = false;
					break;
				}
			}
			if (simple) {
				for (QuickDrawTransform txform : theTransformations) {
					screen = ((QuickDrawSimpleTransform) txform).transform(screen);
					if (screen == null)
						break;
				}
				if (screen != null)
					super.draw(screen);
			} else {
				updateTransform();
				if (theTransform.isIdentity()) {
					super.draw(screen);
					return;
				}
				Graphics2D gfx = screen.getTransformedGraphics();
				try {
					gfx.transform(theTransform);
					Point2D.Float bounds = new Point2D.Float(screen.getWidth(), screen.getHeight());
					for (QuickDrawTransform txform : theTransformations)
						txform.transformBounds(bounds);
					screen = new QuickDrawScreen.SimpleScreen(gfx, bounds.x, bounds.y, Transaction.NONE);
					super.draw(screen);
				} finally {
					gfx.dispose();
				}
			}
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

	static class QuickDrawTranslate implements QuickDrawSimpleTransform {
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
		public void transformBounds(Point2D.Float bounds) {
		}

		@Override
		public QuickDrawScreen transform(QuickDrawScreen screen) {
			return screen.transform(theX.get().floatValue(), theY.get().floatValue(), 1f, 1f);
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

	static class QuickDrawScale implements QuickDrawSimpleTransform {
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
		public void transformBounds(Point2D.Float bounds) {
			double x = theX.get();
			double y = theY.get();
			if (x == 0 || y == 0) {
				theReporting.error("Scale cannot be ==0 (" + x + ", " + y + ")");
				if (x == 0)
					x = 1;
				if (y == 0)
					y = 1;
			}
			bounds.x *= (float) x;
			bounds.y *= (float) y;
		}

		@Override
		public QuickDrawScreen transform(QuickDrawScreen screen) {
			double x = theX.get();
			double y = theY.get();
			if (x == 0 || y == 0) {
				theReporting.error("Scale cannot be ==0 (" + x + ", " + y + ")");
				if (x == 0)
					x = 1;
				if (y == 0)
					y = 1;
			}
			return screen.transform(0f, 0f, (float) x, (float) y);
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
		public void transformBounds(Point2D.Float bounds) {
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

	static class QuickDrawToCoords implements QuickDrawSimpleTransform {
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
				transform(source, screenWidth, screenHeight, targetMinX, targetMinY, targetWidth, targetHeight, isFlipY);
			}
		}

		@Override
		public void transformBounds(Point2D.Float bounds) {
			double screenWidth = theSourceWidth.get();
			double screenHeight = theSourceHeight.get();
			double targetWidth = theTargetWidth.get();
			double targetHeight = theTargetHeight.get();
			if (screenWidth <= 0 || screenHeight <= 0 || targetWidth == 0 || targetHeight == 0)
				return;
			bounds.x = (float) targetWidth;
			bounds.y = (float) targetHeight;
		}

		@Override
		public QuickDrawScreen transform(QuickDrawScreen screen) {
			if (!isActive.get())
				return screen;

			double screenWidth = theSourceWidth.get();
			double screenHeight = theSourceHeight.get();
			double targetMinX = theTargetMinX.get();
			double targetMinY = theTargetMinY.get();
			double targetWidth = theTargetWidth.get();
			double targetHeight = theTargetHeight.get();
			return transform(screen, screenWidth, screenHeight, targetMinX, targetMinY, targetWidth, targetHeight, isFlipY);
		}

		public static void transform(AffineTransform source, //
			double screenWidth, double screenHeight, //
			double targetMinX, double targetMinY, double targetWidth, double targetHeight, boolean flipY) {
			if (screenWidth <= 0 || screenHeight <= 0 || targetWidth == 0 || targetHeight == 0)
				return;

			double translateX = -targetMinX;
			double translateY = -targetMinY;
			double scaleX = screenWidth / targetWidth;
			double scaleY = screenHeight / targetHeight;
			if (flipY) {
				translateY -= targetHeight * 2;
				scaleY = -scaleY;
			}
			source.scale(scaleX, scaleY);
			source.translate(translateX, translateY);
		}

		public static QuickDrawScreen transform(QuickDrawScreen screen, //
			double screenWidth, double screenHeight, //
			double targetMinX, double targetMinY, double targetWidth, double targetHeight, boolean flipY) {
			if (screenWidth <= 0 || screenHeight <= 0 || targetWidth == 0 || targetHeight == 0)
				return screen;

			double scaleX = screenWidth / targetWidth;
			double scaleY = screenHeight / targetHeight;
			double translateX = -targetMinX;
			double translateY = -targetMinY;
			if (flipY) {
				translateY -= targetHeight * 2;
				scaleY = -scaleY;
			} else if (scaleY < 0) {
				// translateY = targetHeight - translateY;
			}
			translateX *= scaleX;
			translateY *= scaleY;
			return screen.transform((float) translateX, (float) translateY, (float) scaleX, (float) scaleY);
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
			theHAxis = chart.getHAxis() == null ? null : tx.transform(chart.getHAxis(), InterpretedChartAxis.class);
			theVAxis = chart.getVAxis() == null ? null : tx.transform(chart.getVAxis(), InterpretedChartAxis.class);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickChart element) throws ModelInstantiationException {
			return new QuickDrawChart(element, //
				theHAxis == null ? null
					: ((InterpretedChartAxis<Number>) theHAxis).interpret((QuickChart.ChartAxis<Number>) element.getHAxis()), //
					theVAxis == null ? null
						: ((InterpretedChartAxis<Number>) theVAxis).interpret((QuickChart.ChartAxis<Number>) element.getVAxis()),
						getPublishers(element));
		}
	}

	static class QuickDrawChart extends QuickDrawRectangle<QuickChart> {
		private final QuickDrawChartAxis<?> theHAxis;
		private final QuickDrawChartAxis<?> theVAxis;
		private final List<QuickSwingGridLines> theGridLines;

		private Rectangle2D.Float theHLabelSection;
		private Rectangle2D.Float theVLabelSection;
		private Rectangle2D.Float theChartArea;

		private boolean isHAxisHovered;
		private boolean isVAxisHovered;
		private boolean isChartAreaHovered;

		QuickDrawChart(QuickChart chart, QuickDrawChartAxis<?> hAxis, QuickDrawChartAxis<?> vAxis, List<QuickDrawShapePublisher> contents) {
			super(chart, contents);
			theHAxis = hAxis;
			theVAxis = vAxis;
			theGridLines = new ArrayList<>();
			for (QuickDrawShapePublisher content : contents) {
				if (content instanceof QuickSwingGridLines) {
					theGridLines.add((QuickSwingGridLines) content);
					((QuickSwingGridLines) content).setAxes(theHAxis, theVAxis);
				}
			}
		}

		@Override
		public float getOpacity() {
			return getOpacity(0.0f);
		}

		@Override
		protected boolean hasInnerContents() {
			return theHAxis != null || theVAxis != null || super.hasInnerContents();
		}

		@Override
		protected void drawInnerContents(QuickDrawScreen screen) {
			// First figure out where everything needs to be
			int hAxisSize = theHAxis == null ? 0 : (int) Math.ceil(theHAxis.getLabelSectionSize(screen, false));
			int vAxisSize = theVAxis == null ? 0 : (int) Math.ceil(theVAxis.getLabelSectionSize(screen, true));
			if (hAxisSize > screen.getWidth())
				hAxisSize = (int) screen.getWidth();
			if (vAxisSize > screen.getHeight())
				vAxisSize = (int) screen.getHeight();
			// Default for the horizontal axis is the bottom
			boolean hAxisTop = theHAxis == null ? false : Boolean.TRUE.equals(theHAxis.getAxis().isLeading().get());
			// Default for the vertical axis is the left
			boolean vAxisLeft = theVAxis == null ? false : !Boolean.FALSE.equals(theVAxis.getAxis().isLeading().get());
			if (theHAxis == null)
				theHLabelSection = null;
			else
				theHLabelSection = new Rectangle2D.Float(//
					vAxisLeft ? vAxisSize : 0, //
						hAxisTop ? 0 : screen.getHeight() - hAxisSize, //
							screen.getWidth() - vAxisSize, //
							hAxisSize);
			if (theVAxis == null)
				theVLabelSection = null;
			else
				theVLabelSection = new Rectangle2D.Float(//
					vAxisLeft ? 0 : screen.getWidth() - vAxisSize, //
						hAxisTop ? hAxisSize : 0, //
							vAxisSize, //
							screen.getHeight() - hAxisSize);
			if (theHAxis == null && theVAxis == null)
				theChartArea = new Rectangle2D.Float(0, 0, screen.getWidth(), screen.getHeight());
			else
				theChartArea = new Rectangle2D.Float(//
					vAxisLeft ? vAxisSize : 0, //
						hAxisTop ? hAxisSize : 0, //
							screen.getWidth() - vAxisSize, //
							screen.getHeight() - hAxisSize);

			// Draw the label sections
			if (theHAxis != null)
				theHAxis.drawLabelSection(screen, theHLabelSection, false, hAxisTop);
			if (theVAxis != null)
				theVAxis.drawLabelSection(screen, theVLabelSection, true, vAxisLeft);

			// Draw the content graphics
			try (
				QuickDrawScreen contentScreen = screen.subScreen(theChartArea.x, theChartArea.y, theChartArea.width, theChartArea.height)) {
				if (theHAxis != null || theVAxis != null) {
					float minX, maxX, minY, maxY;
					if (theHAxis != null) {
						minX = theHAxis.getAxis().getMin().get().floatValue();
						maxX = theHAxis.getAxis().getMax().get().floatValue();
					} else {
						minX = 0;
						maxX = theChartArea.width;
					}
					if (theVAxis != null) {
						minY = theVAxis.getAxis().getMin().get().floatValue();
						maxY = theVAxis.getAxis().getMax().get().floatValue();
					} else {
						minY = 0;
						maxY = theChartArea.height;
					}
					try (QuickDrawScreen txScreen = QuickDrawToCoords.transform(contentScreen, theChartArea.width, theChartArea.height,
						minX, minY, maxX - minX, maxY - minY, false)) {
						if (txScreen != null)
							super.drawInnerContents(txScreen);
					}
				} else
					super.drawInnerContents(contentScreen);
			}
		}

		@Override
		protected Point2D.Float getHit(Point2D.Float point) {
			Point2D.Float hit = null;
			if (theHLabelSection != null && theHLabelSection.contains(point))
				hit = theHAxis.getHit(point);
			if (hit == null && theVLabelSection != null && theVLabelSection.contains(point))
				hit = theVAxis.getHit(point);
			if (hit == null && theChartArea.contains(point)) {
				hit = super.getHit(toContentPoint(point));
				if (hit != null)
					hit = fromContentPoint(hit);
			}
			return hit;
		}

		Point2D.Float toContentPoint(Point2D.Float point) {
			if (theHAxis != null || theVAxis != null) {
				float ptX, ptY;
				if (theHAxis != null) {
					float minX = theHAxis.getAxis().getMin().get().floatValue();
					float maxX = theHAxis.getAxis().getMax().get().floatValue();
					ptX = (point.x - theChartArea.x) / theChartArea.width * (maxX - minX) + minX;
				} else {
					ptX = point.x - theChartArea.x;
				}
				if (theVAxis != null) {
					float minY = theVAxis.getAxis().getMin().get().floatValue();
					float maxY = theVAxis.getAxis().getMax().get().floatValue();
					ptY = (point.y - theChartArea.y) / theChartArea.height * (maxY - minY) + minY;
				} else {
					ptY = point.y - theChartArea.y;
				}
				return new Point2D.Float(ptX, ptY);
			} else
				return point;
		}

		Point2D.Float fromContentPoint(Point2D.Float point) {
			if (theHAxis != null || theVAxis != null) {
				if (theHAxis != null) {
					float minX = theHAxis.getAxis().getMin().get().floatValue();
					float maxX = theHAxis.getAxis().getMax().get().floatValue();
					point.x = (point.x - minX) / (maxX - minX) * theChartArea.width + theChartArea.x;
				}
				if (theVAxis != null) {
					float minY = theVAxis.getAxis().getMin().get().floatValue();
					float maxY = theVAxis.getAxis().getMax().get().floatValue();
					point.y = (point.y - minY) / (maxY - minY) * theChartArea.height + theChartArea.y;
				}
			}
			return point;
		}

		protected QuickShapeInterpretation moused(MouseEvent e, Point2D.Float point, //
			TriFunction<QuickDrawChartAxis<?>, MouseEvent, Point2D.Float, QuickShapeInterpretation> axisAction,
			BiFunction<MouseEvent, Point2D.Float, QuickShapeInterpretation> superAction) {

			QuickShapeInterpretation hit = null;
			if (theHLabelSection != null && theHLabelSection.contains(point)) {
				isHAxisHovered = true;
				hit = axisAction.apply(theHAxis, e, point);
			} else
				isHAxisHovered = false;
			if (hit == null && theVLabelSection != null && theVLabelSection.contains(point)) {
				isVAxisHovered = true;
				hit = axisAction.apply(theVAxis, e, point);
			} else
				isVAxisHovered = false;
			if (hit == null && theChartArea.contains(point)) {
				isChartAreaHovered = true;
				hit = superAction.apply(e, toContentPoint(point));
			} else
				isChartAreaHovered = false;
			return hit;
		}

		protected void moused0(MouseEvent e, Point2D.Float point, //
			TriConsumer<QuickDrawChartAxis<?>, MouseEvent, Point2D.Float> axisAction, BiConsumer<MouseEvent, Point2D.Float> superAction) {

			if (theHLabelSection != null && theHLabelSection.contains(point))
				axisAction.accept(theHAxis, e, point);
			if (theVLabelSection != null && theVLabelSection.contains(point))
				axisAction.accept(theVAxis, e, point);
			if (theChartArea.contains(point))
				superAction.accept(e, toContentPoint(point));
		}

		protected DrawOpacity mousedO(MouseEvent e, Point2D.Float point, //
			TriFunction<QuickDrawChartAxis<?>, MouseEvent, Point2D.Float, DrawOpacity> axisAction,
			BiFunction<MouseEvent, Point2D.Float, DrawOpacity> superAction) {

			DrawOpacity opacity = DrawOpacity.None;
			if (theHLabelSection != null && theHLabelSection.contains(point))
				opacity = axisAction.apply(theHAxis, e, point);
			if (opacity != DrawOpacity.Full && theVLabelSection != null && theVLabelSection.contains(point))
				opacity = axisAction.apply(theVAxis, e, point);
			if (opacity != DrawOpacity.Full && theChartArea.contains(point)) {
				opacity = superAction.apply(e, toContentPoint(point));
			}
			return opacity;
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point) {
			return moused(e, point, QuickDrawChartAxis::mouseEntered, super::mouseEntered);
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point) {
			return moused(e, point, QuickDrawChartAxis::mouseMoved, super::mouseMoved);
		}

		@Override
		public void mouseDragged(MouseEvent e, Point2D.Float point) {
			moused0(e, point, QuickDrawChartAxis::mouseDragged, super::mouseDragged);
		}

		@Override
		public void mouseExited(MouseEvent e, Point2D.Float point) {
			if (isHAxisHovered) {
				isHAxisHovered = false;
				theHAxis.mouseExited(e, point);
			}
			if (isVAxisHovered) {
				isVAxisHovered = false;
				theVAxis.mouseExited(e, point);
			}
			if (isChartAreaHovered) {
				isChartAreaHovered = false;
				super.mouseExited(e, toContentPoint(point));
			}
		}

		@Override
		public DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			return mousedO(e, point, QuickDrawChartAxis::mousePressed, super::mousePressed);
		}

		@Override
		public DrawOpacity mouseReleased(MouseEvent e, Point2D.Float point) {
			return mousedO(e, point, QuickDrawChartAxis::mouseReleased, super::mouseReleased);
		}

		@Override
		public DrawOpacity mouseClicked(MouseEvent e, Point2D.Float point) {
			return mousedO(e, point, QuickDrawChartAxis::mouseClicked, super::mouseClicked);
		}

		@Override
		public DrawOpacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			DrawOpacity opacity = DrawOpacity.None;
			if (theHLabelSection != null && theHLabelSection.contains(point))
				opacity = theHAxis.mouseWheelMoved(e, point);
			if (opacity != DrawOpacity.Full && theVLabelSection != null && theVLabelSection.contains(point))
				opacity = theVAxis.mouseWheelMoved(e, point);
			if (opacity != DrawOpacity.Full && theChartArea.contains(point)) {
				opacity = super.mouseWheelMoved(e, toContentPoint(point));
			}
			return opacity;
		}
	}

	static class InterpretedChartAxis<T extends Number> {
		private final InterpretedText theLabel;
		private final InterpretedChartLine<QuickChart.TickLine> theTickLine;

		InterpretedChartAxis(QuickChart.ChartAxis.Interpreted<T> axis, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			QuickDrawText.Interpreted label = axis.getLabel();
			theLabel = label == null ? null : tx.transform(label, InterpretedText.class);
			QuickChart.TickLine.Interpreted tickLine = axis.getTickLine();
			theTickLine = tickLine == null ? null : tx.transform(tickLine, InterpretedChartLine.class);
		}

		QuickDrawChartAxis<T> interpret(QuickChart.ChartAxis<T> axis) throws ModelInstantiationException {
			return new QuickDrawChartAxis<>(axis, //
				theLabel == null ? null : theLabel.interpret(axis.getLabel()), //
					theTickLine == null ? null : theTickLine.interpret(axis.getTickLine()));
		}
	}

	static class QuickDrawChartAxis<T extends Number> {
		public interface TickConsumer {
			void onTick(int index, double tick, float relativePosition);
		}

		private final QuickChart.ChartAxis<T> theAxis;
		private final QuickDrawSwingText theLabel;
		private final QuickChartLine<QuickChart.TickLine> theTickLine;

		private final ObservableValue<Color> theColor;
		private final ObservableValue<Float> theOpacity;
		private final ObservableValue<Double> theThickness;
		private final ObservableValue<StrokeDashing> theDashing;

		QuickDrawChartAxis(ChartAxis<T> axis, QuickDrawSwingText label, QuickChartLine<QuickChart.TickLine> tickLine) {
			theAxis = axis;
			theLabel = label;
			theTickLine = tickLine;

			theColor = axis.getStyle().getColor();
			theOpacity = axis.getStyle().getOpacity();
			theThickness = axis.getStyle().getThickness();
			theDashing = axis.getStyle().getStrokeDash();
		}

		public QuickChart.ChartAxis<T> getAxis() {
			return theAxis;
		}

		public Color getColor() {
			Color color = theColor.get();
			return color == null ? Color.black : color;
		}

		public float getOpacity() {
			Float opacity = theOpacity.get();
			return opacity == null ? 1.0f : opacity.floatValue();
		}

		public double getThickness() {
			Double thickness = theThickness.get();
			return thickness == null ? 2.0 : thickness.doubleValue();
		}

		public StrokeDashing getDashing() {
			StrokeDashing dashing = theDashing.get();
			return dashing == null ? StrokeDashing.full : dashing;
		}

		public void forEachTick(TickConsumer task) {
			T min = theAxis.getMin().get();
			T max = theAxis.getMax().get();
			if (min == null || max == null || Objects.equals(min, max))
				return;
			float minD = min.floatValue();
			float range = max.floatValue() - minD;
			SettableValue<Double> tickValue = theAxis.getTickValue();
			SettableValue<Integer> tickIndex = theAxis.getTickIndex();
			int index = 0;
			for (Double tick : theAxis.getScheme().getTicks(min.doubleValue(), max.doubleValue())) {
				if (tickValue != null)
					tickValue.set(tick);
				if (tickIndex != null)
					tickIndex.set(index);

				float p = (tick.floatValue() - minD) / range;
				task.onTick(index, tick, p);

				index++;
			}
		}

		float getLabelSectionSize(QuickDrawScreen screen, boolean vertical) {
			if (!theAxis.isVisible().get())
				return 0.0f;
			float[] maxSize = new float[1];
			forEachTick((i, t, p) -> {
				float tickSize = 1 + (float) getThickness();
				if (theTickLine != null)
					tickSize += theTickLine.getShape().getLength().get();
				if (theLabel != null) {
					Rectangle2D.Float labelBounds = theLabel.getRotatedBounds(screen);
					if (labelBounds != null)
						tickSize += vertical ? labelBounds.width : labelBounds.height;
				}
				if (tickSize > maxSize[0])
					maxSize[0] = tickSize;
			});
			return maxSize[0];
		}

		void drawLabelSection(QuickDrawScreen screen, Rectangle2D.Float bounds, boolean vertical, boolean leading) {
			if (!theAxis.isVisible().get())
				return;
			float thickness = (float) getThickness();
			float halfThickness = thickness / 2;
			int[][] axisLine = new int[2][2];
			if (vertical) {
				int initX = (int) (screen.transformX(bounds.x + (leading ? bounds.width : 0)) + (leading ? -halfThickness : halfThickness));
				axisLine[0][0] = axisLine[0][1] = initX;
				axisLine[1][0] = (int) screen.transformX(bounds.y);
				axisLine[1][1] = (int) screen.transformX(bounds.y + bounds.height);
			} else {
				int initY = (int) (screen.transformY(bounds.y + (leading ? bounds.height : 0))
					+ (leading ? -halfThickness : halfThickness));
				axisLine[1][0] = axisLine[1][1] = initY;
				axisLine[0][0] = (int) screen.transformX(bounds.x);
				axisLine[0][1] = (int) screen.transformX(bounds.x + bounds.width);
			}
			QuickDrawLine.drawLine(screen.gfx(), getColor(), getOpacity(), thickness, getDashing(), axisLine);
			float[][] tickPoints = new float[2][2];
			forEachTick((i, t, p) -> {
				if (vertical) { // Vertical axis
					float y = bounds.y + p * bounds.height;
					float x = bounds.x;
					if (leading)
						x += bounds.width - thickness;
					else
						x += thickness;
					if (theTickLine != null) {
						int tickLength = theTickLine.getShape().getLength().get();
						tickPoints[1][0] = tickPoints[1][1] = y;
						if (leading) {
							tickPoints[0][0] = x - tickLength;
							tickPoints[0][1] = x;
							x -= tickLength;
						} else {
							tickPoints[0][0] = x;
							tickPoints[0][1] = x + tickLength;
							x += tickLength;
						}
						theTickLine.setVertices(tickPoints);
						theTickLine.draw(screen);
					}
					x += (leading ? -1 : 1);
					if (theLabel != null) {
						Rectangle2D.Float labelBounds = theLabel.getRotatedBounds(screen);
						if (labelBounds != null) {
							int alignment = 0; // TODO get this from the <chart-tick-label> add-on
							float labelX = x, labelY = y;
							labelY -= 3; // I hate having to pad with magic numbers, but otherwise it clips
							if (leading)
								labelX -= labelBounds.width;
							if (alignment < 0)
								labelY -= labelBounds.height;
							else if (alignment == 0)
								labelY -= labelBounds.height / 2;
							try (QuickDrawScreen labelScreen = screen.transform(labelX, labelY, 1f, 1f)) {
								if (labelScreen != null)
									theLabel.draw(labelScreen);
							}
						}
					}
				} else { // Horizontal axis
					float x = bounds.x + p * bounds.width;
					float y = bounds.y;
					if (leading)
						y += bounds.height - thickness;
					else
						y += thickness;
					if (theTickLine != null) {
						int tickLength = theTickLine.getShape().getLength().get();
						tickPoints[0][0] = tickPoints[0][1] = x;
						if (leading) {
							tickPoints[1][0] = y - tickLength;
							tickPoints[1][1] = y;
							y -= tickLength;
						} else {
							tickPoints[1][0] = y;
							tickPoints[1][1] = y + tickLength;
							y += tickLength;
						}
						theTickLine.setVertices(tickPoints);
						theTickLine.draw(screen);
					}
					y += (leading ? -1 : 1);
					if (theLabel != null) {
						Rectangle2D.Float labelBounds = theLabel.getRotatedBounds(screen);
						if (labelBounds != null) {
							int alignment = 0; // TODO get this from the <chart-tick-label> add-on
							float labelX = x, labelY = y;
							labelY -= 2; // I hate having to pad with magic numbers, but otherwise it clips
							if (leading)
								labelY -= labelBounds.height;
							if (alignment < 0)
								labelX -= labelBounds.width;
							else if (alignment == 0)
								labelX -= labelBounds.width / 2;
							try (QuickDrawScreen labelScreen = screen.transform(labelX, labelY, 1f, 1f)) {
								theLabel.draw(labelScreen);
							}
						}
					}
				}
			});
		}

		Point2D.Float getHit(Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return null;
			// TODO
			return null;
		}

		QuickShapeInterpretation mouseEntered(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return null;
			// TODO
			return null;
		}

		QuickShapeInterpretation mouseMoved(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return null;
			// TODO
			return null;
		}

		void mouseDragged(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return;
			// TODO
			return;
		}

		void mouseExited(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return;
			// TODO
			return;
		}

		DrawOpacity mousePressed(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return DrawOpacity.None;
			// TODO
			return DrawOpacity.None;
		}

		DrawOpacity mouseReleased(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return DrawOpacity.None;
			// TODO
			return DrawOpacity.None;
		}

		DrawOpacity mouseClicked(MouseEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return DrawOpacity.None;
			// TODO
			return DrawOpacity.None;
		}

		DrawOpacity mouseWheelMoved(MouseWheelEvent e, Point2D.Float point) {
			if (!theAxis.isVisible().get())
				return DrawOpacity.None;
			// TODO
			return DrawOpacity.None;
		}
	}

	static class InterpretedChartLine<L extends QuickLinearShape> {
		InterpretedChartLine(QuickLinearShape.Interpreted<L> gridLine, Transformer<ExpressoInterpretationException> tx) {
		}

		QuickChartLine<L> interpret(L gridLine) {
			return new QuickChartLine<>(gridLine);
		}
	}

	static class QuickChartLine<L extends QuickLinearShape> extends AbstractLine<L> {
		private float[][] theVertices;

		QuickChartLine(L shape) {
			super(shape);
			theVertices = new float[2][2];
		}

		@Override
		public float[][] getVertices() {
			return theVertices;
		}

		public void setVertices(float[][] vertices) {
			theVertices = vertices;
		}
	}

	static class InterpretedGridLines implements InterpretedQuickShapePublisher<QuickChart.GridLines> {
		InterpretedGridLines(QuickChart.GridLines.Interpreted gridLines, Transformer<ExpressoInterpretationException> tx) {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickChart.GridLines element) throws ModelInstantiationException {
			return new QuickSwingGridLines(element);
		}
	}

	static class QuickSwingGridLines extends QuickDrawLinearShape<QuickChart.GridLines> {
		private QuickDrawChartAxis<?> theHAxis;
		private QuickDrawChartAxis<?> theVAxis;

		QuickSwingGridLines(QuickChart.GridLines shape) {
			super(shape);
		}

		public void setAxes(QuickDrawChartAxis<?> hAxis, QuickDrawChartAxis<?> vAxis) {
			theHAxis = hAxis;
			theVAxis = vAxis;
		}

		@Override
		public void draw(QuickDrawScreen screen) {
			if (theHAxis == null && theVAxis == null) {
				if (getShape().getParentElement() instanceof QuickChart)
					getShape().reporting().warn("No axes mean no grid lines");
				else
					getShape().reporting().warn("This type is only applicable for content in a <chart>");
				return;
			}
			if (theHAxis != null)
				drawGridLines(theHAxis, screen, false);
			if (theVAxis != null)
				drawGridLines(theVAxis, screen, true);
		}

		@Override
		public Point2D.Float hit(Point2D.Float containerPoint) {
			return null; // TODO
		}

		void drawGridLines(QuickDrawChartAxis<?> axis, QuickDrawScreen screen, boolean verticalAxis) {
			SettableValue<Boolean> verticalLine = getShape().getVerticalLine();
			if (verticalLine != null)
				verticalLine.set(!verticalAxis);
			SettableValue<Double> tickValueAs = getShape().getTickValueAs();
			SettableValue<Integer> tickIndexAs = getShape().getTickIndexAs();

			int[][] points = new int[2][2];
			if (verticalAxis) {
				points[0][0] = (int) screen.transformX(screen.getMinX());
				points[0][1] = (int) screen.transformX(screen.getMaxX());
			} else {
				points[1][0] = (int) screen.transformY(screen.getMinY());
				points[1][1] = (int) screen.transformY(screen.getMaxY());
			}
			axis.forEachTick((i, tick, p) -> {
				if (verticalAxis)
					points[1][0] = points[1][1] = (int) screen.transformY((float) tick);
				else
					points[0][0] = points[0][1] = (int) screen.transformX((float) tick);

				if (tickValueAs != null)
					tickValueAs.set(tick);
				if (tickIndexAs != null)
					tickIndexAs.set(i);

				AbstractLine.drawLine(screen.gfx(), getColor(), getOpacity(), getThickness(), getStrokeDash(), points);
			});
		}
	}

	static class InterpretedGradientPlot extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickGradientPlot> {
		InterpretedGradientPlot(QuickGradientPlot.Interpreted plot, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(plot, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickGradientPlot element) throws ModelInstantiationException {
			return new QuickDrawGradientPlot(element, getPublishers(element));
		}
	}

	static class QuickDrawGradientPlot extends QuickDrawRectangle<QuickGradientPlot> {
		private final Observable<?> theUpdate;

		public QuickDrawGradientPlot(QuickGradientPlot rectangle, List<QuickDrawShapePublisher> contents) {
			super(rectangle, contents);
			theUpdate = Observable.or(super.update(), Observable.onRootFinish(rectangle.getChanges()));
		}

		@Override
		protected void doDraw(QuickDrawScreen screen, Rectangle bounds) {
			super.doDraw(screen, bounds);
			if (!isVisible() || bounds.width <= 0 || bounds.height <= 0)
				return;

			BufferedImage image;
			try (GradientPlotRenderer renderer = new GradientPlotRenderer(getShape(), screen, bounds)) {
				if (renderer.isEmpty())
					return;

				image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_4BYTE_ABGR);

				renderer.draw(image);
			}
			screen.gfx().drawImage(image, bounds.x, bounds.y, null);
		}

		interface FloatUnaryOperator {
			float applyAsFloat(float arg);
		}

		private static class GradientPlotRenderer implements Transaction {
			private final QuickGradientPlot.Renderer theRenderer;
			private final float[] theXIndexes;
			private final float[] theYIndexes;
			private final int theDXIndex;
			private final int theDYIndex;
			private int thePrevXIndex;
			private int thePrevYIndex;
			private final boolean[] hasPrevYValues;
			private final float[] thePrevYValues;
			private boolean hasNextYPrevX;
			private float theNextYPrevX;
			private boolean hasNextYNextX;
			private float theNextYNextX;

			GradientPlotRenderer(QuickGradientPlot plot, QuickDrawScreen screen, Rectangle bounds) {
				List<Number> xList = plot.getXs(), yList = plot.getYs();
				if (xList == null || xList.isEmpty() || yList == null || yList.isEmpty()) {
					theRenderer = null;
					theXIndexes = theYIndexes = thePrevYValues = null;
					hasPrevYValues = null;
					theDXIndex = theDYIndex = 0;
					return;
				}

				// Convert to arrays and sort
				float[] xs = new float[xList.size()];
				float[] ys = new float[yList.size()];
				int i = 0;
				for (Number x : xList)
					xs[i++] = x.floatValue();
				Arrays.sort(xs);
				i = 0;
				for (Number y : yList)
					ys[i++] = y.floatValue();
				Arrays.sort(ys);

				// For each X and Y pixel, find its position within the available X and Y values
				theXIndexes = getIndexes(xs, bounds.x, bounds.width, screen::inverseTransformX);
				theYIndexes = getIndexes(ys, bounds.y, bounds.height, screen::inverseTransformY);
				theDXIndex = theXIndexes[theXIndexes.length - 1] > theXIndexes[0] ? 1 : -1;
				theDYIndex = theYIndexes[theYIndexes.length - 1] > theYIndexes[0] ? 1 : -1;

				hasPrevYValues = new boolean[bounds.width];
				thePrevYValues = new float[bounds.width];
				theRenderer = plot.getRenderer();
			}

			boolean isEmpty() {
				return theXIndexes == null;
			}

			void draw(BufferedImage image) {
				int width = image.getWidth();
				int height = image.getHeight();
				thePrevYIndex = (int) theYIndexes[0] - 1;
				for (int pixY = 0; pixY < height; pixY++) {
					int yIndex = (int) theYIndexes[pixY + 1];
					boolean interpolateY;// If no grid index occurs inside the Y pixel, interpolate.
					if (theDYIndex > 0)
						interpolateY = yIndex < theYIndexes[pixY];
					else {
						interpolateY = yIndex > theYIndexes[pixY];
					}
					int nextYIndex = yIndex + theDYIndex;
					thePrevXIndex = (int) theXIndexes[0] - 1;
					for (int pixX = 0; pixX < width; pixX++) {
						float pixelValue = getPixelValue(pixY, yIndex, nextYIndex, interpolateY, pixX);
						Color color = theRenderer.getColor(pixelValue);
						if (color == null)
							color = Colors.transparent;
						image.setRGB(pixX, pixY, color.getRGB());
					}
					thePrevYIndex = yIndex;
				}
			}

			private float getPixelValue(int pixY, int yIndex, int nextYIndex, boolean interpolateY, int pixX) {
				boolean hasPrevYPrevX = hasNextYPrevX;
				float prevYPrevX = theNextYPrevX;
				int xIndex = (int) theXIndexes[pixX + 1];
				boolean interpolateX;
				if (theDXIndex > 0)
					interpolateX = xIndex < theXIndexes[pixX];
				else {
					interpolateX = xIndex > theXIndexes[pixX];
				}
				int nextXIndex = xIndex + theDXIndex;
				try {
					if (interpolateY) {
						float y0, y1;
						if (interpolateX) { // Use bi-linear interpolation
							float x0y0;
							if (hasPrevYValues[pixX])
								x0y0 = thePrevYValues[pixX];
							else
								x0y0 = getValue(xIndex, xIndex, yIndex, yIndex);
							float x1y0;
							if (pixX + 1 < hasPrevYValues.length && hasPrevYValues[pixX + 1])
								x1y0 = thePrevYValues[pixX + 1];
							else
								x1y0 = getValue(nextXIndex, nextXIndex, yIndex, yIndex);
							float x0y1, x1y1;
							if (pixX > 0 && xIndex == (int) theXIndexes[pixX - 1]) {
								// Interpolating between the same values as last time
								x0y1 = theNextYPrevX;
								x1y1 = theNextYNextX;
							} else {
								if (hasNextYNextX)
									x0y1 = theNextYNextX;
								else
									x0y1 = getValue(xIndex, xIndex, nextYIndex, nextYIndex);
								x1y1 = getValue(nextXIndex, nextXIndex, nextYIndex, nextYIndex);
							}
							float avgXIndex = (theXIndexes[pixX] + theXIndexes[pixX + 1]) / 2;
							float px = avgXIndex - xIndex;
							y0 = x0y0 + px * (x1y0 - x0y0);
							y1 = x0y1 * px * (x1y1 - x0y1);
							hasNextYPrevX = hasNextYNextX = true;
							theNextYPrevX = x0y1;
							theNextYNextX = x1y1;
						} else {
							int minX = thePrevXIndex + 1, maxX = xIndex;
							if (minX == maxX) {
								if (hasPrevYValues[pixX])
									y0 = thePrevYValues[pixX];
								else
									y0 = getValue(minX, minX, yIndex, yIndex);
								if (hasNextYNextX)
									y1 = theNextYNextX;
								else {
									y1 = getValue(minX, minX, nextYIndex, nextYIndex);
									hasNextYPrevX = true;
									theNextYPrevX = y1;
								}
								hasNextYNextX = false;
							} else { // All-new set of X values that we know won't be used again
								y0 = getValue(minX, maxX, yIndex, yIndex);
								y1 = getValue(minX, maxX, nextYIndex, nextYIndex);
								hasNextYPrevX = hasNextYNextX = false;
							}
						}
						float avgYIndex = (theYIndexes[pixY] + theYIndexes[pixY + 1]) / 2;
						float py = avgYIndex - yIndex;
						return y0 + py * (y1 - y0);
					} else {
						int minY = thePrevYIndex + 1, maxY = yIndex;
						if (interpolateX) {
							float x0, x1;
							if (minY == maxY) {
								if (hasNextYPrevX)
									x0 = theNextYPrevX;
								else
									x0 = getValue(xIndex, xIndex, minY, minY);
								if (hasNextYNextX)
									x1 = theNextYNextX;
								else
									x1 = getValue(nextXIndex, nextXIndex, minY, minY);
							} else {
								x0 = getValue(xIndex, xIndex, minY, maxY);
								x1 = getValue(nextXIndex, nextXIndex, minY, maxY);
							}
							float avgXIndex = (theXIndexes[pixX] + theXIndexes[pixX + 1]) / 2;
							float px = avgXIndex - xIndex;
							return x0 + px * (x1 - x0);
						} else {
							int minX = thePrevXIndex + 1, maxX = xIndex;
							float pixelValue = getValue(minX, maxX, minY, maxY);
							if (minX == maxX) {
								hasNextYPrevX = true;
								theNextYPrevX = pixelValue;
							} else
								hasNextYPrevX = false;
							return pixelValue;
						}
					}
				} finally {
					// Store the prev Y value for the next row
					hasPrevYValues[pixX] = hasPrevYPrevX;
					if (hasPrevYPrevX)
						thePrevYValues[pixX] = prevYPrevX;
					thePrevXIndex = xIndex;
				}
			}

			private float getValue(int minX, int maxX, int minY, int maxY) {
				int temp;
				if (theDXIndex < 0) {
					temp = minX;
					minX = maxX;
					maxX = temp;
				}
				if (theDYIndex < 0) {
					temp = minY;
					minY = maxY;
					maxY = temp;
				}
				return theRenderer.getValue(minX, maxX, minY, maxY);
			}

			private static float[] getIndexes(float[] values, int pix0, int pixLength, FloatUnaryOperator transform) {
				float[] indexes = new float[pixLength + 1];
				float value0 = transform.applyAsFloat(pix0);
				float value1 = transform.applyAsFloat(pix0 + pixLength);
				boolean reverse = value0 > value1;
				float startValue = reverse ? value1 : value0;
				int startIndex = Arrays.binarySearch(values, startValue);
				float prevValue;
				int prevIndex, nextIndex;
				if (startIndex >= 0) {
					prevIndex = startIndex;
					nextIndex = startIndex + 1;
					prevValue = values[startIndex];
				} else {
					startIndex = -startIndex - 1;
					if (startIndex == 0) {
						prevValue = 0; // This doesn't matter. Will be skipped in the loop below.
						prevIndex = -1;
						nextIndex = 0;
					} else if (startIndex == values.length) {
						Arrays.fill(indexes, values.length);
						return indexes;
					} else {
						prevIndex = startIndex - 1;
						nextIndex = startIndex;
						prevValue = values[prevIndex];
					}
				}
				if (reverse) {
					for (int pix = pixLength; pix >= 0; pix--) {
						float value = transform.applyAsFloat(pix0 + pix);
						float nextValue = values[nextIndex];
						while (value > nextValue) {
							prevIndex = nextIndex;
							nextIndex++;
							prevValue = nextValue;
							if (nextIndex == values.length) {
								Arrays.fill(indexes, 0, pix + 1, values.length);
								return indexes;
							}
							nextValue = values[nextIndex];
						}
						indexes[pix] = prevIndex + (value - prevValue) / (nextValue - prevValue);
					}
				} else {
					for (int pix = 0; pix <= pixLength; pix++) {
						float value = transform.applyAsFloat(pix0 + pix);
						float nextValue = values[nextIndex];
						while (value > nextValue) {
							prevIndex = nextIndex;
							nextIndex++;
							prevValue = nextValue;
							if (nextIndex == values.length) {
								Arrays.fill(indexes, pix, pixLength + 1, values.length);
								return indexes;
							}
							nextValue = values[nextIndex];
						}
						indexes[pix] = prevIndex + (value - prevValue) / (nextValue - prevValue);
					}
				}
				return indexes;
			}

			@Override
			public void close() {
				if (theRenderer != null)
					theRenderer.close();
			}
		}
	}

	static class InterpretedCustomDraw implements InterpretedQuickShapePublisher<QuickCustomDraw> {
		InterpretedCustomDraw(QuickCustomDraw.Interpreted customDraw, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (!QuickCustomDrawer.class.isAssignableFrom(TypeTokens.getRawType(customDraw.getDrawer().getType().getType(0))))
				throw new ExpressoInterpretationException(
					"Value for <" + QuickCustomDraw.CUSTOM_DRAW + "> must evaluate to an instance of " + QuickCustomDrawer.class.getName(),
					customDraw.reporting().getFileLocation());
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickCustomDraw element) throws ModelInstantiationException {
			return new QuickSwingCustomDraw(element);
		}
	}

	static class QuickSwingCustomDraw extends QuickDrawSimpleShape<QuickCustomDraw> {
		private final ObservableValue<? extends QuickCustomDrawer> theDrawer;
		private final Observable<?> theUpdate;

		public QuickSwingCustomDraw(QuickCustomDraw shape) {
			super(shape);
			theDrawer = (ObservableValue<? extends QuickCustomDrawer>) shape.getDrawer();
			theUpdate = Observable.or(super.update(), theDrawer.noInitChanges(),
				ObservableValue.flattenObservableValue(theDrawer.map(d -> d == null ? null : d.getUpdate())));
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		protected void doDraw(QuickDrawScreen screen, Rectangle bounds) {
			if (!isVisible() || bounds.width <= 0 || bounds.height <= 0)
				return;
			Color color = getColor();
			Float opacity = getOpacity();
			if (opacity.floatValue() >= 0.0f && color.getAlpha() > 0) {
				if (opacity.floatValue() < 1.0f)
					color = Colors.transluce(color, opacity);
				screen.gfx().setColor(color);
				screen.gfx().fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}

			QuickCustomDrawer drawer = theDrawer.get();
			if (drawer == null)
				return;
			drawer.draw(screen, bounds);
		}

		@Override
		protected Point2D.Float getHit(Point2D.Float point) {
			QuickCustomDrawer drawer = theDrawer.get();
			if (drawer != null && drawer.getOpacity(point) != DrawOpacity.None)
				return point;
			else if (getOpacity() >= 0.0f)
				return point;
			else
				return null;
		}

		@Override
		public DrawOpacity getOpacity(Point2D.Float point) {
			if (!isVisible())
				return DrawOpacity.None;
			QuickCustomDrawer drawer = theDrawer.get();
			DrawOpacity opacity;
			if (drawer != null)
				opacity = drawer.getOpacity(point);
			else
				opacity = DrawOpacity.None;
			if (opacity != DrawOpacity.Full)
				opacity = opacity.or(super.getOpacity(point));
			return opacity;
		}
	}
}
