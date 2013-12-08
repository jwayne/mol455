package edu.umaryland.igs.idea;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.Vector;

import javax.swing.ComboBoxModel;
import javax.swing.InputVerifier;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * <code>StandardFileParameter</code> represents a filename parameter
 * which allows the user to choose among default choices or enter a new one.
 * <code>StandardFileParameter</code> overrides <code>FileParameter</code>
 * to replace the text area with an editable combo box.
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
public class StandardFileParameter extends FileParameter{

	static final long serialVersionUID = -7729594194067920998L;

	protected JComboBox choices;  // an editable combo box allowing the user to select a default choice or enter a new one
	
	/**
	 * This constructs a <code>StandardFileParameter</code> with no default options.
	 * After calling the superclass constructor, it creates the combo box.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 */
	public StandardFileParameter(String n, Object v, ParameterExplanation e){
		this(n, v, e, null);
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 */
	public StandardFileParameter(String n, Object v, String e){
		this(n, v, new ParameterExplanation(e), null);
	}

	/**
	 * This constructs a <code>StandardFileParameter</code> with the specified default options.
	 * After calling the superclass constructor, it creates the combo box.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 * @param defaultOptions a vector containing the standard value choices; may be null
	 */
	public StandardFileParameter(String n, Object v, ParameterExplanation e, Vector defaultOptions){
		super(n, v, e);
		choices = (defaultOptions == null) ? new VerifyingComboBox() : new VerifyingComboBox(defaultOptions);
		choices.setEditable(true);
		if (defaultOptions != null){
			choices.setSelectedIndex(defaultOptions.indexOf(v));
		}
		initializeComboBoxInputVerifier();
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 * @param defaultOptions a vector containing the standard value choices; may be null
	 */
	public StandardFileParameter(String n, Object v, String e, Vector defaultOptions){
		this(n, v, new ParameterExplanation(e), defaultOptions);
	}

	/**
	 * @deprecated
	 */
	protected Object clone(){
		StandardFileParameter rv = (StandardFileParameter) super.clone();
		if (rv.choices.getItemCount() == 0){
			rv.choices = new VerifyingComboBox();
		}
		else{
			Vector<Object> options = new Vector<Object>(choices.getItemCount());
			for (int i = 0; i < rv.choices.getItemCount(); i++){
				options.add(rv.choices.getItemAt(i));
			}
			rv.choices = new VerifyingComboBox(options);
			rv.choices.setSelectedItem(rv.value);			
		}
		rv.initializeComboBoxInputVerifier();
		return rv;
	}

	/**
	 * The <code>initializeComboBoxInputVerifier</code> method adds an input verifier to the combo box's
	 * internal text field (the one seen on the screen) which calls any input verifier currently set
	 * for the <code>textField</code> member inherited from class <code>Parameter</code>, which is not
	 * displayed for a <code>StandardFileParameter</code>.
	 */
	private void initializeComboBoxInputVerifier(){
		JTextField comboBoxInternalTextField = (JTextField) choices.getEditor().getEditorComponent();
		comboBoxInternalTextField.setInputVerifier(new InputVerifier(){
				public boolean verify(JComponent input){
					InputVerifier textFieldVerifier = textField.getInputVerifier();
					return (textFieldVerifier == null) || textFieldVerifier.verify(input);
				}
				public boolean shouldYieldFocus(JComponent input){
					InputVerifier textFieldVerifier = textField.getInputVerifier();
					return (textFieldVerifier == null) || textFieldVerifier.shouldYieldFocus(input);
				}
			});
	}

	/**
	 * The <code>setEnabled</code> overrides the method in class <code>FileParameter</code> to disable the combo box.
	 *
	 * @param enabled whether the parameter should be enabled
	 */
	public void setEnabled(boolean enabled){
		super.setEnabled(enabled);
		choices.setEnabled(enabled);
		choices.invalidate();
		JTextField comboBoxInternalTextField = (JTextField) choices.getEditor().getEditorComponent();
		if (! comboBoxInternalTextField.getInputVerifier().shouldYieldFocus(comboBoxInternalTextField)){
			choices.requestFocusInWindow();
		}
	}

	/**
	 * The <code>entryArea</code> method returns a GUI component that displays the name of the parameter
	 * and, if available, an explanation of its interpretation and allows the user to enter a value.
	 * This overrides the superclass method to pair the file chooser with a combo box instead of a text field.
	 * Callers of this method supply a <code>minimumNameLength</code> parameter obtained from a
	 * <code>ParameterSet</code> containing this parameter.
	 *
	 * @param minimumNameLength the text label length, based on the maximum length among names in the parameter set
	 * @return a JPanel component to add to the GUI
	 */
	public JPanel entryArea(int minimumNameLength){
		FlowLayout parameterPanelLayout = new FlowLayout(FlowLayout.LEADING);
		parameterPanelLayout.setVgap(0);
		JLabel parameterLabel = new JLabel(String.format("%" + minimumNameLength + "s", name + ":")); 
		parameterLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
		final JPanel parameterPanel = new JPanel(parameterPanelLayout);
		parameterPanel.add(parameterLabel);
		if (! isEnabled()){
			choices.setEnabled(false);
		}
		parameterPanel.add(choices);
		choices.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					String selectedFile = choices.getSelectedItem().toString();
					StandardFileParameter.this.value = selectedFile;
				}
			});
		final JFileChooser parameterFileChooser = new JFileChooser(valueString());
		parameterFileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		setBrowseButtonListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					if (parameterFileChooser.showDialog(parameterPanel, "Select") == JFileChooser.APPROVE_OPTION){
						String selectedFile = parameterFileChooser.getSelectedFile().getAbsolutePath();

						InputVerifier verifier = textField.getInputVerifier();
						String oldText = choices.getSelectedItem().toString();
						if ((verifier == null) || verifier.shouldYieldFocus(new JTextField(selectedFile))){
							textField.setText(selectedFile); // This is so Parameter's listener doesn't reset it to the default.
							choices.setSelectedItem(selectedFile);
							StandardFileParameter.this.value = selectedFile;
							
							// All registered action listeners on this parameter are activated when the user selects a file.
							ActionEvent dummyEvent = new ActionEvent(this, 0, null);
							for (ActionListener al : actionListeners){
								al.actionPerformed(dummyEvent);
							}
						}
						else{
							choices.setSelectedItem(oldText);
							choices.requestFocusInWindow();
						}
					}
				}
			});
		if (! isEnabled()){
			browseButton.setEnabled(false);
		}
		browseButton.setMargin(new Insets(0, 0, 0, 0));
		parameterPanel.add(browseButton);
		if (explanation != null){
			JLabel explanationLabel = new JLabel(Parameter.I_ICON);
			explanationLabel.setToolTipText(explanation.graphicalRepresentation());
			parameterPanel.add(explanationLabel);
		}
		parameterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		parameterPanel.setMaximumSize(parameterPanel.getPreferredSize());
		return parameterPanel;
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

// A converter for a previous version's configuration format might look like this:
// 	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
//  		in.defaultReadObject();
// 		setMinTextAreaSize(5);
// 		textField.setInputVerifier(inputVerifier);
// 		LinkedList<ActionListener> copy = new LinkedList<ActionListener>(actionListeners);
// 		for (ActionListener al : copy){
// 			addActionListener(al);
// 		}
// 		actionListeners = copy;
// 		if (! (choices instanceof VerifyingComboBox)){
// 			choices = new VerifyingComboBox(choices.getModel());
// 		}
// 		initializeComboBoxInputVerifier();
// 	}

/**
 * Starting in IDEA 2.3, <code>StandardFileParameter</code>'s <code>choices</code> combo box is an instance of
 * <code>VerifyingComboBox</code>, which extends <code>JComboBox</code> to validate a user's input before allowing it
 * to be selected on the combo box.
 */
	class VerifyingComboBox extends JComboBox{

		/**
		 * The default constructor creates a combo box with no predefined default choices.
		 */
		public VerifyingComboBox(){
			super();
		}

		/**
		 * This is the standard method for creating a <code>VerifyingComboBox</code>.  The first item (if any) in the vector
		 * is automatically selected.
		 *
		 * @param items a vector of default choices for the combo box
		 */
		public VerifyingComboBox(Vector items){
			super(items);
		}

		/**
		 * This constructor is useful when loading a configuration saved with an IDEA version earlier than 2.3.
		 * Standard file parameters in such a saved configuration have combo boxes of class <code>JComboBox</code>
		 * rather than <code>VerifyingComboBox</code>, so a subclass combo box must be constructed from the loaded
		 * combo box's model.
		 *
		 * @param aModel the model for a previous combo box on which this combo box is to be based
		 */
		public VerifyingComboBox(ComboBoxModel aModel){
			super(aModel);
		}

		/**
		 * The <code>setSelectedItem</code> method overrides the method in class <code>JComboBox</code> to perform
		 * input validation before accepting the user's entry or selection.
		 *
		 * @param anObject the user's entry or the user's selection from the existing choices; typically a string
		 */
		public void setSelectedItem(Object anObject){
			if (anObject instanceof String){
				InputVerifier verifier = ((JComponent) getEditor().getEditorComponent()).getInputVerifier();
				if ((verifier == null) || verifier.shouldYieldFocus(new JTextField((String) anObject))){
					super.setSelectedItem(anObject);
				}
			}
		}
	}

}
