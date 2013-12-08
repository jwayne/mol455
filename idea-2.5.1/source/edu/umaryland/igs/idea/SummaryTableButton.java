package edu.umaryland.igs.idea;

import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.border.EmptyBorder;

/**
 * <code>SummaryTableButton</code> represents a button in a summary table's
 * header.  It is associated with an icon.  Each summary table button is
 * also associated with a `button method' that may be present in
 * <code>SummaryTableModel</code> or a subclass.  The header renderer uses
 * reflection to invoke the appropriate method when the button is clicked.
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

public class SummaryTableButton extends JButton{
	
	static final EmptyBorder EMPTY_BORDER = new EmptyBorder(IDEAConstants.ZERO_INSETS);  // an invisible border

	protected String methodName;  // the name of the method to be invoked when this button is clicked


	/**
	 * The <code>getMethodName</code> method returns the name of the method to be invoked when this button is clicked.
	 *
	 * @return the name of the method associated with this button
	 */
	public String getMethodName(){
		return methodName;
	}
	
	/**
	 * This is the only constructor for <code>SummaryTableButton</code>.  The button is created from a button name,
	 * which would be its tool tip text.  Its method name is derived from that name by removing spaces, capitalizing
	 * the letters after removed spaces and converting the first letter of the first word to lower case.
	 * The icon for the button is loaded based on this derived method name.
	 *
	 * @param ttt the name of the button, which would be its tool tip text
	 */
	public SummaryTableButton(String ttt){
		setToolTipText(ttt);
		String[] words = ttt.split("\\s");
		StringBuffer mn = new StringBuffer();
		for (String word : words){
			if ((word != null) && (! word.equals(""))){
				String firstLetter = word.substring(0, 1);
				if (word == words[0]){  // This is object equality, not string equality.
					mn.append(firstLetter.toLowerCase());
				}
				else{
					mn.append(firstLetter.toUpperCase());
				}
				mn.append(word.substring(1, word.length()));
			}
		}
		methodName = mn.toString();
		setIcon(new ImageIcon(ClassLoader.getSystemResource(methodName + ".png")));
		setMargin(IDEAConstants.ZERO_INSETS);
		setBorder(EMPTY_BORDER);
	}

}
