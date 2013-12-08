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

import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * <code>FileParameter</code> represents a user-specifiable filename parameter
 * which determines IDEA behavior.  This class overrides
 * <code>Parameter</code> to provide an entry area which includes a "Browse"
 * button that brings up a file chooser.
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
public class FileParameter extends Parameter{

	static final long serialVersionUID = 1336444759224157078L;

	protected JButton browseButton = new JButton("Browse...");  // When clicked, this button brings up a file chooser.

	// The browse button has only one listener.  This listener calls all listeners on the <code>FileParameter</code>.
	protected ActionListener browseButtonListener;

	/**
	 * This is the basic constructor for <code>FileParameter</code>.
	 * It calls the superclass constructor, which creates the text field and its listeners.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e the parameter explanation
	 */
	public FileParameter(String n, Object v, ParameterExplanation e){
		super(n, v, e);
		textField.setColumns(25);
		browseButton.setFont(browseButton.getFont().deriveFont(10.0f));
	}

	/**
	 * This convenience constructor creates a new <code>ParameterExplanation</code> from a one-line String.
	 *
	 * @param n the parameter name
	 * @param v the parameter value
	 * @param e a String containing the text of the one-line parameter explanation
	 */
	public FileParameter(String n, Object v, String e){
		this(n, v, new ParameterExplanation(e));
	}

	/**
	 * @deprecated
	 */
	protected Object clone(){
		FileParameter rv = (FileParameter) super.clone();
		rv.browseButton = new JButton("Browse...");
		rv.browseButtonListener = null;
		return rv;
	}

	/**
	 * The <code>setEnabled</code> overrides the method in class <code>Parameter</code> to disable the Browse button.
	 *
	 * @param enabled whether the parameter should be enabled
	 */
	public void setEnabled(boolean enabled){
		super.setEnabled(enabled);
		browseButton.setEnabled(enabled);
		browseButton.invalidate();
	}

	/**
	 * The <code>setBrowseButtonListener</code> method adds the specified listener to the Browse button and removes
	 * any previous listener.  This is necessary to prevent duplicate listeners when the user displays a GUI page
	 * more than once.
	 *
	 * @param al the new <code>ActionListener</code> for the Browse button
	 */
	protected void setBrowseButtonListener(ActionListener al){
		if (browseButtonListener != null){
			browseButton.removeActionListener(browseButtonListener);
		}
		browseButton.addActionListener(al);
		browseButtonListener = al;
	}

	/**
	 * The <code>entryArea</code> method returns a GUI component that displays the name of the parameter
	 * and, if available, an explanation of its interpretation and allows the user to enter a value.
	 * This overrides the superclass method to provide a "Browse" button that brings up a file chooser.
	 * Callers of this method supply a <code>minimumNameLength</code> parameter obtained from a
	 * <code>ParameterSet</code> containing this parameter.
	 *
	 * @param minimumNameLength the text label length, based on the maximum length among names in the parameter set
	 * @return a JPanel component to add to the GUI
	 */
	public JPanel entryArea(int minimumNameLength){
		
		// The panel is arranged in a (horizontal) flow layout.
		FlowLayout parameterPanelLayout = new FlowLayout(FlowLayout.LEADING);

		// The vertical gap is set to zero to allow parameter entry areas to be displayed close together.
		parameterPanelLayout.setVgap(0);

		// The label with the parameters name is padded with spaces on the left so the labels for parameters in a column are
		// right-aligned.
		JLabel parameterLabel = new JLabel(String.format("%" + minimumNameLength + "s", name + ":")); 
		parameterLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
		final JPanel parameterPanel = new JPanel(parameterPanelLayout);
		parameterPanel.add(parameterLabel);

		// The text field (where the user may enter a file name) is displayed to the right of the parameter's name.
		parameterPanel.add(textField);

		// A file chooser is activated when the user clicks the Browse button (browseButton, a field in this class).
		final JFileChooser parameterFileChooser = new JFileChooser(valueString());
		parameterFileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		setBrowseButtonListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					if (parameterFileChooser.showDialog(parameterPanel, "Select") == JFileChooser.APPROVE_OPTION){
						String selectedFile = parameterFileChooser.getSelectedFile().getAbsolutePath();

						InputVerifier verifier = textField.getInputVerifier();
						String oldText = textField.getText();
						if ((verifier == null) || verifier.shouldYieldFocus(new JTextField(selectedFile))){
							textField.setText(selectedFile);
							FileParameter.this.value = selectedFile;
							
							// All registered action listeners on this parameter are activated when the user selects a file.
							ActionEvent dummyEvent = new ActionEvent(this, 0, null);
							for (ActionListener al : actionListeners){
								al.actionPerformed(dummyEvent);
							}
						}
						else{
							textField.setText(oldText);
							textField.requestFocusInWindow();
						}
					}
				}
			});
		if (! isEnabled()){
			browseButton.setEnabled(false);
		}
		browseButton.setMargin(new Insets(0, 0, 0, 0));  // This makes the browse button's size smaller than the default.
		parameterPanel.add(browseButton);

		// If there is a tool tip, the help icon is displayed to the right of the Browse button.
		if (explanation != null){
			JLabel explanationLabel = new JLabel(Parameter.I_ICON);
			explanationLabel.setToolTipText(explanation.graphicalRepresentation());
			parameterPanel.add(explanationLabel);
		}
		parameterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		parameterPanel.setMaximumSize(parameterPanel.getPreferredSize());  // Keep the entry area compact.
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

}

