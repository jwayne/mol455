package edu.umaryland.igs.idea;

import java.awt.GridLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * <code>ParameterSet</code> represents a grouping of parameters.
 * Initially, the grouping was both a visual grouping and a grouping by parameter source
 * (either PAML's .ctl file or the IDEA code).  However, the display has been changed so
 * that parameters are no longer grouped by source.  Therefore, a <code>ParameterSet</code>
 * can now represent either a visual grouping or a grouping by source, and a
 * <code>Parameter</code> can now belong to more than one <code>ParameterSet</code>.
 * Class <code>ParameterSet</code> is extended by class <code>PAMLParameterSet</code>.
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
public class ParameterSet implements Cloneable, Serializable{

	static final long serialVersionUID = 5551026741712719469L;

	// This maps parameter names to parameters for efficient look-up.
	protected HashMap<String, Parameter> parameters = new HashMap<String, Parameter>();

	// This is a linked list of the parameter *names* only; it is useful for some operations.
	protected LinkedList<String> parameterList = new LinkedList<String>();

	// The name of the parameter set is used as a title for the entry area.
	protected String setName;

	/**
	 * default constructor
	 */
	public ParameterSet(){
	}
	
	/**
	 * This constructs a <code>ParameterSet</code> with the given name.
	 *
	 * @param setName the name for the parameter set to be constructed
	 */
	public ParameterSet(String setName){
		this.setName = setName;
	}

	/**
	 * @deprecated
	 */
	@SuppressWarnings("unchecked")
	protected Object clone(){
		try{
			ParameterSet rv = (ParameterSet) super.clone();
			rv.parameters = (HashMap<String, Parameter>) rv.parameters.clone();
			rv.parameterList = (LinkedList<String>) rv.parameterList.clone();
			for (String parameterName : parameters.keySet()){
				rv.parameters.put(parameterName, (Parameter) rv.parameters.get(parameterName).clone());
			}
			return rv;
		}
		catch (CloneNotSupportedException cnse){
			return null;
		}
	}

	/**
	 * @deprecated
	 */
	void setCloneConfig(IDEAConfiguration alreadyCopiedConfig){
		for (Parameter p : getParameterList()){
			p.setCloneConfig(alreadyCopiedConfig);
		}
	}

	/**
	 * This <code>addParameter</code> method creates a new parameter with the given name, value
	 * and one-line explanation and adds it to this parameter set.
	 * This is a convenience method for standard parameters with one-line explanations.
	 *
	 * @param name the name for the new parameter to add to this parameter set
	 * @param value the value for the new parameter to add to this parameter set
	 * @param explanation a one-line string explanation for the new parameter to add to this parameter set
	 */
	public void addParameter(String name, Object value, String explanation){
		addParameter(name, value, new ParameterExplanation(explanation));
	}
	
	/**
	 * This <code>addParameter</code> method creates a new parameter with the given name, value
	 * and explanation and adds it to this parameter set.
	 * This is a convenience method for standard parameters with multi-line explanations.
	 *
	 * @param name the name for the new parameter to add to this parameter set
	 * @param value the value for the new parameter to add to this parameter set
	 * @param explanation a <code>ParameterExplanation</code> for the new parameter to add to this parameter set
	 */
	public void addParameter(String name, Object value, ParameterExplanation explanation){
		addParameter(new Parameter(name, value, explanation));
	}

	/**
	 * This <code>addParameter</code> method adds the given parameter to this parameter set.
	 * This method is useful for subclasses of <code>Parameter</code>.  It is also useful for adding
	 * parameters already in at least one parameter set to another.  For that reason, null
	 * parameters are ignored.
	 *
	 * @param p the parameter to add to this parameter set
	 */
	public void addParameter(Parameter p){
		if (p != null){
			parameters.put(p.name, p);
			parameterList.add(p.name);
		}
	}

	/**
	 * The <code>getParameter</code> method looks up the given name in this parameter set.
	 * If a parameter with that name is found, it is returned; otherwise, null is returned.
	 *
	 * @param name the name of the parameter to look up
	 * @return the parameter with the given name, or null if no such parameter is found in this parameter set
	 */
	public Parameter getParameter(String name){
		return parameters.get(name);
	}

	/**
	 * The <code>renameParameter</code> method is useful in loading configurations saved
	 * with old versions of IDEA.  If the specified parameter is found, its name is changed,
	 * and the value in <code>parameterList</code> is also updated.  If the parameter
	 * is not found, nothing happens.
	 *
	 * @param oldName the current name of the parameter to rename
	 * @param newName the desired new name
	 */
	void renameParameter(String oldName, String newName){
		Parameter p = parameters.remove(oldName);
		if (p == null){
			return;
		}
		p.name = newName;
		parameters.put(newName, p);
		parameterList.set(parameterList.indexOf(oldName), newName);
	}

	/**
	 * The <code>getParameterList</code> method returns a linked list of the parameters in this
	 * parameter set.  Note that this is *not* equivalent to the protected <code>parameterList</code> field
	 * because it is a list of <code>Parameter</code> objects, not just parameter names.
	 *
	 * @return a linked list of the parameters in this parameter set, represented as <code>Parameter</code> objects
	 */
	public LinkedList<Parameter> getParameterList(){
		LinkedList<Parameter> rv = new LinkedList<Parameter>();
		for (String parameterName :parameterList){
			rv.add(parameters.get(parameterName));
		}
		return rv;
	}

	/**
	 * The <code>size</code> method returns the number of parameters in this parameter set.
	 *
	 * @return the number of parameters in this parameter set
	 */
	public int size(){
		return parameterList.size();
	}

	/**
	 * The static <code>maxNameLength</code> method returns the maximum name length in a set of
	 * tied parameter sets.
	 *
	 * @param tiedSets the tied parameter sets
	 *
	 * @return the maximum name length across all parameter sets in the input array
	 */
	public static int maxNameLength(ParameterSet[] tiedSets){
		return Collections.max(Arrays.asList(tiedSets),
													 new Comparator<ParameterSet>(){
			public int compare(ParameterSet o1, ParameterSet o2){
				return o1.maxNameLength() - o2.maxNameLength();
			}
		}).maxNameLength();
	}

	/**
	 * The <code>maxNameLength</code> method returns the maximum name length in this parameter set.
	 * This is used for display purposes.
	 *
	 * @return the maximum name length in this parameter set
	 */
	public int maxNameLength(){
		if (size() == 0){
			return 0;
		}
		return Collections.max(parameterList,
													 new Comparator<String>(){
			public int compare(String o1, String o2){
				if (parameters.get(o1) instanceof MultipleChoiceParameter){
					return -1;
				}
				if (parameters.get(o2) instanceof MultipleChoiceParameter){
					return 1;
				}
				if (o1 == null){
					return (o2 == null) ? 0 : -1;
				}
				if (o2 == null){
					return 1;
				}
				return o1.length() - o2.length();
			}
		}).length();
	}

	/**
	 * The <code>maxCombinedNameAndValueLength</code> method returns the maximum combined (name + value)
	 * length in this parameter set.  This is used for writing configuration files in .ctl format.
	 *
	 * @return the maximum combined (name + value) length in this parameter set
	 */
	public int maxCombinedNameAndValueLength(){
		Parameter longest = Collections.max(parameters.values(),
																				new Comparator<Parameter>(){
																					public int compare(Parameter o1, Parameter o2){
																						return (o1.name.length() + o1.valueString().length()
																										- o2.name.length() - o2.valueString().length());
																					}
																				});
		return longest.name.length() + longest.valueString().length();
	}

	/**
	 * This convenience method passes the maximum name length for this parameter set to the
	 * two-arg version.  It is to be used for parameter sets not tied to any other parameter set.
	 *
	 * @param displayColumns the number of display columns in which to lay out the entry areas
	 *
	 * @return a <code>JPanel</code> component to add to the GUI
	 */
	public JPanel entryArea(int displayColumns){
		return entryArea(displayColumns, maxNameLength());
	}

	/**
	 * The <code>entryArea</code> method returns a GUI component that lays out entry areas for each
	 * parameter in one or more vertical columns.
	 * @param displayColumns the number of display columns in which to lay out the entry areas
	 * @param maxNameLength the length of the longest parameter name in this set or any set tied to it
	 *
	 * @return a <code>JPanel</code> component to add to the GUI
	 */
	public JPanel entryArea(int displayColumns, int maxNameLength){
		if (displayColumns < 1){
			throw new IllegalArgumentException("The argument to entryArea must be at least one.");
		}
		JPanel entryArea = new JPanel(new GridLayout(1, displayColumns));
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0; // Display the title in the left column.
		constraints.gridy = 0;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridwidth = displayColumns;
		constraints.ipady = 3;
		GridBagLayout panelLayout = new GridBagLayout();
		JPanel[] columns = new JPanel[displayColumns];
		for (int i = 0; i < displayColumns; i++){
			columns[i] = new JPanel(panelLayout);
			// Add the set name as a title for the entry area.
			if ((setName != null) && (setName.length() != 0)){
				JLabel entryAreaLabel = new JLabel(setName);
				entryAreaLabel.setFont(new Font(entryAreaLabel.getFont().getName(), Font.BOLD, 10));
				if (i > 0){
					entryAreaLabel.setForeground(entryAreaLabel.getBackground());
				}
				constraints.weighty = 0;
				constraints.weightx = 1;
				panelLayout.setConstraints(entryAreaLabel, constraints); // defaults
				columns[i].add(entryAreaLabel);
			}
		}

		// Add the entry areas for the parameters.
		ListIterator<Parameter> parameterIterator = getParameterList().listIterator();
		int nextColumn = displayColumns - 1;  // Display the first parameter in the left column.
		constraints.gridy = 0;
		constraints.gridwidth = 1;
		constraints.weightx = 1;
		int maxPreferredWidth = 0;
		while (parameterIterator.hasNext()){
			if (nextColumn == displayColumns - 1){
				constraints.gridy+= constraints.gridheight;
			}
			Parameter nextParameter = parameterIterator.next();
			JPanel parameterEntryArea = nextParameter.entryArea(maxNameLength + 1);
			if (nextColumn == displayColumns - 1){
				nextColumn = 0;
			}
			else{
				nextColumn++;
			}
			constraints.gridheight = nextParameter.displayRows();
			constraints.weighty = constraints.gridheight;
			constraints.fill =
				(nextParameter instanceof MultipleChoiceParameter) ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
			if (nextParameter instanceof BlankParameter){
				constraints.fill = GridBagConstraints.BOTH;
			}
			constraints.ipady = 3;
			panelLayout.setConstraints(parameterEntryArea, constraints); // defaults
			maxPreferredWidth = Math.max(maxPreferredWidth, parameterEntryArea.getPreferredSize().width);
			columns[nextColumn].add(parameterEntryArea);
		}


		// Finish arranging the entry area.
		for (int i = 0; i < displayColumns; i++){
			constraints.weighty=1000;
			constraints.fill=GridBagConstraints.BOTH;
			Component glue = Box.createVerticalGlue();
			panelLayout.setConstraints(glue, constraints);
			columns[i].add(glue);
			entryArea.add(columns[i]);
		}
		return entryArea;
	}

}
