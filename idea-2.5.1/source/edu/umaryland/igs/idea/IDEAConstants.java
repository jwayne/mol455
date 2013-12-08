package edu.umaryland.igs.idea;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.ImageIcon;

/**
 * <code>IDEAConstants</code> contains non-color constants used throughout IDEA.
 *
 * <p>Written:
 *
 * <p>Copyright (C) 2006, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 *
 */

class IDEAConstants{
	
	// The screen resolution is used to set up displays.
	static final Rectangle SCREEN_SIZE = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

	static final Insets ZERO_INSETS = new Insets(0, 0, 0, 0);  // zero insets for compactness

	// a thumbnail-sized idea logo to display in title bars
	static final Image LOGO_THUMB = new ImageIcon(ClassLoader.getSystemResource("idea-logo-thumb.gif")).getImage();

	static final String OS;  // the user's operating system

	static{
		String os = System.getProperty("os.name");
		OS = (os == null) ? "N/A" : os;
	}

}
