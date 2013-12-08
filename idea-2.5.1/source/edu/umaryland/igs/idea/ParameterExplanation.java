package edu.umaryland.igs.idea;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * <code>ParameterExplanation</code> represents an explanation for users about the
 * interpretation of a parameter.  A <code>ParameterExplanation</code> can be converted
 * to two different <code>String</code> formats, one suitable for GUI display and the
 * other suitable for writing to a configuration file in .ctl format.
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
public class ParameterExplanation{

	// The default prefix for writing explanations in .ctl format is used for explanations
	// written on the same line as (name, value) pairs in the .ctl file.
	private static final String DEFAULT_PREFIX = "* ";

	// the lines of this parameter explanation
	protected LinkedList<String> lines = new LinkedList<String>();

	/**
	 * A <code>ParameterExplanation</code> is always created with only one line.  After it
	 * is created, additional lines may be added.
	 *
	 * @param firstLine the first or only line of this parameter explanation
	 */
	public ParameterExplanation(String firstLine){
		addLine(firstLine);
	}

	/**
	 * The <code>addLine</code> method adds a line to this parameter explanation.
	 * Lines are always added at the end.
	 *
	 * @param additionalLine the line to add to this parameter explanation
	 */
	public void addLine(String additionalLine){
		lines.add(additionalLine);
	}

	/**
	 * This <code>textRepresentation</code> method returns a String representation
	 * of this parameter explanation suitable for writing to a .ctl file on
	 * the same line as a (name, value) pair.
	 *
	 * @return a <code>String</code> representation of this parameter explanation prefixed with the default prefix
	 */
	public String textRepresentation(){
		return textRepresentation(DEFAULT_PREFIX);
	}

	/**
	 * This <code>textRepresentation</code> method returns a String representation
	 * of this parameter explanation suitable for writing to a .ctl file on
	 * its own line.  Callers of this method specify the <code>prefix</code>
	 * argument based on the lengths of the names and values on the output set
	 * to ensure that the .ctl file is properly aligned.
	 *
	 * @param prefix the custom indentation prefix for this parameter explanation
	 * @return a <code>String</code> representation of this parameter explanation prefixed with the given prefix
	 */
	public String textRepresentation(String prefix){
		if (lines.size() == 0){
			return "";
		}
		ListIterator<String> it = lines.listIterator();
		StringBuffer rv = new StringBuffer(DEFAULT_PREFIX).append(it.next());
		while (it.hasNext()){
			rv.append("\n").append(prefix).append(it.next());
		}
		return rv.toString();
	}

	/**
	 * The <code>graphicalRepresentation</code> method returns a String representation
	 * of this parameter explanation suitable for display as a tool tip in the GUI.
	 * HTML breaks are used in place of newlines.
	 *
	 * @return a <code>String</code> representation of this parameter suitable for display as a tool tip in the GUI
	 */
	public String graphicalRepresentation(){
		if (lines.size() == 0){
			return "";
		}
		if (lines.size() == 1){
			return lines.getFirst();
		}
		ListIterator<String> it = lines.listIterator();
		StringBuffer rv = new StringBuffer("<HTML><BODY>").append(it.next());
		while (it.hasNext()){
			rv.append("<BR>").append(it.next());
		}
		return rv.append("</BODY></HTML>").toString();
	}

}
