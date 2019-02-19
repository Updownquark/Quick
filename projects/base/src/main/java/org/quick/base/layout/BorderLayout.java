package org.quick.base.layout;

import static org.quick.core.layout.LayoutAttributes.*;
import static org.quick.core.layout.LayoutUtils.add;
import static org.quick.core.layout.Orientation.horizontal;
import static org.quick.core.layout.Orientation.vertical;

import java.util.List;

import org.observe.Observable;
import org.quick.core.QuickElement;
import org.quick.core.Rectangle;
import org.quick.core.layout.*;
import org.quick.core.style.LayoutStyle;
import org.quick.core.style.Size;
import org.quick.util.CompoundListener;

/**
 * Lays components out by {@link Region regions}. Containers with this layout may have any number of components in any region except center,
 * which may have zero or one component in it.
 */
public class BorderLayout implements org.quick.core.QuickLayout {
	private final CompoundListener theListener;

	/** Creates a border layout */
	public BorderLayout() {
		theListener = CompoundListener.build()//
			.watchAll(LayoutStyle.margin, LayoutStyle.padding).onEvent(CompoundListener.sizeNeedsChanged)//
			.child(builder -> {
				builder.accept(region).onEvent(CompoundListener.sizeNeedsChanged);
				builder.when(el -> el.getAttribute(region) == Region.left, builder2 -> {
					builder2.acceptAll(width, minWidth, maxWidth, right, minRight, maxRight).onEvent(CompoundListener.sizeNeedsChanged);
				});
				builder.when(el -> el.getAttribute(region) == Region.right, builder2 -> {
					builder2.acceptAll(width, minWidth, maxWidth, left, minLeft, maxLeft).onEvent(CompoundListener.sizeNeedsChanged);
				});
				builder.when(el -> el.getAttribute(region) == Region.top, builder2 -> {
					builder2.acceptAll(height, minHeight, maxHeight, bottom, minBottom, maxBottom)
						.onEvent(CompoundListener.sizeNeedsChanged);
				});
				builder.when(el -> el.getAttribute(region) == Region.bottom, builder2 -> {
					builder2.acceptAll(height, minHeight, maxHeight, top, minTop, maxTop).onEvent(CompoundListener.sizeNeedsChanged);
				});
			})//
			.build();
	}

	@Override
	public void install(QuickElement parent, Observable<?> until) {
		theListener.listen(parent, parent, until);
	}

	@Override
	public SizeGuide getSizer(QuickElement parent, Iterable<? extends QuickElement> children, Orientation orient) {
		return new SizeGuide.GenericSizeGuide() {
			@Override
			public int get(LayoutGuideType type, int crossSize, boolean csMax) {
				Size margin = parent.getStyle().get(LayoutStyle.margin).get();
				Size padding = parent.getStyle().get(LayoutStyle.padding).get();
				if (!children.iterator().hasNext()) {
					if (type == LayoutGuideType.min)
						return 0;
					return margin.evaluate(0) * 2;
				}
				int origCrossSize = crossSize;
				if (type != LayoutGuideType.min)
					crossSize -= margin.evaluate(crossSize) * 2;
				if (crossSize < 0)
					crossSize = 0;
				LayoutSize ret=new LayoutSize();
				LayoutSize cross=new LayoutSize(true);
				LayoutSize temp = new LayoutSize();
				QuickElement center=null;
				for(QuickElement child : children){
					Region childRegion = child.atts().getValue(region, Region.center);
					if(childRegion == Region.center) {
						if(center != null) {
							// Error later, in layout()
						} else
							center = child;
					} else if(childRegion.getOrientation() == orient){
						ret.add(cross);
						cross.clear();
						LayoutUtils.getSize(child, orient, type, 0, crossSize, csMax, ret);
						ret.add(padding);
					} else {
						LayoutUtils.getSize(child, orient, type, crossSize, Integer.MAX_VALUE, true, cross);
						if (crossSize > 0) {
							LayoutUtils.getSize(child, orient.opposite(), type, crossSize, Integer.MAX_VALUE, true, temp.clear());
							crossSize -= temp.add(padding).getTotal(origCrossSize);
							if (crossSize < 0)
								crossSize = 0;
						}
					}
				}
				ret.add(cross);
				if (center != null) {
					ret.add(padding);
					ret.add(padding);
					LayoutUtils.getSize(center, orient, type, 0, crossSize, csMax, ret);
				}
				ret.add(margin);
				ret.add(margin);
				return ret.getTotal();
			}

			@Override
			public int getBaseline(int size) {
				return 0;
			}
		};
	}

	@Override
	public void layout(final QuickElement parent, List<? extends QuickElement> children) {
		final int parentWidth = parent.bounds().getWidth();
		final int parentHeight = parent.bounds().getHeight();
		final Size margin = parent.getStyle().get(LayoutStyle.margin).get();
		final Size padding = parent.getStyle().get(LayoutStyle.padding).get();
		LayoutUtils.LayoutInterpolation<int []> wResult = LayoutUtils.interpolate(new LayoutUtils.LayoutChecker<int []>() {
			@Override
			public int [] getLayoutValue(LayoutGuideType type) {
				int[] ret = new int[children.size()];
				for (int c = 0; c < children.size(); c++)
					ret[c] = LayoutUtils.getSize(children.get(c), horizontal, type, parentWidth, parentHeight, true, null);
				return ret;
			}

			@Override
			public long getSize(int[] layoutValue) {
				return BorderLayout.this.getSize(parentWidth, margin, padding, children, layoutValue);
			}
		}, parent.bounds().getWidth(), LayoutGuideType.min, LayoutGuideType.max);

		final int[] widths = new int[children.size()];
		for (int c = 0; c < widths.length; c++) {
			widths[c] = wResult.lowerValue[c];
			if(wResult.proportion > 0)
				widths[c] = add(widths[c],
					(int) Math.round(wResult.proportion * (wResult.upperValue[c] - wResult.lowerValue[c])));
		}

		LayoutUtils.LayoutInterpolation<int []> hResult = LayoutUtils.interpolate(new LayoutUtils.LayoutChecker<int []>() {
			@Override
			public int [] getLayoutValue(LayoutGuideType type) {
				int[] ret = new int[children.size()];
				for (int c = 0; c < children.size(); c++)
					ret[c] = LayoutUtils.getSize(children.get(c), vertical, type, parentHeight, widths[c], false, null);
				return ret;
			}

			@Override
			public long getSize(int[] layoutValue) {
				return BorderLayout.this.getSize(parentHeight, margin, padding, children, layoutValue);
			}
		}, parent.bounds().getHeight(), LayoutGuideType.min, LayoutGuideType.max);

		int[] heights = new int[children.size()];
		for (int c = 0; c < children.size(); c++) {
			heights[c] = hResult.lowerValue[c];
			if(hResult.proportion > 0)
				heights[c] = add(heights[c],
					(int) Math.round(hResult.proportion * (hResult.upperValue[c] - hResult.lowerValue[c])));
		}

		// Got the sizes. Now put them in place.
		int leftEdge = margin.evaluate(parentWidth);
		int rightEdge = parentWidth - margin.evaluate(parentWidth);
		int topEdge = margin.evaluate(parentHeight);
		int bottomEdge = parentHeight - margin.evaluate(parentHeight);
		int centerIndex = -1;
		Rectangle[] bounds = new Rectangle[children.size()];
		for (int c = 0; c < children.size(); c++) {
			Region childRegion = children.get(c).atts().getValue(region, Region.center);
			if(childRegion == Region.center) {
				if(centerIndex >= 0)
					parent.msg().error("Only one element may be in the center region in a border layout."//
						+ "  Only first center will be layed out.", "element", children.get(c));
				else
					centerIndex = c;
				continue; // Lay out center last
			}
			switch (childRegion) {
			case left:
				bounds[c] = new Rectangle(leftEdge, topEdge, //
					Math.max(0, widths[c]), Math.max(0, bottomEdge - topEdge));
				leftEdge = add(leftEdge, add(bounds[c].width, padding.evaluate(parentWidth)));
				break;
			case right:
				bounds[c] = new Rectangle(rightEdge - widths[c], topEdge, //
					Math.max(0, widths[c]), Math.max(0, bottomEdge - topEdge));
				rightEdge -= add(bounds[c].width, padding.evaluate(parentWidth));
				break;
			case top:
				bounds[c] = new Rectangle(leftEdge, topEdge, //
					Math.max(0, rightEdge - leftEdge), Math.max(0, heights[c]));
				topEdge = add(leftEdge, add(bounds[c].height, padding.evaluate(parentHeight)));
				break;
			case bottom:
				bounds[c] = new Rectangle(leftEdge, bottomEdge - heights[c], //
					Math.max(0, rightEdge - leftEdge), Math.max(0, heights[c]));
				bottomEdge -= add(bounds[c].height, padding.evaluate(parentHeight));
				break;
			case center:
				// Already handled
			}
		}

		if (centerIndex >= 0)
			bounds[centerIndex] = new Rectangle(leftEdge, topEdge, //
				Math.max(0, rightEdge - leftEdge), Math.max(0, bottomEdge - topEdge));

		for (int c = 0; c < children.size(); c++)
			children.get(c).bounds().set(bounds[c], null);
	}

	private long getSize(int parentLength, Size margin, Size padding, List<? extends QuickElement> children, int[] layoutValue) {
		long ret = 0;
		ret += margin.evaluate(parentLength) * 2;
		QuickElement center = null;
		for (int i = 0; i < children.size(); i++) {
			QuickElement child = children.get(i);
			Region childRegion = child.atts().getValue(region, Region.center);
			if (childRegion == Region.center) {
				if (center != null) {
					// Error later, in layout()
				} else {
					center = child;
					ret += layoutValue[i] + padding.evaluate(parentLength) * 2;
				}
			} else if (!childRegion.getOrientation().isVertical())
				ret += layoutValue[i] + padding.evaluate(parentLength);
		}
		return ret;
	}

	@Override
	public String toString() {
		return "border-layout";
	}
}
