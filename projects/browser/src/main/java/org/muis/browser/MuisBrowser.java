/* Created Mar 23, 2009 by Andrew */
package org.muis.browser;

/** A browser that renders MUIS documents */
public class MuisBrowser extends javax.swing.JPanel
{
	javax.swing.JTextField theAddressBar;

	private MuisContentPane theContentPane;

	private boolean theDataLock;

	/** Creates a MUIS browser */
	public MuisBrowser()
	{
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		theAddressBar = new javax.swing.JTextField(250);
		add(theAddressBar);
		theAddressBar.setMaximumSize(theAddressBar.getPreferredSize());
		theContentPane = new MuisContentPane();
		add(theContentPane);
		theContentPane.setPreferredSize(new java.awt.Dimension(800, 600));
		theAddressBar.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyReleased(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyCode() != java.awt.event.KeyEvent.VK_ENTER)
					return;
				goToAddress(theAddressBar.getText());
			}
		});
	}

	/** @param address The address of the document to get */
	public void goToAddress(String address)
	{
		if(!theDataLock && !theAddressBar.getText().equals(address))
		{
			theDataLock = true;
			try
			{
				theAddressBar.setText(address);
			} finally
			{
				theDataLock = false;
			}
		}
		java.net.URL url;
		try
		{
			url = new java.net.URL(address);
		} catch(java.net.MalformedURLException e)
		{
			throw new IllegalArgumentException(address + " is not a valid URL", e);
		}
		org.muis.core.parser.MuisParser muisParser = new org.muis.core.parser.MuisDomParser();
		org.muis.core.MuisDocument muisDoc;
		try
		{
			muisDoc = muisParser.parseDocument(url, new java.io.InputStreamReader(url.openStream()),
				new org.muis.core.MuisDocument.GraphicsGetter() {
					@Override
					public java.awt.Graphics2D getGraphics()
					{
						return (java.awt.Graphics2D) getContentPane().getGraphics();
					}
				});
		} catch(java.io.IOException e)
		{
			throw new IllegalArgumentException("Could not access address " + address, e);
		} catch(org.muis.core.parser.MuisParseException e)
		{
			throw new IllegalArgumentException("Could not parse XML document at " + address, e);
		}
		muisDoc.postCreate();
		theContentPane.setContent(muisDoc);
		repaint();
		for(org.muis.core.MuisMessage msg : muisDoc.getAllMessages())
			switch (msg.type)
			{
			case FATAL:
			case ERROR:
			case WARNING:
				System.err.println(msg.type + (msg.element == null ? "" : " on " + msg.element.getTagName()) + ": " + msg.text);
				if(msg.exception != null)
					msg.exception.printStackTrace();
				break;
			case INFO:
				System.out.println(msg.type + (msg.element == null ? "" : " on " + msg.element.getTagName()) + ": " + msg.text);
				if(msg.exception != null)
					msg.exception.printStackTrace(System.out);
				break;
			}
	}

	/** @return The content pane that renders content pointed to from this browser */
	public MuisContentPane getContentPane()
	{
		return theContentPane;
	}

	/**
	 * Starts up a MuisBrowser
	 *
	 * @param args Command-line arguments. If any are supplied, the first one is used as the initial address of the MUIS document to
	 *            display.
	 */
	public static void main(String [] args)
	{
		MuisBrowser browser = new MuisBrowser();
		javax.swing.JFrame frame = new javax.swing.JFrame("MUIS Browser");
		frame.setContentPane(browser);
		frame.setBounds(0, 0, 900, 600);
		frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		if(args.length > 0)
			browser.goToAddress(args[0]);
	}
}