package org.quick.widget.base;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import org.quick.base.widget.Transform;
import org.quick.core.Point;
import org.quick.core.Rectangle;
import org.quick.core.layout.LayoutGuideType;
import org.quick.core.layout.Orientation;
import org.quick.widget.core.QuickElementCapture;
import org.quick.widget.core.QuickWidget;
import org.quick.widget.core.QuickWidgetDocument;
import org.quick.widget.core.layout.SizeGuide;

public class TransformWidget extends SimpleContainerWidget {
	public TransformWidget(QuickWidgetDocument doc, Transform element, QuickWidget parent) {
		super(doc, element, parent);
		getElement().life().runWhen(() -> {
			getElement().atts().get(Transform.flip).changes().act(event -> {
				sizeNeedsChanged();
				relayout(false);
			});
			getElement().atts().get(Transform.rotate).changes().act(event -> {
				if (event.getNewValue() % 90 != 0) {
					getElement().msg().warn("The " + Transform.rotate.getName() + " attribute currently supports only multiples of 90",
						"value", event.getNewValue());
					try {
						getElement().atts().get(Transform.rotate).set(Math.round(event.getNewValue() / 90) * 90.0, event);
					} catch (IllegalArgumentException e) {
						throw new IllegalStateException(e);
					}
					return;
				}
				sizeNeedsChanged();
				relayout(false);
			});
		}, org.quick.core.QuickConstants.CoreStage.INIT_SELF.toString(), 1);
	}

	@Override
	public Transform getElement() {
		return (Transform) super.getElement();
	}

	public BlockWidget getContents() {
		return (BlockWidget) getChild(getElement().getContents());
	}

	@Override
	public void doLayout() {
		BlockWidget contents = getContents();
		double rotation = normalize(getElement().atts().get(Transform.rotate).get());
		Double s = getElement().atts().get(Transform.scale).get();
		Double sx = getElement().atts().get(Transform.scaleX).get();
		Double sy = getElement().atts().get(Transform.scaleY).get();
		double sxv = sx != null ? sx : (s != null ? s : 1);
		double syv = sy != null ? sx : (s != null ? s : 1);

		int pw = bounds().getWidth();
		int ph = bounds().getHeight();
		int x, y;
		int cw, ch;
		if (rotation == 0.0) {
			x = y = 0;
			cw = pw;
			ch = ph;
		} else if (rotation == 90.0) {
			x = pw;
			y = 0;
			cw = ph;
			ch = pw;
		} else if (rotation == 180.0) {
			x = pw;
			y = ph;
			cw = pw;
			ch = ph;
		} else if (rotation == 270.0) {
			x = 0;
			y = ph;
			cw = ph;
			ch = pw;
		} else {
			x = y = cw = ch = 0;
			// TODO
		}

		if (sxv != 1.0)
			cw = (int) Math.round(cw * sxv);
		if (syv != 1.0)
			ch = (int) Math.round(ch * syv);

		contents.bounds().setBounds(x, y, cw, ch);

		super.doLayout();
	}

	@Override
	public SizeGuide getSizer(Orientation orientation) {
		BlockWidget contents = getContents();
		Double rotation = getElement().atts().get(Transform.rotate).get();
		Double s = getElement().atts().get(Transform.scale).get();
		Double sx = getElement().atts().get(Transform.scaleX).get();
		Double sy = getElement().atts().get(Transform.scaleY).get();
		double sxv = sx != null ? sx : (s != null ? s : 1);
		double syv = sy != null ? sx : (s != null ? s : 1);
		return new TransformingSizeGuide(normalize(rotation), sxv, syv, contents.getSizer(Orientation.horizontal),
			contents.getSizer(Orientation.vertical), orientation);
	}

	@Override
	protected QuickElementCapture createCapture(int x, int y, int z, int w, int h) {
		Double rotation = getElement().atts().get(Transform.rotate).get();
		Double s = getElement().atts().get(Transform.scale).get();
		Double sx = getElement().atts().get(Transform.scaleX).get();
		Double sy = getElement().atts().get(Transform.scaleY).get();
		double sxv = sx != null ? sx : (s != null ? s : 1);
		double syv = sy != null ? sx : (s != null ? s : 1);
		return new QuickElementCapture(null, this,
			new TransformTransformer(getElement().atts().get(Transform.flip).get(), normalize(rotation), sxv, syv), x, y, z, w, h);
	}

	private static double normalize(Double rotation) {
		if (rotation == null)
			return 0.0;
		double ret = rotation;
		if (ret < 0) {
			ret = -ret;
			ret %= 360.0;
			ret = 360.0 - ret;
		} else if (ret >= 360.0)
			ret %= 360;
		return ret;
	}

	private static Point rotate(Point p, int w, int h, double rotation) {
		int x = p.x;
		int y = p.y;
		if (rotation == 0) {
			return p;
		} else if (rotation == 90.0) {
			final int oldX = p.x, oldY = p.y;
			x = oldY;
			y = h - oldX;
		} else if (rotation == 180.0) {
			final int oldX = p.x, oldY = p.y;
			x = w - oldX;
			y = h - oldY;
		} else if (rotation == 270.0) {
			final int oldX = p.x, oldY = p.y;
			x = oldY;
			y = h - oldX;
		} else {
			double radians = -rotation / 180.0 * Math.PI;
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			final int oldX = p.x, oldY = p.y;
			x = (int) Math.round(cos * oldX - sin * oldY);
			y = (int) Math.round(sin * oldX + cos * oldY);
		}
		return new Point(x, y);
	}

	@Override
	public QuickElementCapture[] paintChildren(Graphics2D graphics, Rectangle area) {
		BlockWidget content = getContents();
		Orientation reflection = getElement().atts().get(Transform.flip).get();
		double rotation = normalize(getElement().atts().get(Transform.rotate).get());
		Double s = getElement().atts().get(Transform.scale).get();
		Double sx = getElement().atts().get(Transform.scaleX).get();
		Double sy = getElement().atts().get(Transform.scaleY).get();
		double sxv = sx != null ? sx : (s != null ? s : 1);
		double syv = sy != null ? sx : (s != null ? s : 1);
		AffineTransform tx = new AffineTransform();
		boolean isTransformed = false;
		if (reflection != null) {
			isTransformed = true;
			switch (reflection) {
			case horizontal:
				// Flip the contents horizontally
				tx.scale(-1, 1);
				tx.translate(-content.bounds().getWidth(), 0);
				break;
			case vertical:
				// Flip the contents vertically
				tx.scale(1, -1);
				tx.translate(0, -content.bounds().getHeight());
				graphics.transform(tx);
				break;
			}
		}
		if (sxv != 1.0 || syv != 1.0) {
			isTransformed = true;
			tx.scale(sxv, syv);
		}
		if (rotation != 0.0) {
			isTransformed = true;
			tx.rotate(-(rotation / 180 * Math.PI));
		}

		AffineTransform pre = null;
		if (isTransformed) {
			pre = graphics.getTransform();
			graphics.transform(tx);
		}
		try {
			return super.paintChildren(graphics, area);
		} finally {
			if (isTransformed)
				graphics.setTransform(pre);
		}
	}

	/** Transform's content's size guides */
	public static class TransformingSizeGuide implements SizeGuide {
		private final double theRotation;
		private final double theScaleX;
		private final double theScaleY;

		private final SizeGuide theWSizer;
		private final SizeGuide theHSizer;

		private final Orientation theOrientation;

		/**
		 * @param rotation The rotation, in clockwise degrees, for this transformer to apply
		 * @param sX The scale factor in the x-direction
		 * @param sY The scale factor in the y-direction
		 * @param wSizer The width sizer to transform
		 * @param hSizer The height sizer to transform
		 * @param orientation The orientation for this size guide
		 */
		public TransformingSizeGuide(double rotation, double sX, double sY, SizeGuide wSizer, SizeGuide hSizer, Orientation orientation) {
			theRotation = rotation;
			theScaleX = sX;
			theScaleY = sY;
			theWSizer = wSizer;
			theHSizer = hSizer;
			theOrientation = orientation;
		}

		/** @return The rotation (in clockwise degrees) that this transformer applies */
		public double getRotation() {
			return theRotation;
		}

		/** @return The scale factor in the x-direction that this transformer applies */
		public double getScaleX() {
			return theScaleX;
		}

		/** @return The scale factor in the y-direction that this transformer applies */
		public double getScaleY() {
			return theScaleY;
		}

		@Override
		public int getMin(int crossSize, boolean csMax) {
			return get(LayoutGuideType.min, crossSize, csMax);
		}

		@Override
		public int getMinPreferred(int crossSize, boolean csMax) {
			return get(LayoutGuideType.minPref, crossSize, csMax);
		}

		@Override
		public int getPreferred(int crossSize, boolean csMax) {
			return get(LayoutGuideType.pref, crossSize, csMax);
		}

		@Override
		public int getMaxPreferred(int crossSize, boolean csMax) {
			return get(LayoutGuideType.maxPref, crossSize, csMax);
		}

		@Override
		public int getMax(int crossSize, boolean csMax) {
			return get(LayoutGuideType.max, crossSize, csMax);
		}

		@Override
		public int getBaseline(int size) {
			return 0;
		}

		@Override
		public int get(LayoutGuideType type, int crossSize, boolean csMax) {
			boolean vertical = theOrientation == Orientation.vertical;
			int ret;
			if (theRotation == 0.0 || theRotation == 180.0) {
				ret = (vertical ? theHSizer : theWSizer).get(type, crossSize, csMax);
				if ((vertical ? theScaleY : theScaleX) != 1.0)
					ret *= vertical ? theScaleY : theScaleX;
			} else if (theRotation == 90.0 || theRotation == 270.0) {
				ret = (vertical ? theWSizer : theHSizer).get(type, crossSize, csMax);
				if ((vertical ? theScaleX : theScaleY) != 1.0)
					ret *= vertical ? theScaleX : theScaleY;
			} else {
				// TODO
				ret = 0;
			}
			return ret;
		}
	}

	/** Applies a transform to {@link QuickElementCapture}s */
	public static class TransformTransformer implements org.quick.widget.core.QuickElementCapture.Transformer {
		private final Orientation theReflection;
		private final double theRotation;
		private final double theScaleX;
		private final double theScaleY;

		/**
		 * @param reflection The reflection for this transformer to apply. May be null.
		 * @param rotation The rotation, in clockwise degrees, for this transformer to apply
		 * @param sX The scale factor in the x-direction
		 * @param sY The scale factor in the y-direction
		 */
		public TransformTransformer(Orientation reflection, double rotation, double sX, double sY) {
			theReflection = reflection;
			theRotation = rotation;
			theScaleX = sX;
			theScaleY = sY;
		}

		/** @return The orientation that this transformer applies. May be null. */
		public Orientation getReflection() {
			return theReflection;
		}

		/** @return The rotation (in clockwise degrees) that this transformer applies */
		public double getRotation() {
			return theRotation;
		}

		/** @return The scale factor in the x-direction that this transformer applies */
		public double getScaleX() {
			return theScaleX;
		}

		/** @return The scale factor in the y-direction that this transformer applies */
		public double getScaleY() {
			return theScaleY;
		}

		@Override
		public Point getChildPosition(QuickElementCapture parent, QuickElementCapture child, Point pos) {
			// Order is translate, rotate, scale, reflect
			int x = pos.x - child.getX();
			int y = pos.y - child.getY();

			pos = rotate(pos, child.getWidth(), child.getHeight(), theRotation);

			if (theScaleX != 1.0)
				x = (int) Math.round(x / theScaleX);
			if (theScaleY != 1.0)
				y = (int) Math.round(y / theScaleY);

			if (x < 0 || x >= child.getWidth() || y < 0 || y >= child.getHeight())
				return null;

			if (theReflection == Orientation.horizontal) {
				x = child.getWidth() - x;
			} else if (theReflection == Orientation.vertical) {
				y = child.getHeight() - y;
			}

			return new Point(x, y);
		}

		@Override
		public Point getParentPosition(QuickElementCapture parent, QuickElementCapture child, Point pos) {
			// Order is reflect, scale, rotate, translate
			int x = pos.x;
			int y = pos.y;

			if (theReflection == Orientation.horizontal) {
				x = child.getWidth() - x;
			} else if (theReflection == Orientation.vertical) {
				y = child.getHeight() - y;
			}

			if (theScaleX != 1.0)
				x = (int) Math.round(x * theScaleX);
			if (theScaleY != 1.0)
				y = (int) Math.round(y * theScaleY);

			pos = rotate(pos, child.getWidth(), child.getHeight(), -theRotation);

			x += child.getX();
			y += child.getY();

			return new Point(x, y);
		}
	}
}
