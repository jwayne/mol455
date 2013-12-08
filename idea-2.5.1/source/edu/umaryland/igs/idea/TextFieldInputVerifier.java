package edu.umaryland.igs.idea;

import java.io.Serializable;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import edu.umaryland.igs.aegan.utils.WrapperPane;

/**
 * <code>TextFieldInputVerifier</code> is a convenience extension of <code>InputVerifier</code>
 * that passes calls to verify through to its own verifyText method, which subclasses define.
 * This is the base classe for all IDEA input validators that validate parameters' text fields.
 * All cloning-related code in these classes is not currently supported.
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

public abstract class TextFieldInputVerifier extends InputVerifier implements Cloneable, Serializable{

	protected String errorMessage;  // an error message that may be stored when input is invalid

	/**
	 * @deprecated
	 */
	public Object clone() throws CloneNotSupportedException{
		throw new CloneNotSupportedException();
	}
	
	/**
	 * @deprecated
	 */
	void setCloneConfig(IDEAConfiguration alreadyCopiedConfig){
	}

	/**
	 * The <code>verify</code> method is declared by <code>InputVerifier</code>.
	 * <code>TextFieldInputVerifier</code>'s implementation obtains the text field's <code>String</code> contents
	 * and calls <code>verifyText</code> on those contents.  This allows subclasses to deal directly with strings.
	 * Adding any input verifier to a parameter actually adds a substitute input verifier that automatically fails
	 * any <code>JComponent</code> that is not a <code>JTextField</code>, so this class can assume that the supplied
	 * component is a text field.
	 *
	 * @param input the component to validate; always a <code>JTextField</code>
	 *
	 * @return whether the text field's text is valid according to a subclass
	 */
	public boolean verify(JComponent input){
		if (! input.isEnabled()){
			return true;
		}
		String text = ((JTextField) input).getText();
		return (text == null) || (text.length() == 0) || verifyText(text);
	}

	/**
	 * The <code>shouldYieldFocus</code> method is declared by <code>InputVerifier</code>.
	 * <code>TextFieldInputVerifier</code>'s implementation displays an error dialog if input is invalid.
	 *
	 * @param input the component to validate; always a <code>JTextField</code>
	 *
	 * @return whether the text field's text is valid according to a subclass
	 */
	public boolean shouldYieldFocus(JComponent input){
		String candidateText = ((JTextField) input).getText();
		if (verify(input)){
			return true;
		}
		else{
			warn(candidateText);
			return false;
		}
	}

	/**
	 * The <code>verifyText</code> method should return whether the text meets a particular input verifier's requirements.
	 *
	 * @param text the text to validate
	 * 
	 * @return whether the text is valid
	 */
	public abstract boolean verifyText(String text);

	/**
	 * The <code>errorMessage</code> method should return a descriptive explanation of the reason for an entry's failure.
	 * Subclass implementations should not call <code>verifyText</code> but may return error messages saved during an
	 * earlier call to <code>verifyText</code>.
	 *
	 * @param text the text which was found to be invalid during a previous validation attempt
	 *
	 * @return a descriptive error message explaining why the entry is invalid
	 */
	abstract String errorMessage(String text);

	/**
	 * The <code>warn</code> method shows an error dialog with the subclass-defined error message.
	 *
	 * @param inputText the text which was found to be invalid during a previous validation attempt
	 */
	void warn(String inputText){
		WrapperPane.showMessageDialog(null, errorMessage(inputText), "Invalid Input", JOptionPane.ERROR_MESSAGE); // no owner
	}

}
