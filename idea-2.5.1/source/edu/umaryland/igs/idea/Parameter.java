package edu.umaryland.igs.idea;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.tigr.antware.shared.util.StringUtils;

/**
 * <code>Parameter</code> represents a user-specifiable parameter
 * which determines IDEA behavior.  Each parameter has a name, a value
 * and an explanation.  The default entry area provided features a text field;
 * subclasses (including anonymous subclasses) may provide their own methods
 * for displaying entry areas.
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
public class Parameter implements Serializable{

	static final long serialVersionUID = 9205676949227530359L;
	
	// An icon with a lower-case I in a circle alerts the user to mouse-over help.
	static final ImageIcon I_ICON = new ImageIcon(ClassLoader.getSystemResource("i_icon.gif"));

	public String name;  // the name for this parameter, displayed as part of a label in the default entry panel
	public Object value;  // the value for this parameter (possibly null)
	public ParameterExplanation explanation;  // an explanation for users about the interpretation of this parameter
	protected int minTextAreaSize = 5;  // the minimum size of a text field, so even parameters with no value show up
	protected JTextField textField;  // the editable text field in which the value for this parameter is displayed
	protected JPanel entryArea;  // a GUI component displaying this parameter and generally allow the user to modify it

	// A list of action listeners is maintained so they can be restored following deserialization.
	protected LinkedList<ActionListener> actionListeners = new LinkedList<ActionListener>();
	protected TextFieldInputVerifier inputVerifier;

	/**
	 * This is the basic constructor for <code>Parameter</code>.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 */
	public Parameter(String n, Object v, ParameterExplanation e){
		name = n;
		value = v;
		explanation = e;
		String valueString = valueString();
		if (valueString.length() > 5){
			minTextAreaSize = 20;
		}
		textField = new JTextField(valueString, minTextAreaSize);

		// The value is updated whenever the text field loses focus or the user hits "Enter" while it has focus.
		addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					Parameter.this.value = textField.getText();
				}
			});
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 */
	public Parameter(String n, Object v, String e){
		this(n, v, new ParameterExplanation(e));
	}
	
	/**
	 * @deprecated
	 */
	protected Object clone(){
		try{
			final Parameter rv = (Parameter) super.clone();
			rv.textField = new JTextField(rv.valueString(), rv.minTextAreaSize);
			rv.addActionListener(new ActionListener(){
					public void actionPerformed(ActionEvent e){
						rv.value = rv.textField.getText();
					}
				});
			if (textField.getInputVerifier() instanceof Cloneable){
				try{
					rv.addInputVerifier((TextFieldInputVerifier) getInputVerifier().clone());
				}
				catch (CloneNotSupportedException cnse){
					TextFieldInputVerifier newVerifier = getInputVerifier();
					newVerifier.errorMessage = (newVerifier.errorMessage == null ) ? null : newVerifier.errorMessage.substring(0);
					return newVerifier;
				}
			}
			return rv;
		}
		catch (CloneNotSupportedException cnse){  // N/A
			cnse.printStackTrace();
			return null;
		}
	}

	/**
	 * @deprecated
	 */
	void setCloneConfig(IDEAConfiguration alreadyCopiedConfig){
		InputVerifier iv = textField.getInputVerifier();
		if (iv instanceof TextFieldInputVerifier){
			((TextFieldInputVerifier) iv).setCloneConfig(alreadyCopiedConfig);
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

	/**
	 * The <code>valueIsValid</code> method can be overridden by subclasses.  It is used
	 * to perform input validation.
	 *
	 * @return whether the current value is valid for this parameter
	 */
	public boolean valueIsValid(){
		try{
			TextFieldInputVerifier tfiv = (TextFieldInputVerifier) textField.getInputVerifier();
			return tfiv.verifyText(valueString());
		}
		catch (ClassCastException cce){  // There is no input verifier, or the verifier isn't a TextFieldInputVerifier.
			return true;
		}
	}

	/**
	 * The <code> valueIsValidOrEmpty</code> method returns true if the text field either is
	 * empty or contains a valid value.  This is useful for the purposes of checking affixes
	 * when one field is not yet filled in.
	 *
	 * @return true if the text field is empty or contains a valid value; false otherwise
	 */
	public boolean valueIsValidOrEmpty(){
		String text = textField.getText();
		return (text == null) || (text.length() == 0) || valueIsValid();
	}

	/**
	 * The <code>updateValue</code> method is called whenever the parameter's value is changed
	 * for some reason other than user input.  For example, changing the value of one parameter may
	 * automatically change the value of another parameter.
	 *
	 * @param newValue the new value for this parameter
	 */
	public void updateValue(Object newValue){
		value = newValue;
		String valueString = valueString();
		textField.setText(valueString);
		textField.invalidate();
		textField.setColumns(Math.max(textField.getColumns(), valueString.length() + 2));  // automatically invalidates
		textField.setColumns(Math.min(textField.getColumns(), 25));
	}

	/**
	 * The <code>setMinTextAreaSize</code> method can be used to set a non-standard minimum size
	 * for the text area.  It has no effect if the text size is already larger than the new minimum.
	 *
	 * @param newSize the new minimum size
	 */
	public void setMinTextAreaSize(int newSize){
		minTextAreaSize = newSize;
		textField.setColumns(Math.max(textField.getColumns(), newSize));  // automatically invalidates
	}

	/**
	 * The <code>addActionListener</code> method adds a listener which is activated when something is entered
	 * in the text field *or* when the text field loses focus.  Code elsewhere forces a focus loss when the user
	 * clicks a button to move to another page in the GUI or to start analysis.
	 *
	 * @param l the ActionListener to add
	 */
	public void addActionListener(ActionListener l){
		final ActionListener al = l;
		textField.addActionListener(al);
		FocusListener fl = new FocusAdapter(){
				public void focusLost(FocusEvent e){
					al.actionPerformed(null);
				}
			};
		textField.addFocusListener(fl);
		actionListeners.add(al);
	}

	/**
	 * The <code>resetActionListeners</code> method removes all action listeners on this parameter.
	 * It is useful in loading configurations saved using older versions of IDEA.  Action listeners
	 * in these saved configurations may perform outdated actions, so they should be removed.
	 */
	void resetActionListeners(){
		for (ActionListener al : textField.getActionListeners()){
			textField.removeActionListener(al);
		}
		actionListeners.clear();
		for (FocusListener fl : textField.getFocusListeners()){
			textField.removeFocusListener(fl);
		}
		// The value is updated whenever the text field loses focus or the user hits "Enter" while it has focus.
		addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					Parameter.this.value = textField.getText();
				}
			});
	}

	/**
	 * The <code>addInputVerifier</code> method adds an input verifier which checks that text in the text field
	 * is valid.  The new input verifier for the text field combines the checks in the specified verifier with
	 * those in any previous verifier.  It also checks that the component is a text field.
	 *
	 * @param tfiv an input verifier which performs checks on a text field; these checks are added to any existing checks
	 */
	public void addInputVerifier(final TextFieldInputVerifier tfiv){
		final TextFieldInputVerifier oldVerifier = getInputVerifier();
		textField.setInputVerifier(tfiv);
		inputVerifier = tfiv;  // Save it so it can be reinstated following deserialization.
 		textField.setInputVerifier(new TextFieldInputVerifier(){
 				public boolean verify(JComponent input){
 					return (input instanceof JTextField)
						&& oldVerifier.verify(input)
						&& tfiv.verifyText(((JTextField) input).getText());
 				}
 				public boolean shouldYieldFocus(JComponent input){
					return
						(! (input instanceof JTextField))
						|| (oldVerifier.shouldYieldFocus(input) && tfiv.shouldYieldFocus(input));
 				}
 				String errorMessage(String text){
 					return tfiv.verifyText(text) ? oldVerifier.errorMessage(text) : tfiv.errorMessage(text);
				}
				public boolean verifyText(String text){
 					return oldVerifier.verify(textField) && tfiv.verifyText(text);
 				}
 			});
	}

	/**
	 * The <code>getInputVerifier</code> method returns the current input verifier if it is a
	 * <code>TextFieldInputVerifier</code>; if it is not or if there is currently no input
	 * verifier, it returns a <code>TextFieldInputVerifier</code> that passes all input.
	 * Thus, it is guaranteed to return a non-null <code>TextFieldInputVerifier</code>.
	 * 
	 * @return either the current input verifier or a new <code>TextFieldInputVerifier</code> that passes all input
	 */
	final public TextFieldInputVerifier getInputVerifier(){
		InputVerifier iv = textField.getInputVerifier();
		if (iv instanceof TextFieldInputVerifier){
			return (TextFieldInputVerifier) iv;
		}
		return new TextFieldInputVerifier(){
				public boolean verifyText(String text){
					return true;
				}
				String errorMessage(String text){
					return "Valid input:  " + text;
				}
			};
	}

	/**
	 * The <code>setEnabled</code> method enables or disables this parameter.
	 *
	 * @param enabled whether the parameter should be enabled
	 */
	public void setEnabled(boolean enabled){
		if (enabled){
			textField.setForeground(Color.BLACK);
			textField.setBackground(Color.WHITE);
		}
		else{
			textField.setForeground(Color.GRAY);
			textField.setBackground(Color.LIGHT_GRAY);
		}
		setEditable(enabled);
		if (! getInputVerifier().shouldYieldFocus(textField)){
			textField.requestFocusInWindow();
		}
		if (entryArea != null){
			entryArea.invalidate();
		}
	}

	/**
	 * The <code>setEditable</code> method toggles the editability of the text field.
	 *
	 * @param editable whether the text field should be editable
	 */
	public void setEditable(boolean editable){
		textField.setEditable(editable);
		textField.setEnabled(editable);
		textField.setDisabledTextColor(textField.getForeground());
		textField.invalidate();
	}

	/**
	 * The <code>isEnabled</code> method returns whether the text field is currently editable.
	 *
	 * @return whether the text field is editable
	 */
	public boolean isEnabled(){
		return textField.isEditable();
	}

	/**
	 * The <code>displayRows</code> method returns how many rows the entry area for this parameter
	 * should take up on the display.  The default is one; subclasses that override
	 * <code>entryArea</code> may override this method.
	 *
	 * @return the number of rows the entry area for this parameter should take up on the display
	 */
	public int displayRows(){
		return 1;
	}

	/**
	 * The <code>entryArea</code> method returns a GUI component that displays the name of the parameter
	 * and, if available, an explanation of its interpretation and allows the user to enter a value.
	 * Subclasses, including anonymous subclasses, may override this method to provide entry areas with
	 * a different appearance.  Callers of this method supply a <code>minimumNameLength</code> parameter
	 * obtained from a <code>ParameterSet</code> containing this parameter.
	 *
	 * @param minimumNameLength the text label length, based on the maximum length among names in the parameter set
	 * @return a JPanel component to add to the GUI
	 */
	public JPanel entryArea(int minimumNameLength){
		FlowLayout parameterPanelLayout = new FlowLayout(FlowLayout.LEADING);
		parameterPanelLayout.setVgap(0);
		JPanel parameterPanel = new JPanel(parameterPanelLayout);
		JLabel parameterLabel = new JLabel(String.format("%" + minimumNameLength + "s", name + ":")); 
		parameterLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
		if (! isEnabled()){
			parameterLabel.setForeground(Color.GRAY);
		}
		parameterPanel.add(parameterLabel);
		parameterPanel.add(textField);
		if (explanation != null){
			JLabel explanationLabel = new JLabel(I_ICON);
			explanationLabel.setToolTipText(explanation.graphicalRepresentation());
			parameterPanel.add(explanationLabel);
		}
		parameterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		entryArea = parameterPanel;
		return parameterPanel;
	}

	/**
	 * The <code>valueString</code> method returns a string representation of the parameter value.
	 * This is just a call to the value <code>Object</code>'s <code>toString()</code> method unless the value
	 * <code>Object</code> has an array class.
	 *
	 * @return a string representation of the value for this parameter
	 */
	public String valueString(){
		if (value == null){
			return "";
		}
		if (value.getClass().isArray()){
			// The array is converted to a string array in case its elements are of a primitive data type.
			String[] stringValues = new String[Array.getLength(value)];
			for (int i = 0; i < Array.getLength(value); i++){
				stringValues[i] = "" + Array.get(value, i);
			}
			return StringUtils.toString(stringValues, " ");
		}
		else{
			return value.toString();
		}
	}

	/**
	 * This <code>toString</code> method is used for writing configuration files in .ctl format.
	 * Callers of this method supply a <code>minimumNameLength</code> parameter obtained from a
	 * <code>ParameterSet</code> containing this parameter.
	 *
	 * @param desiredLength The returned string will be padded with spaces on the left until it reaches this length.
	 *
	 * @return a string representation of this parameter (name, value and explanation) suitable for writing in .ctl format
	 */
	public String toString(int desiredLength){
		StringBuffer rv = new StringBuffer(name).append(" = ").append(valueString()).append(" ");
		String nameAndValue = rv.toString();
		rv = new StringBuffer(String.format("%" + desiredLength + "s", nameAndValue)); 
		if (explanation != null){
			String prefix = String.format("%" + desiredLength + "s", "") + "* "; 
			rv.append(explanation.textRepresentation(prefix));
		}
		return rv.toString();
	}

	/**
	 * The basic <code>toString</code> method returns a string representation of the parameter (name, value
	 * and explanation).
	 *
	 * @return a string representation of this parameter (name, value and explanation)
	 */
	public String toString(){
		return toString(1);
	}

}
