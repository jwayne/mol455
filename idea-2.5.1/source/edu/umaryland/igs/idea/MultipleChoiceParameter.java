package edu.umaryland.igs.idea;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.LineBorder;

/**
 * <code>MultipleChoiceParameter</code> represents an IDEA parameter that
 * allows the user to choose one of multiple options.
 * This class overrides <code>Parameter</code> to provide an entry area
 * with radio buttons.  The buttons may be arranged either horizontally or
 * vertically.  If the buttons are laid out horizontally, the parameter
 * name is displayed above the buttons; if the buttons are laid out
 * vertically, the parameter name is not displayed.
 * <code>BooleanParameter</code> is a convenience extension for
 * parameters with only two possible values.
 *
 * <p>Written:
 *
 * <p>Copyright (C) 2009, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 *
 */

public class MultipleChoiceParameter extends Parameter{

	private static final long serialVersionUID = -8652650567147580647L;

	protected String[] choiceArray = {"Yes", "No"};  // the names of the options, as displayed to the user
	protected boolean displayVertically = false;// radio buttons are laid out vertically if true, horizontally otherwise
	protected LocalRadioButton[] choiceButtonArray;  // the radio buttons for the option

	/**
	 * This constructor creates a <code>MultipleChoiceParameter</code> with horizontally arranged radio
	 * buttons labeled "Yes" and "No".  It calls the superclass constructor.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 */
	public MultipleChoiceParameter(String n, Object v, ParameterExplanation e){
		super(n, v, e);
	}

	/**
	 * This constructor creates a <code>MultipleChoiceParameter</code> with horizontally arranged
	 * radio buttons.  The fourth and fifth parameters are used as the names of the options.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 * @param cs the names of the choices (as shown to the user)
	 */
	public MultipleChoiceParameter(String n, Object v, ParameterExplanation e, String [] cs){
		super(n, v, e);
		choiceArray = cs;
		choiceButtonArray = new LocalRadioButton[cs.length];
		for (int i = 0; i < cs.length; i++){
			choiceButtonArray[i] = new LocalRadioButton(cs[i]);
		}
	}

	/** 
	 * This constructor creates a <code>MultipleChoiceParameter</code> with the given name,
	 * value, explanation and choices <code>cs</code>.
	 * The <code>vertical</code> parameter controls whether the radio buttons are arranged
	 * vertically or horizontally.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 * @param cs the names of the choices (as shown to the user)
	 * @param vertical whether to arrange the radio buttons vertically (true) or horizontally (false)
	 */
	public MultipleChoiceParameter(String n, Object v, ParameterExplanation e, String[] cs, boolean vertical){
		this(n, v, e, cs);
		displayVertically = vertical;
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 */
	public MultipleChoiceParameter(String n, Object v, String e){
		this(n, v, new ParameterExplanation(e));
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 * @param cs the names of the choices (as shown to the user)
	 */
	public MultipleChoiceParameter(String n, Object v, String e, String[] cs){
		this(n, v, new ParameterExplanation(e), cs);
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 * @param cs the names of the choices (as shown to the user)
	 * @param vertical whether to arrange the radio buttons vertically (true) or horizontally (false)
	 */
	public MultipleChoiceParameter(String n, Object v, String e, String[] cs, boolean vertical){
		this(n, v, new ParameterExplanation(e), cs, vertical);
	}

	/**
	 * @deprecated
	 */
	protected Object clone(){
		MultipleChoiceParameter rv = (MultipleChoiceParameter) super.clone();
		rv.choiceButtonArray = new LocalRadioButton[rv.choiceArray.length];
		for (int i = 0; i < rv.choiceArray.length; i++){
			rv.choiceButtonArray[i] = new LocalRadioButton(rv.choiceArray[i]);
		}
		return rv;
	}

	/**
	 * This method overrides the method in <code>Parameter</code> to request two rows for a
	 * horizontally displayed parameter or one row per choice for a vertically displayed parameter.
	 *
	 * @return the number of rows the entry area for this parameter should take up on the display
	 */
	public int displayRows(){
		return displayVertically ? choiceArray.length : 2;
	}

	/**
	 * The <code>addActionListener</code> method allows listeners on a <code>MultipleChoiceParameter</code>
	 * to process events on its radio buttons.
	 *
	 * @param l the action listener to add
	 */
	public void addActionListener(ActionListener l){
		super.addActionListener(l);
		if (choiceButtonArray != null){
			for (LocalRadioButton lrb : choiceButtonArray){
				lrb.addActionListener(l);
			}
		}
	}

	/**
	 * The <code>resetActionListeners</code> method removes all action listeners on this parameter.
	 * It is useful in loading configurations saved using older versions of IDEA.  Action listeners
	 * in these saved configurations may perform outdated actions, so they should be removed.
	 */
	void resetActionListeners(){
		super.resetActionListeners();
		if (choiceButtonArray != null){
			for (ActionListener al : choiceButtonArray[0].getActionListeners()){
				for (LocalRadioButton lrb : choiceButtonArray){
					lrb.removeActionListener(al);
				}
			}
		}
	}
	
	/**
	 * The <code>setEnabled</code> overrides the method in class <code>Parameter</code> to disable the radio buttons.
	 *
	 * @param enabled whether the parameter should be enabled
	 */
	public void setEnabled(boolean enabled){
		super.setEnabled(enabled);
		for (LocalRadioButton lrb :choiceButtonArray){
			lrb.setEnabled(enabled);
			lrb.invalidate();
		}
	}

	/** This <code>setEnabled</code> method enables only those choices at the specified indices and disables all others.
	 */
	public void setEnabled(int... choices){
		ALL_POSSIBLE_CHOICES: for (int i = 0; i < choiceArray.length; i++){
			for (int choice : choices){
				if (choice == i){
					choiceButtonArray[i].setEnabled(true);
					continue ALL_POSSIBLE_CHOICES;
				}
			}
			choiceButtonArray[i].setEnabled(false);
		}
	}

	/**
	 * The <code>entryArea</code> method returns a GUI component that displays the name of the parameter
	 * and, if available, an explanation of its interpretation and allows the user to supply a value.
	 * This overrides the superclass method to display a set of radio buttons instead of a text field.
	 * Callers of this method supply a <code>minimumNameLength</code> parameter obtained from a
	 * <code>ParameterSet</code> containing this parameter.
	 *
	 * @param minimumNameLength the text label length, based on the maximum length among names in the parameter set
	 * @return a JPanel component to add to the GUI
	 */
	public JPanel entryArea(int minimumNameLength){

		// Create the panel and give it a vertical layout (even if the buttons are to be laid out horizontally).
		JPanel parameterPanel = new JPanel();
		parameterPanel.setLayout(new BoxLayout(parameterPanel, BoxLayout.Y_AXIS));

		// Create the radio buttons.
		for (int i = 0; i < choiceArray.length; i++){
			choiceButtonArray[i].setFont(new Font("Monospaced", Font.PLAIN, 10));
			choiceButtonArray[i].setActionCommand(choiceArray[i]);
			choiceButtonArray[i].setMaximumSize(new Dimension(choiceButtonArray[i].getPreferredSize().width,
																												choiceButtonArray[i].getFont().getSize() + 2));
			choiceButtonArray[i].setBorder(new LineBorder(Color.BLACK));  // This makes the radio button smaller.
		}

		// If this parameter already has a value, display the appropriate option as selected.
		if (value != null){
			for (int i = 0; i < choiceArray.length; i++){
				if (value.equals(choiceArray[i])){
					choiceButtonArray[i].setSelected(true);
				}
			}
		}

		// Add the radio buttons to a button group to make the options mutually exclusive.
		ButtonGroup choices = new ButtonGroup();
		for (LocalRadioButton lrb : choiceButtonArray){
			choices.add(lrb);
		}

		// Add an action listener that changes the parameter value when the user chooses a radio button.
		ActionListener choiceListener = new ActionListener(){
				public void actionPerformed(ActionEvent e){
					MultipleChoiceParameter.this.value = e.getActionCommand();
				}
			};
		for (LocalRadioButton lrb : choiceButtonArray){
			lrb.addActionListener(choiceListener);
		}

		if (displayVertically){
			// In vertical mode, each option is laid out in a separate (horizontal) flow layout.
			// A help icon is displayed to the right of each option.  The parameter name is not displayed.
			FlowLayout explanationPanelLayout = new FlowLayout(FlowLayout.LEADING);
			explanationPanelLayout.setVgap(0);

			for (LocalRadioButton choiceButton : choiceButtonArray){
				JPanel explanationPanel = new JPanel(explanationPanelLayout);
				explanationPanel.add(choiceButton);
				if (explanation != null){
					JLabel explanationLabel = new JLabel(Parameter.I_ICON);
					explanationLabel.setToolTipText(explanation.graphicalRepresentation());
					explanationPanel.add(explanationLabel);
				}
				explanationPanel.setMaximumSize(explanationPanel.getPreferredSize());
				explanationPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
				parameterPanel.add(explanationPanel);
			}
		}
		else{
			// In horizontal mode, the parameter name and help icon are laid out in a border layout in the first row.
			// Underneath them, the options are laid out in a grid layout.
			JPanel headingPanel = new JPanel(new BorderLayout());
			JLabel nameLabel = new JLabel(name + ":");
			nameLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
			nameLabel.setMaximumSize(new Dimension(nameLabel.getPreferredSize().width, nameLabel.getPreferredSize().height));
			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			headingPanel.add(nameLabel, BorderLayout.WEST);
			if (explanation != null){
				JLabel explanationLabel = new JLabel(Parameter.I_ICON);
				explanationLabel.setToolTipText(explanation.graphicalRepresentation());
				explanationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				headingPanel.add(explanationLabel, BorderLayout.EAST);
			}
			headingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			parameterPanel.add(headingPanel);
			JPanel choicePanel = new JPanel(new GridLayout(1, choiceArray.length));
			for (LocalRadioButton lrb : choiceButtonArray){
				choicePanel.add(lrb);
			}
			choicePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			parameterPanel.add(choicePanel);
		}
		return parameterPanel;
	}

	/**
	 * The <code>isEnabled</code> method returns whether at least two choices are currently enabled.
	 *
	 * @return whether at least two choices are enabled
	 */
	public boolean isEnabled(){
		int numEnabled = 0;
		for (LocalRadioButton lrb : choiceButtonArray){
			if (lrb.isEnabled()){
				numEnabled++;
			}
		}
		return numEnabled >= 2;
	}

	/**
	 * The <code>updateValue</code> method is called whenever the parameter's value is changed
	 * for some reason other than user input.  For example, changing the value of one parameter may
	 * automatically change the value of another parameter.  This method extends the superclass
	 * method to select the appropriate radio button.
	 *
	 * @param newValue the new value for this parameter
	 */
	public void updateValue(Object newValue){
		super.updateValue(newValue);
		for (int i = 0; i < choiceArray.length; i++){
			if (value.equals(choiceArray[i])){
				choiceButtonArray[i].setSelected(true);
			}
		}
	}

	/**
	 * The <code>readObject</code> method has the exact signature required for Java serialization,
	 * although serialization is actually performed using the third-party XStream library.
	 * Parameters require custom serialization because the action and focus listeners which update
	 * their values based on user input to the GUI are not fields of the objects they listen on
	 * and thus are not automatically restored during deserialization, so they must be restored
	 * explicitly to ensure that parameter changes the user makes after loading a saved configuration
	 * are honored.  Since the <code>addActionListener</code> method modifies the <code>actionListeners</code>
	 * field, a copy must be used here to avoid a <code>ConcurrentModificationException</code>.
	 * After adding the listeners to the GUI components using <code>addActionListener</code>, the
	 * <code>actionListeners</code> parameter is reset to the copy so it will not contain duplicates.
	 *
	 * NOTE:  This method is identical for class <code>Parameter</code> and all its subclasses, but
	 * because it is required to be private, it has been copied into each subclass.
	 *
	 * @param in the input stream from which to read this parameter
	 *
	 * @throws IOException if an I/O error occurs during deserialization
	 * @throws ClassNotFoundException if a class in the hierarchy of the deserialized parameter is unrecognized
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
 		in.defaultReadObject();
		LinkedList<ActionListener> copy = new LinkedList<ActionListener>(actionListeners);
		for (ActionListener al : copy){
			addActionListener(al);
		}
		actionListeners = copy;
	}

}

/**
 * The radio button members of a <code>MultipleChoiceParameter</code> are instances of type <code>LocalRadioButton</code>,
 * which extends <code>JRadioButton</code> to allow more leniency in deserialization.  The locale for a radio button
 * may not be initialized properly during deserialization if the button had not been displayed before it was
 * serialized.  <code>LocalRadioButton</code> provides a default locale in that situation so deserialization can succeed.
 */
class LocalRadioButton extends JRadioButton{

	/**
	 * The constructor for <code>LocalRadioButton</code> calls the corresponding superclass constructor.
	 *
	 * @param text the string displayed beside the radio button
	 */
	public LocalRadioButton(String text){
		super(text);
	}

	/**
	 * The <code>getLocale</code> method overrides the method in class <code>Component</code> to provide a default
	 * locale if none can be determined in the usual way.
	 *
	 * @return the locale for this component as normally determined, or a default locale if that would throw an exception
	 */
	public Locale getLocale(){
		try{
			return super.getLocale();
		}
		catch (IllegalComponentStateException icse){
			return Locale.getDefault();
		}
	}

}

