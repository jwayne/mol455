package edu.umaryland.igs.idea;

import java.awt.Component;

import javax.swing.JLabel;

/**
 * The <code>CustomRenderable</code> interface is implemented by classes whose objects can be
 * converted to an AWT or Swing component designed to take up part of a table cell.  This
 * component is added to the table cell renderer's rendering of that cell.  It is intended that
 * objects of <code>CustomRenderable</code> classes be added directly to the data structure that
 * serves as the table's model.  Each <code>CustomRenderable</code> class should define a
 * <code>toString()</code> method that returns a reasonable value suitable for displaying as a
 * label in the cell in which the custom rendering is to be displayed.
 *
 * <p>Written:
 *
 * <p>Copyright (C) 2007, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 *
 */
public interface CustomRenderable{

	/**
	 * The <code>customRendering</code> method should return an AWT or Swing component representing
	 * this object that can be added to the component to be returned by a table cell renderer.
	 *
	 * @return an AWT or Swing component representing this object that can be displayed as part of a table cell
	 */
	Component customRendering();
}
