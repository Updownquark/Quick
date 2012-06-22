package org.muis.base.widget;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.net.URL;

import org.muis.core.layout.SimpleSizePolicy;
import org.muis.core.layout.SizePolicy;

/** Renders an image */
public class GenericImage extends org.muis.core.LayoutContainer
{
	/** Determines how this image may be resized */
	public enum ImageResizePolicy
	{
		/** This widget will simply use the size set by the layout */
		none,
		/** This widget's size will be locked to the image size */
		lock,
		/** Functions as {@link #lock} if the image has no content and {@link #none} if it has content */
		lockIfEmpty,
		/** Repeats the image as many times as necessary in the container */
		repeat,
		/** Resizes the image to fit the widget's size */
		resize
	}

	public static final org.muis.core.MuisCache.CacheItemType<URL, Image, java.io.IOException> cacheType;

	static
	{
		cacheType = new org.muis.core.MuisCache.CacheItemType<URL, Image, java.io.IOException>() {
			@Override
			public Image generate(org.muis.core.MuisDocument doc, URL key) throws java.io.IOException
			{
				java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
				java.io.InputStream input = null;
				try
				{
					input = new java.io.BufferedInputStream(key.openStream());
					int read = input.read();
					while(read >= 0)
					{
						bytes.write(read);
						read = input.read();
					}
				} catch(java.io.IOException e)
				{
					throw new java.io.IOException("Could not retrieve image at " + key, e);
				} finally
				{
					if(input != null)
						try
						{
							input.close();
						} catch(java.io.IOException e)
						{
						}
				}
				Image ret = java.awt.Toolkit.getDefaultToolkit().createImage(bytes.toByteArray());
				while(ret.getWidth(null) < 0 || ret.getHeight(null) < 0)
					try
					{
						Thread.sleep(10);
					} catch(InterruptedException e)
					{
					}
				return ret;
			}

			@Override
			public int size(Image value)
			{
				int size = value.getHeight(null) * value.getWidth(null) * 4;
				if(size < 0)
					size = 0;
				return size;
			}
		};
	}

	volatile boolean isLoading;

	volatile Throwable theLoadError;

	volatile URL theLocation;

	volatile Image theImage;

	volatile Image theLoadingImage;

	volatile Image theErrorImage;

	private ImageResizePolicy theHResizePolicy;

	private ImageResizePolicy theVResizePolicy;

	boolean isProportionLocked;

	boolean isSizeLocked;

	/** Creates a generic image */
	public GenericImage()
	{
		try
		{
			setAttribute(LAYOUT_ATTR, org.muis.base.layout.SimpleLayout.class);
		} catch(org.muis.core.MuisException e)
		{
			error("Could not set layout attribute as default for image tag", e);
		}
		life().addListener(new LifeCycleListener() {
			@Override
			public void preTransition(String fromStage, String toStage)
			{
				if(fromStage.equals(CoreStage.INIT_SELF))
				{
					org.muis.core.ResourceMapping res = getToolkit().getMappedResource("img-load-icon");
					if(res == null)
						error("No configured img-load-icon", null);
					if(res != null && theLoadingImage == null)
						try
						{
							getDocument().getCache().get(getDocument(), cacheType,
								org.muis.core.MuisUtils.resolveURL(getToolkit().getURI(), res.getLocation()),
								new org.muis.core.MuisCache.ItemReceiver<URL, Image>() {
									@Override
									public void itemGenerated(URL key, Image value)
									{
										if(theLoadingImage == null)
											theLoadingImage = value;
									}

									@Override
									public void errorOccurred(URL key, Throwable exception)
									{
										error("Could not load image loading icon", exception);
									}
								});
						} catch(org.muis.core.MuisException | java.io.IOException e)
						{
							error("Could not retrieve image loading icon", e);
						}
					res = getToolkit().getMappedResource("img-load-failed-icon");
					if(res == null)
						error("No configured img-load-failed-icon", null);
					if(res != null && theLoadingImage == null)
						try
						{
							getDocument().getCache().get(getDocument(), cacheType,
								org.muis.core.MuisUtils.resolveURL(getToolkit().getURI(), res.getLocation()),
								new org.muis.core.MuisCache.ItemReceiver<URL, Image>() {
									@Override
									public void itemGenerated(URL key, Image value)
									{
										if(theLoadingImage == null)
											theLoadingImage = value;
									}

									@Override
									public void errorOccurred(URL key, Throwable exception)
									{
										error("Could not load image load failed icon", exception);
									}
								});
						} catch(org.muis.core.MuisException | java.io.IOException e)
						{
							error("Could not retrieve image load failed icon", e);
						}
				}
			}

			@Override
			public void postTransition(String oldStage, String newStage)
			{
			}
		});
		theHResizePolicy = ImageResizePolicy.lockIfEmpty;
		theVResizePolicy = ImageResizePolicy.lockIfEmpty;
	}

	public void setImageLocation(URL location)
	{
		theLocation = location;
		isLoading = true;
		theLoadError = null;
		theImage = null;
		try
		{
			theImage = getDocument().getCache().get(getDocument(), cacheType, location,
				new org.muis.core.MuisCache.ItemReceiver<URL, java.awt.Image>() {
					@Override
					public void itemGenerated(URL key, Image value)
					{
						if(!key.equals(theLocation))
							return;
						theImage = value;
						isLoading = false;
						if(getParent() != null)
							getParent().relayout(false);
						repaint(null, false);
					}

					@Override
					public void errorOccurred(URL key, Throwable exception)
					{
						if(!key.equals(theLocation))
							return;
						theLoadError = exception;
						isLoading = false;
					}
				});
		} catch(java.io.IOException e)
		{
		}
	}

	public void setImage(Image image)
	{
		theLocation = null;
		theImage = image;
		if(getParent() != null)
			getParent().relayout(false);
		repaint(null, false);
	}

	public void setLoadingImage(Image image)
	{
		theLoadingImage = image;
		if(isLoading)
		{
			if(getParent() != null)
				getParent().relayout(false);
			repaint(null, false);
		}
	}

	public void setErrorImage(Image image)
	{
		theErrorImage = image;
		if(theLoadError != null)
		{
			if(getParent() != null)
				getParent().relayout(false);
			repaint(null, false);
		}
	}

	public ImageResizePolicy getHorizontalResizePolicy()
	{
		return theHResizePolicy;
	}

	public void setHorizontalResizePolicy(ImageResizePolicy policy)
	{
		if(policy == null)
			throw new NullPointerException();
		if(theHResizePolicy == policy)
			return;
		theHResizePolicy = policy;
		if(getParent() != null)
			getParent().relayout(false);
		repaint(null, false);
	}

	public ImageResizePolicy getVerticalResizePolicy()
	{
		return theVResizePolicy;
	}

	public void setVerticalResizePolicy(ImageResizePolicy policy)
	{
		if(policy == null)
			throw new NullPointerException();
		if(theVResizePolicy == policy)
			return;
		theVResizePolicy = policy;
		if(getParent() != null)
			getParent().relayout(false);
		repaint(null, false);
	}

	@Override
	public SizePolicy getWSizer(int height)
	{
		Image img = getDisplayedImage();
		int w, h;
		if(img != null)
		{
			w = img.getWidth(null);
			h = img.getHeight(null);
		}
		else
			return super.getWSizer(height);

		switch (theHResizePolicy)
		{
		case none:
			return super.getWSizer(height);
		case lock:
			return new SimpleSizePolicy(w, w, w, 0);
		case lockIfEmpty:
			if(getChildCount() == 0)
				return new SimpleSizePolicy(w, w, w, 0);
			else
				return super.getWSizer(height);
		case repeat:
			return super.getWSizer(height);
		case resize:
			if(height < 0)
				return new SimpleSizePolicy(0, w, Integer.MAX_VALUE, 0);
			else
			{
				w = w * height / h;
				if(isProportionLocked)
					return new SimpleSizePolicy(w, w, w, 0);
				else
					return new SimpleSizePolicy(0, w, Integer.MAX_VALUE, 0);
			}
		}
		return super.getWSizer(height);
	}

	@Override
	public SizePolicy getHSizer(int width)
	{
		Image img = getDisplayedImage();
		int w, h;
		if(img != null)
		{
			w = img.getWidth(null);
			h = img.getHeight(null);
		}
		else
			return super.getHSizer(width);

		switch (theVResizePolicy)
		{
		case none:
			return super.getHSizer(width);
		case lock:
			return new SimpleSizePolicy(h, h, h, 0);
		case lockIfEmpty:
			if(getChildCount() == 0)
				return new SimpleSizePolicy(h, h, h, 0);
			else
				return super.getHSizer(width);
		case repeat:
			return super.getHSizer(width);
		case resize:
			if(width < 0)
				return new SimpleSizePolicy(0, h, Integer.MAX_VALUE, 0);
			else
			{
				h = h * width / w;
				if(isProportionLocked)
					return new SimpleSizePolicy(h, h, h, 0);
				else
					return new SimpleSizePolicy(0, h, Integer.MAX_VALUE, 0);
			}
		}
		return super.getHSizer(width);
	}

	public Image getDisplayedImage()
	{
		if(isLoading)
			return theLoadingImage;
		else if(theLoadError != null)
			return theErrorImage;
		else
			return theImage;
	}

	@Override
	public void paintSelf(Graphics2D graphics, Rectangle area)
	{
		super.paintSelf(graphics, area);
		Image img = getDisplayedImage();
		if(img == null)
			return;
		int h = img.getHeight(null);
		switch (theVResizePolicy)
		{
		case none:
		case lock:
		case lockIfEmpty:
			drawImage(graphics, img, 0, h, 0, h, area);
			break;
		case repeat:
			for(int y = 0; y < getHeight(); y += h)
				drawImage(graphics, img, y, y + h, 0, h, area);
			break;
		case resize:
			drawImage(graphics, img, 0, getHeight(), 0, h, area);
			break;
		}
	}

	private void drawImage(Graphics2D graphics, Image img, int gfxY1, int gfxY2, int imgY1, int imgY2, Rectangle area)
	{
		int w = img.getWidth(null);
		switch (theHResizePolicy)
		{
		case none:
		case lock:
		case lockIfEmpty:
			drawImage(graphics, img, 0, gfxY1, w, gfxY2, 0, imgY1, w, imgY2, area);
			break;
		case repeat:
			for(int x = 0; x < getWidth(); x += w)
				drawImage(graphics, img, x, gfxY1, x + w, gfxY2, 0, imgY1, w, imgY2, area);
			break;
		case resize:
			drawImage(graphics, img, 0, gfxY1, getWidth(), gfxY2, 0, imgY1, w, imgY2, area);
			break;
		}
	}

	private void drawImage(Graphics2D graphics, Image img, int gfxX1, int gfxY1, int gfxX2, int gfxY2, int imgX1, int imgY1, int imgX2,
		int imgY2, Rectangle area)
	{
		if(area != null && (area.x >= gfxX2 || area.x + area.width <= gfxX1 || area.y >= gfxY2 || area.y + area.height <= gfxY1))
			return;
		graphics.drawImage(img, gfxX1, gfxY1, gfxX2, gfxY2, imgX1, imgY1, imgX2, imgY2, null);
	}
}
