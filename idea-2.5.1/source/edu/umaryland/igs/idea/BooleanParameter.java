package edu.umaryland.igs.idea;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedList;

import javax.swing.JPanel;

/**
 * <code>BooleanParameter</code> represents an IDEA parameter which allows
 * the user to choose one of two options (not necessarily true and false).
 * This is a convenience extension of <code>MultipleChoiceParameter</code>.
 * If the buttons are laid out horizontally, the parameter
 * name is displayed above the buttons; if the buttons are laid out
 * vertically, the parameter name is not displayed.  Therefore, a
 * <code>BooleanParameter</code> always takes up two display rows.
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

public class BooleanParameter extends MultipleChoiceParameter{

	static final long serialVersionUID = -8652650567147580646L;

	private static String[] boxIntoArray(String s1, String s2){
		String[] rv = {s1, s2};
		return rv;
	}

	/**
	 * This constructor creates a <code>BooleanParameter</code> with horizontally arranged radio
	 * buttons labeled "Yes" and "No".  It calls the superclass constructor.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 */
	public BooleanParameter(String n, Object v, ParameterExplanation e){
		super(n, v, e);
	}

	/**
	 * This constructor creates a <code>BooleanParameter</code> with horizontally arranged
	 * radio buttons.  The fourth and fifth parameters are used as the names of the options.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 * @param c1 the name of the first choice (as shown to the user)
	 * @param c2 the name of the second choice (as shown to the user)
	 */
	public BooleanParameter(String n, Object v, ParameterExplanation e, String c1, String c2){
		super(n, v, e, boxIntoArray(c1, c2));
	}

	/** 
	 * This constructor creates a <code>BooleanParameter</code> with the given name, value,
	 * explanation and choices <code>c1</code> and <code>c2</code>.
	 * The <code>vertical</code> parameter controls whether the radio buttons are arranged
	 * vertically or horizontally.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 * @param c1 the name of the first choice (as shown to the user)
	 * @param c2 the name of the second choice (as shown to the user)
	 * @param vertical whether to arrange the radio buttons vertically (true) or horizontally (false)
	 */
	public BooleanParameter(String n, Object v, ParameterExplanation e, String c1, String c2, boolean vertical){
		super(n, v, e, boxIntoArray(c1, c2), vertical);
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 */
	public BooleanParameter(String n, Object v, String e){
		this(n, v, new ParameterExplanation(e));
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 * @param c1 the name of the first choice (as shown to the user)
	 * @param c2 the name of the second choice (as shown to the user)
	 */
	public BooleanParameter(String n, Object v, String e, String c1, String c2){
		this(n, v, new ParameterExplanation(e), c1, c2);
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 * @param c1 the name of the first choice (as shown to the user)
	 * @param c2 the name of the second choice (as shown to the user)
	 * @param vertical whether to arrange the radio buttons vertically (true) or horizontally (false)
	 */
	public BooleanParameter(String n, Object v, String e, String c1, String c2, boolean vertical){
		this(n, v, new ParameterExplanation(e), c1, c2, vertical);
	}

	/**
	 * The <code>updateValue</code> method is called whenever the parameter's value is changed
	 * for some reason other than user input.  For example, changing the value of one parameter may
	 * automatically change the value of another parameter.  This method extends the <code>Parameter</code>
	 * method to select the appropriate radio button.
	 *
	 * @param newValue the new value for this parameter
	 */
	public void updateValue(Object newValue){
		super.updateValue(newValue);
		if (value.equals(Boolean.TRUE)){
			choiceButtonArray[0].setSelected(true);
		}
		else{
			choiceButtonArray[1].setSelected(true);
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
