package org.observe.quick.swing;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import org.qommons.Transaction;

public interface QuickDrawScreen extends Transaction {
	Graphics2D gfx();

	boolean isTransformed();

	Point transformToRoot(float x, float y);

	float getWidth();

	float getHeight();

	int containsX(float x);

	int containsY(float y);

	/**
	 * Transforms an x-coordinate in the current coordinate system to the corresponding x-coordinate of a pixel on the graphics display
	 *
	 * @param x The x-coordinate to transform
	 * @return The transformed x-coordinate
	 */
	float transformX(float x);

	/**
	 * Transforms an x-coordinate of a pixel on the graphics display to the corresponding x-coordinate in the current coordinate system
	 *
	 * @param x The x-coordinate to transform
	 * @return The transformed x-coordinate
	 */
	float inverseTransformX(float x);

	/**
	 * Transforms a y-coordinate in the current coordinate system to the corresponding y-coordinate of a pixel on the graphics display
	 *
	 * @param y The y-coordinate to transform
	 * @return The transformed y-coordinate
	 */
	float transformY(float y);

	/**
	 * Transforms a y-coordinate of a pixel on the graphics display to the corresponding y-coordinate in the current coordinate system
	 *
	 * @param y The y-coordinate to transform
	 * @return The transformed y-coordinate
	 */
	float inverseTransformY(float y);

	Point tx(float x, float y);

	int[][] tx(float[][] points);

	QuickDrawScreen transform(float translateX, float translateY, float scaleX, float scaleY);

	QuickDrawScreen subScreen(float x, float y, float width, float height);

	Graphics2D getTransformedGraphics();

	public static abstract class AbstractScreen implements QuickDrawScreen {
		private final Graphics2D theGraphics;
		private final float theWidth;
		private final float theHeight;
		private final Transaction onClose;
		protected final Point thePoint;

		protected AbstractScreen(Graphics2D graphics, float width, float height, Transaction onClose) {
			theGraphics = graphics;
			theWidth = width;
			theHeight = height;
			this.onClose = onClose;
			thePoint = new Point();
		}

		@Override
		public Point transformToRoot(float x, float y) {
			AffineTransform txform = gfx().getTransform();
			if (txform == null || txform.isIdentity()) {
				thePoint.x = (int) x;
				thePoint.y = (int) y;
			} else {
				Point2D.Double pt = new Point2D.Double();
				pt.setLocation(x, y);
				txform.transform(pt, pt);
				thePoint.x = (int) pt.x;
				thePoint.y = (int) pt.y;
			}
			return thePoint;
		}

		@Override
		public void close() {
			onClose.close();
		}

		@Override
		public Graphics2D gfx() {
			return theGraphics;
		}

		@Override
		public float getWidth() {
			return theWidth;
		}

		@Override
		public float getHeight() {
			return theHeight;
		}

		@Override
		public Point tx(float x, float y) {
			thePoint.x = (int) transformX(x);
			thePoint.y = (int) transformY(y);
			return thePoint;
		}

		@Override
		public int[][] tx(float[][] points) {
			int[][] transformed = new int[2][points[0].length];
			for (int p = 0; p < points[0].length; p++) {
				transformed[0][p] = (int) transformX(points[0][p]);
				transformed[1][p] = (int) transformY(points[1][p]);
			}
			return transformed;
		}
	}

	public static class SimpleScreen extends AbstractScreen {
		public SimpleScreen(Graphics2D graphics, float width, float height, Transaction onClose) {
			super(graphics, width, height, onClose);
		}

		@Override
		public boolean isTransformed() {
			AffineTransform txform = gfx().getTransform();
			return txform != null && !txform.isIdentity();
		}

		@Override
		public Point transformToRoot(float x, float y) {
			AffineTransform txform = gfx().getTransform();
			if (txform == null || txform.isIdentity()) {
				thePoint.x = (int) x;
				thePoint.y = (int) y;
			} else {
				Point2D.Double pt = new Point2D.Double();
				pt.setLocation(x, y);
				txform.transform(pt, pt);
				thePoint.x = (int) pt.x;
				thePoint.y = (int) pt.y;
			}
			return thePoint;
		}

		@Override
		public int containsX(float x) {
			if (x < 0)
				return -1;
			else if (x >= getWidth())
				return 1;
			else
				return 0;
		}

		@Override
		public int containsY(float y) {
			if (y < 0)
				return -1;
			else if (y >= getHeight())
				return 1;
			else
				return 0;
		}

		@Override
		public float transformX(float x) {
			return x;
		}

		@Override
		public float inverseTransformX(float x) {
			return x;
		}

		@Override
		public float transformY(float y) {
			return y;
		}

		@Override
		public float inverseTransformY(float y) {
			return y;
		}

		@Override
		public QuickDrawScreen transform(float translateX, float translateY, float scaleX, float scaleY) {
			if (scaleX == 1.0f && scaleY == 1.0f) {
				if (translateX == 0 && translateY == 0)
					return this;
				else
					return new TranslatedScreen(gfx(), getWidth(), getHeight(), translateX, translateY, Transaction.NONE);
			} else
				return new ScaledScreen(gfx(), getWidth() / Math.abs(scaleX), getHeight() / Math.abs(scaleY), translateX, translateY,
					scaleX, scaleY, Transaction.NONE);
		}

		@Override
		public QuickDrawScreen subScreen(float x, float y, float width, float height) {
			Shape prevClip = gfx().getClip();
			if (x + width > getWidth())
				width = getWidth() - x;
			if (y + height > getHeight())
				height = getHeight() - y;
			if (width < 0 || height < 0)
				return null;
			gfx().setClip((int) x, (int) y, (int) width, (int) height);
			gfx().translate(x, y);
			return new SimpleScreen(gfx(), width, height, () -> {
				gfx().translate(-x, -y);
				gfx().setClip(prevClip);
			});
		}

		@Override
		public Graphics2D getTransformedGraphics() {
			return gfx();
		}

		@Override
		public String toString() {
			return "SimpleScreen[" + getWidth() + "x" + getHeight() + "]";
		}
	}

	static class TranslatedScreen extends AbstractScreen {
		private final float theOffsetX;
		private final float theOffsetY;

		public TranslatedScreen(Graphics2D graphics, float width, float height, float offsetX, float offsetY, Transaction onClose) {
			super(graphics, width, height, onClose);
			theOffsetX = offsetX;
			theOffsetY = offsetY;
		}

		@Override
		public boolean isTransformed() {
			return true;
		}

		@Override
		public Point transformToRoot(float x, float y) {
			return super.transformToRoot(x + theOffsetX, y + theOffsetY);
		}

		@Override
		public int containsX(float x) {
			x += theOffsetX;
			if (x < 0)
				return -1;
			else if (x >= getWidth())
				return 1;
			else
				return 0;
		}

		@Override
		public int containsY(float y) {
			y += theOffsetY;
			if (y < 0)
				return -1;
			else if (y >= getHeight())
				return 1;
			else
				return 0;
		}

		@Override
		public float transformX(float x) {
			return x + theOffsetX;
		}

		@Override
		public float inverseTransformX(float x) {
			return x - theOffsetX;
		}

		@Override
		public float transformY(float y) {
			return y + theOffsetY;
		}

		@Override
		public float inverseTransformY(float y) {
			return y - theOffsetY;
		}

		@Override
		public QuickDrawScreen transform(float translateX, float translateY, float scaleX, float scaleY) {
			if (scaleX == 1.0f && scaleY == 1.0f) {
				if (translateX == 1.0f && translateY == 1.0f)
					return this;
				else
					return new TranslatedScreen(gfx(), getWidth(), getHeight(), theOffsetX + translateX, theOffsetY + translateY,
						Transaction.NONE);
			} else
				return new ScaledScreen(gfx(), getWidth() / Math.abs(scaleX), getHeight() / Math.abs(scaleY), theOffsetX + translateX,
					theOffsetY + translateY, scaleX, scaleY, Transaction.NONE);
		}

		@Override
		public QuickDrawScreen subScreen(float x, float y, float width, float height) {
			Shape prevClip = gfx().getClip();
			if (theOffsetX + x + width > getWidth())
				width = getWidth() - x - theOffsetX;
			if (theOffsetY + y + height > getHeight())
				height = getHeight() - y - theOffsetY;
			if (width < 0 || height < 0)
				return null;
			float translateX = theOffsetX + x;
			float translateY = theOffsetY + y;
			gfx().setClip((int) translateX, (int) translateY, (int) width, (int) height);
			gfx().translate(translateX, translateY);
			return new SimpleScreen(gfx(), width, height, () -> {
				gfx().translate(-translateX, -translateY);
				gfx().setClip(prevClip);
			});
		}

		@Override
		public Graphics2D getTransformedGraphics() {
			Graphics2D copy = (Graphics2D) gfx().create();
			copy.translate(theOffsetX, theOffsetY);
			return copy;
		}

		@Override
		public String toString() {
			return "TranslatedScreen[" + theOffsetX + ", " + theOffsetY + ", " + getWidth() + "x" + getHeight() + "]";
		}
	}

	public static class ScaledScreen extends AbstractScreen {
		private final float theOffsetX;
		private final float theOffsetY;
		private final float theScaleX;
		private final float theScaleY;
		private final float theScaledOffsetX;
		private final float theScaledOffsetY;

		public ScaledScreen(Graphics2D graphics, float width, float height, float offsetX, float offsetY, float scaleX, float scaleY,
			Transaction onClose) {
			super(graphics, width, height, onClose);
			theOffsetX = offsetX;
			theOffsetY = offsetY;
			theScaleX = scaleX;
			theScaleY = scaleY;
			theScaledOffsetX = theOffsetX / theScaleX;
			theScaledOffsetY = theOffsetY / theScaleY;
		}

		@Override
		public boolean isTransformed() {
			return true;
		}

		@Override
		public Point transformToRoot(float x, float y) {
			return super.transformToRoot(x * theScaleX + theOffsetX, y * theScaleY + theOffsetY);
		}

		@Override
		public int containsX(float x) {
			if (theScaleX < 0) {
				x = -(x + theScaledOffsetX);
				if (x < 0)
					return 1;
				else if (x >= getWidth())
					return -1;
				else
					return 0;
			} else {
				x += theScaledOffsetX;
				if (x < 0)
					return -1;
				else if (x >= getWidth())
					return 1;
				else
					return 0;
			}
		}

		@Override
		public int containsY(float y) {
			if (theScaleY < 0) {
				y = -(y + theScaledOffsetY);
				if (y < 0)
					return 1;
				else if (y >= getHeight())
					return -1;
				else
					return 0;
			} else {
				y += theScaledOffsetY;
				if (y < 0)
					return -1;
				else if (y >= getHeight())
					return 1;
				else
					return 0;
			}
		}

		@Override
		public float transformX(float x) {
			return x * theScaleX + theOffsetX;
		}

		@Override
		public float inverseTransformX(float x) {
			return (x - theOffsetX) / theScaleX;
		}

		@Override
		public float transformY(float y) {
			return y * theScaleY + theOffsetY;
		}

		@Override
		public float inverseTransformY(float y) {
			return (y - theOffsetY) / theScaleY;
		}

		@Override
		public QuickDrawScreen transform(float translateX, float translateY, float scaleX, float scaleY) {
			return new ScaledScreen(gfx(), getWidth() / Math.abs(scaleX), getHeight() / Math.abs(scaleY), //
				translateX * theScaleX + theOffsetX, translateY * theScaleY + theOffsetY, //
				theScaleX * scaleX, theScaleY * scaleY, Transaction.NONE);
		}

		@Override
		public QuickDrawScreen subScreen(float x, float y, float width, float height) {
			Shape prevClip = gfx().getClip();
			if (theOffsetX + x + width > getWidth())
				width = getWidth() - x;
			if (theOffsetY + y + height > getHeight())
				height = getHeight() - y;
			if (width < 0 || height < 0)
				return null;
			int clipMinX, clipMinY, clipMaxX, clipMaxY;
			tx(x, y);
			clipMinX = thePoint.x;
			clipMinY = thePoint.y;
			tx(x + width, y + height);
			clipMaxX = thePoint.x;
			clipMaxY = thePoint.y;
			if (clipMinX > clipMaxX) {
				int temp = clipMinX;
				clipMinX = clipMaxX;
				clipMaxX = temp;
			}
			if (clipMinY > clipMaxY) {
				int temp = clipMinY;
				clipMinY = clipMaxY;
				clipMaxY = temp;
			}
			float translateX = theOffsetX + x * theScaleX;
			float translateY = theOffsetY + y * theScaleY;
			gfx().setClip(clipMinX, clipMinY, clipMaxX - clipMinX, clipMaxY - clipMinY);
			gfx().translate(translateX, translateY);
			return new ScaledScreen(gfx(), width, height, 0f, 0f, theScaleX, theScaleY, () -> {
				gfx().translate(-translateX, -translateY);
				gfx().setClip(prevClip);
			});
		}

		@Override
		public Graphics2D getTransformedGraphics() {
			Graphics2D copy = (Graphics2D) gfx().create();
			copy.translate(theOffsetX, theOffsetY);
			copy.scale(theScaleX, theScaleY);
			return copy;
		}

		@Override
		public String toString() {
			return "ScaledScreen[" + theOffsetX + ", " + theOffsetY + ", " + theScaleX + "x" + theScaleY + ", " + getWidth() + "x"
				+ getHeight() + "]";
		}
	}
}