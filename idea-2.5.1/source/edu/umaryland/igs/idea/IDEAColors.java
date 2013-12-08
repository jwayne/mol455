package edu.umaryland.igs.idea;

import java.awt.Color;

/**
 * <code>IDEAColors</code> contains color constants and color-related
 * code that may be used throughout IDEA.
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
class IDEAColors{
	
	// Color constants below are (R, G, B).

	static final Color BASIC_BLUE = new Color(0, 0, 224);
	static final Color GRAY_BLUE = new Color(112, 112, 192);

	static final Color PURPLE = new Color(128, 0, 160);
	static final Color HYACINTH = new Color(160, 96, 192);
	static final Color MAUVE = new Color(192, 160, 208);  // An alternate version had (184, 152, 208).
	static final Color LAVENDER = new Color(224, 208, 232);
	static final Color PALE_LAVENDER = new Color(240, 232, 244);
	static final Color FUCHSIA = new Color(192, 0, 112);
	static final Color RASPBERRY = new Color(224, 0, 72);
	static final Color SUBTLE_PURPLE = new Color(120, 72, 172);

	static final Color DARK_GREEN = new Color(0, 96, 32);
	static final Color BORDER_GRAY = new Color(122, 138, 153);

	/**
	 * This method returns the midpoint between the two specified colors
	 * based on numerically averaging the colors' red, green and blue components.
	 *
	 * @param c1 one of two colors to be averaged
	 * @param c2 one of two colors to be averaged
	 *
	 * @return the midpoint between the two colors
	 */
	static Color average(Color c1, Color c2){
		return new Color ((c1.getRed() + c2.getRed()) / 2,
											(c1.getGreen() + c2.getGreen()) / 2,
											(c1.getBlue() + c2.getBlue()) / 2);
	}

}
