package edu.umaryland.igs.idea;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * <code>BlankParameter</code> represents a parameter with no value.
 * It is meant to take up space in a display.
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
public class BlankParameter extends Parameter{

	static final long serialVersionUID = -805462688625323171L;

	// Since a <code>BlankParameter</code> has no properties, only one is created.
	public static final BlankParameter SOLE_INSTANCE = new BlankParameter();

	/**
	 * This is the only  constructor for <code>BlankParameter</code>.
	 */
	private BlankParameter(){
		super(null, null, (ParameterExplanation) null);
	}

	/**
	 * The <code>setEnabled</code> method has no function for a <code>BlankParameter</code>.
	 *
	 * @param enabled whether the parameter should be enabled
	 */
	public void setEnabled(boolean enabled){
	}

	/**
	 * The <code>setEditable</code> method has no function for a <code>BlankParameter</code>.
	 *
	 * @param editable whether the text field should be editable
	 */
	public void setEditable(boolean editable){
	}

	/**
	 * The <code>isEnabled</code> method returns false; a <code>BlankParameter</code> is always disabled.
	 *
	 * @return false for any <code>BlankParameter</code>
	 */
	public boolean isEnabled(){
		return false;
	}

	/**
	 * The <code>entryArea</code> method for <code>BlankParameter</code> returns a blank <code>JPanel</code>
	 * sized to take up the same amount of vertical space as a <code>FileParameter</code>.  This is
	 * accomplished by means of an invisible button.
	 *
	 * @param minimumNameLength ignored in this subclass method
	 * @return a JPanel component to add to the GUI
	 */
	public JPanel entryArea(int minimumNameLength){
		FlowLayout parameterPanelLayout = new FlowLayout(FlowLayout.LEADING);
		parameterPanelLayout.setVgap(0);
		JPanel rv = new JPanel(parameterPanelLayout);
		JButton browseButton = new JButton(" ");
		browseButton.setFont(new Font(browseButton.getFont().getName(), browseButton.getFont().getStyle(), 10));
		browseButton.setMargin(new Insets(0, 0, 0, 0));
		browseButton.setEnabled(false);
		browseButton.setBorder(new EmptyBorder(3, 3, 3, 3));
		rv.add(browseButton);
		return rv;
	}

	/**
	 * The <code>valueString</code> method for <code>BlankParameter</code> returns an empty string.
	 *
	 * @return an empty string
	 */
	public String valueString(){
		return "";
	}

	/**
	 * This <code>toString</code> method for <code>BlankParameter</code> returns an empty string.
	 * Callers of this method supply a <code>minimumCombinedLength</code> parameter obtained from a
	 * <code>ParameterSet</code> containing this parameter.
	 *
	 * @param minimumCombinedLength the maximum combined (name + value) length in a parameter set containing this parameter
	 * @return an empty string
	 */
	public String toString(int minimumCombinedLength){
		return "";
	}

	/**
	 * The basic <code>toString</code> method for <code>BlankParameter</code> returns an empty string.
	 *
	 * @return an empty string
	 */
	public String toString(){
		return "";
	}

}
