package org.quick.core.style;

import java.awt.Color;

import org.observe.ObservableValue;
import org.qommons.IterableUtils;
import org.quick.core.prop.QuickProperty;
import org.quick.core.prop.QuickPropertyType;

import com.google.common.reflect.TypeToken;

/** Style attributes that pertain to lighting effects */
public class LightedStyle implements StyleDomain {
	private StyleAttribute<?>[] theAttributes;

	private LightedStyle() {
		theAttributes = new StyleAttribute[0];
	}

	private void register(StyleAttribute<?> attr) {
		theAttributes = org.qommons.ArrayUtils.add(theAttributes, attr);
	}

	private static final LightedStyle instance;

	/** The direction (in degrees clockwise from the top) from which the light source lighting the texture is coming */
	public static final StyleAttribute<Double> lightSource;

	/** The color of the lighting */
	public static final StyleAttribute<Color> lightColor;

	/** The color of the shadowing */
	public static final StyleAttribute<Color> shadowColor;

	/** The maximum amount of shading that should be rendered as a result of lighting */
	public static final StyleAttribute<Double> maxShadingAmount;

	static {
		instance = new LightedStyle();
		lightSource = StyleAttribute.build(instance, "source",
			QuickPropertyType.build("source", TypeToken.of(Double.class))//
				.buildContext(ctx -> {
					ctx.withValue("top", ObservableValue.constant(TypeToken.of(Double.TYPE), 0d));
					ctx.withValue("top-right", ObservableValue.constant(TypeToken.of(Double.TYPE), 45d));
					ctx.withValue("right", ObservableValue.constant(TypeToken.of(Double.TYPE), 90d));
					ctx.withValue("bottom-right", ObservableValue.constant(TypeToken.of(Double.TYPE), 135d));
					ctx.withValue("bottom", ObservableValue.constant(TypeToken.of(Double.TYPE), 180d));
					ctx.withValue("bottom-left", ObservableValue.constant(TypeToken.of(Double.TYPE), 225d));
					ctx.withValue("left", ObservableValue.constant(TypeToken.of(Double.TYPE), 270d));
					ctx.withValue("top-left", ObservableValue.constant(TypeToken.of(Double.TYPE), 315d));
			}).build(), 315d).validate(new QuickProperty.ComparableValidator<>(0d, 360d)).build();
		instance.register(lightSource);
		lightColor = StyleAttribute.build(instance, "color", QuickPropertyType.color, Color.white).build();
		instance.register(lightColor);
		shadowColor = StyleAttribute.build(instance, "shadow", QuickPropertyType.color, Color.black).build();
		instance.register(shadowColor);
		maxShadingAmount = StyleAttribute.build(instance, "max-amount", QuickPropertyType.floating, .5)
			.validate(new QuickProperty.ComparableValidator<>(0d, 1d)).build();
		instance.register(maxShadingAmount);
	}

	/** @return The style domain for all lighting styles */
	public static LightedStyle getDomainInstance() {
		return instance;
	}

	@Override
	public String getName() {
		return "light";
	}

	@Override
	public java.util.Iterator<StyleAttribute<?>> iterator() {
		return IterableUtils.iterator(theAttributes, true);
	}
}
