package org.muis.core;

import java.awt.Graphics2D;
import java.awt.Rectangle;

import org.muis.core.layout.SizePolicy;

/** A MUIS element that serves as a placeholder for text content which may be interspersed with element children in an element. */
public class MuisTextElement extends MuisLeaf
{
	private String theText;

	/** Creates a MUIS text element */
	public MuisTextElement()
	{
	}

	/**
	 * Creates a MUIS text element with text
	 *
	 * @param text The text for the element
	 */
	public MuisTextElement(String text)
	{
		theText = text;
	}

	/** @param text The text content for this element */
	public void setText(String text)
	{
		theText = text;
	}

	/** @return This element's text content */
	public String getText()
	{
		return theText;
	}

	@Override
	public SizePolicy getWSizer(int height)
	{
		// TODO Auto-generated method stub
		return super.getWSizer(height);
	}

	@Override
	public SizePolicy getHSizer(int width)
	{
		// TODO Auto-generated method stub
		return super.getHSizer(width);
	}

	@Override
	public void paintSelf(Graphics2D graphics, Rectangle area)
	{
		// TODO Auto-generated method stub
		super.paintSelf(graphics, area);
	}
}
