package edu.umaryland.igs.idea;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.WindowConstants;

/**
 * <code>HistogramDialog</code> displays a dialog which allows the user to choose
 * a model upon which a histogram will be based when the user clicks "Display".
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
public class HistogramDialog extends JDialog{
	
	protected StandardSummaryTableModel resultSet;  // the result set from which to derive the histogram
	protected JPanel contents;  // the panel containing the dialog's display element
	protected int selectedColumn;  // the column for which to display a histogram
	protected String selectedModel;  // the model on which to base the histogram; user-selected from a drop-down menu
	final JButton displayButton = new JButton("Display");  // When this button is pressed, the histogram is drawn.

	/**
	 * This is the only  constructor for <code>HistogramDialog</code>.
	 *
	 * @param owner the frame to which the dialog is attached
	 * @param rs the result set from which to derive the histogram
	 * @param column the column for which to display a histogram
	 */
	public HistogramDialog(JFrame owner, SummaryTableModel rs, int column){
		super(owner, "Create Histogram:  " + rs.getColumnName(column));
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		resultSet = (StandardSummaryTableModel) rs;
		selectedColumn = column;
		contents = new JPanel();
		contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
		JLabel prompt = new JLabel("Histogram based on model:");
		prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
		contents.add(prompt);
		ButtonGroup choices = new ButtonGroup();
		for (String modelName : resultSet.getModelNames()){
			JRadioButton choice = new JRadioButton(modelName);
			choice.addActionListener(new ActionListener(){
					public void actionPerformed(ActionEvent e){
						selectedModel = ((JRadioButton) e.getSource()).getText();
						displayButton.setEnabled(true);
					}
				});
			choice.setAlignmentX(Component.LEFT_ALIGNMENT);
			choices.add(choice);
			contents.add(choice);
		}
		contents.add(buttonPanel());
		add(contents);
		pack();
		setSize(getPreferredSize().width * 2, getPreferredSize().height);
	}

	/**
	 * The <code>buttonPanel</code> method returns a panel containing Display and Cancel buttons.
	 *
	 * @return a panel containing Display and Cancel buttons
	 */
	protected JPanel buttonPanel(){
		JPanel rv = new JPanel();
		displayButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					HistogramWindow hw = new HistogramWindow((JFrame) getOwner(), resultSet, selectedColumn, selectedModel);
					dispose();
					hw.setVisible(true);
				}
			});
		displayButton.setEnabled(false);
		rv.add(displayButton);
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					dispose();
				}
			});
		rv.add(cancelButton);				
		rv.setAlignmentX(Component.LEFT_ALIGNMENT);
		return rv;
	}

}
