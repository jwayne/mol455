package edu.umaryland.igs.idea;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalBorders;
import javax.swing.table.TableCellRenderer;


/**
 * <code>SummaryTableHeaderRenderer</code> is the custom renderer for the
 * header row.  It sets a non-standard background color, adds buttons and
 * uses Greek letters where appropriate.
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
public class SummaryTableHeaderRenderer extends JPanel implements TableCellRenderer{

    private GridBagConstraints constraints = new GridBagConstraints();  // constraints for the panel's grid bag layout

    // A set of button clicks already processed is maintained as a tree set in order to
    // prevent the processing of the same event by multiple mouse listeners.
    final TreeSet<MouseEvent> processedEvents = new TreeSet<MouseEvent>(new Comparator<MouseEvent>(){
									    public int compare(MouseEvent o1, MouseEvent o2){
										return o1.hashCode() - o2.hashCode();
									    }
									});

    final private JFrame owner;  // the GUI containing the table this renderer handles

    /**
     * This constructs a <code>SummaryTableHeaderRenderer</code> associated with the specified
     * GUI and sets some initial properties.  A grid bag layout is used.
     */
    public SummaryTableHeaderRenderer(JFrame ownerWindow) {
	setOpaque(true);
	setForeground(Color.BLACK);
	setBackground(IDEAColors.MAUVE);
	setBorder(new MetalBorders.TableHeaderBorder());
	setLayout(new GridBagLayout());
	constraints.fill = GridBagConstraints.NONE;
	constraints.gridx = 0;
	setAlignmentY(Component.CENTER_ALIGNMENT);
	owner = ownerWindow;
    }

    /**
     * The <code>getTableCellRendererComponent</code> method is the only method in interface
     * <code>TableCellRenderer</code>.  This implementation returns a <code>JPanel</code> which
     * contains, at a minimum, a formatted version of the column name (unless the table is null).
     * One or more buttons may also be rendered.  Because the rendered buttons are inactive,
     * a mouse listener is added to listen for clicks on them.  See the corresponding method in
     * <code>SummaryTableCellRenderer</code>.
     *
     * @param table the table this renderer should draw; can be null
     * @param value the value in the cell to be rendered
     * @param isSelected unused
     * @param hasFocus unused
     * @param row the row index of the cell to be rendered
     * @param column the column index of the cell to be rendered
     *
     * @return a <code>JPanel</code> on which the header is rendered
     */
    public Component getTableCellRendererComponent(final JTable table, Object value, boolean isSelected,
						   boolean hasFocus, int row, final int column){
	if (table != null){
	    removeAll();

	    // Format the column name using HTML.
	    String valueString = (value == null) ? "" : value.toString();
	    if (valueString.equals("n") || valueString.equals("\u03C9") || valueString.equals("\u03BA")){
		valueString = new StringBuffer("<html><i>").append(valueString).append("</i></html>").toString();
	    }
	    if (valueString.equals("dN") || valueString.equals("dS")){
		valueString =
		    new StringBuffer("<html><i>d")
		    .append("</i>")
		    .append(valueString.substring(1, 2))
		    .append("</html>").toString();
	    }
	    JLabel title = new JLabel(valueString);
	    title.setHorizontalAlignment(SwingConstants.CENTER);
	    title.setFont(title.getFont().deriveFont(12.0f));
	    constraints.gridy = 0;
	    constraints.insets = IDEAConstants.ZERO_INSETS;
	    add(title, constraints);

	    final SummaryTableModel model = (SummaryTableModel) table.getModel();

	    // Get a list of buttons to display in this column and create a panel to hold them.
	    LinkedList<String> buttons = model.buttons(column);
	    final JPanel buttonPanel = new JPanel();
	    buttonPanel.setLayout(new GridLayout(1, Math.min(buttons.size(), 1)));
	    buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
	    buttonPanel.setBackground(IDEAColors.MAUVE);
	    constraints.gridy = 1;
	    constraints.insets = new Insets(5, 0, 0, 0);

	    if (buttons.size() == 0){
		// If the model has no buttons for this column, add a vertical strut to size the header correctly.
		final SummaryTableButton dummyButton = new SummaryTableButton("Sort Ascending");
		add(Box.createVerticalStrut(dummyButton.getPreferredSize().height), constraints);
	    }

	    // Creating the buttons loads their icons and derives the names of their methods.
	    for (String buttonName : buttons){
		final SummaryTableButton button = new SummaryTableButton(buttonName);
		buttonPanel.add(button);
	    }
	    add(buttonPanel, constraints);

	    // Add a mouse listener to respond to clicks on the buttons.
	    table.getTableHeader().addMouseListener(new MouseAdapter(){
		    public void mouseClicked(MouseEvent e) {
			Rectangle panelBounds = table.getTableHeader().getHeaderRect(column);
			if (! panelBounds.contains(e.getX(), e.getY())){
 			    return;
 			}
			Rectangle buttonPanelBounds = buttonPanel.getBounds();
			for (Component child : buttonPanel.getComponents()){
			    if (child instanceof SummaryTableButton){
				SummaryTableButton button = (SummaryTableButton) child;
				Rectangle buttonBounds = button.getBounds();
				buttonBounds.translate(buttonPanelBounds.x, buttonPanelBounds.y);
				buttonBounds.translate(panelBounds.x, panelBounds.y);
				if (buttonBounds.contains(e.getX(), e.getY()) && processedEvents.add(e)){  // This button was clicked.
				    String methodName = button.getMethodName();

				    // Attempt to invoke the button method.  Each button method takes an int and a JFrame.
				    try{
					int selectedRowIndex = table.getSelectedRow();
					Vector<Object> previouslySelectedRow = null;
					if (selectedRowIndex != -1){
					    int rowIndex = 0;
					    DATASET: for (LinkedList<Vector<Object>> dataset : model.data){
						for (Vector<Object> model : dataset){
						    if (rowIndex == selectedRowIndex){
							previouslySelectedRow = model;
							break DATASET;
						    }
						    rowIndex++;
						}
					    }
					}
					model.getClass().getMethod(methodName, int.class, JFrame.class).invoke(model, column, owner);
					if (previouslySelectedRow != null){
					    int rowIndex = 0;
					    DATASET: for (LinkedList<Vector<Object>> dataset : model.data){
						for (Vector<Object> model : dataset){
						    if (model.equals(previouslySelectedRow)){
							table.setRowSelectionInterval(rowIndex, rowIndex);
							break DATASET;
						    }
						    rowIndex++;
						}
					    }
					}
					table.invalidate();
					table.revalidate();
					table.repaint();
				    }
				    catch (NoSuchMethodException nsme){
					JOptionPane.showMessageDialog(owner,
								      "The function \"" + methodName
								      + "\" is not supported.\nPlease report this problem.\n",
								      "Unsupported Function",
								      JOptionPane.ERROR_MESSAGE);
				    }
				    catch (IllegalAccessException iae){
					JOptionPane.showMessageDialog(owner,
								      "The function \"" + methodName
								      + "\" could not be accessed.\nPlease report this problem.\n",
								      "Inaccessible Function",
								      JOptionPane.ERROR_MESSAGE);
				    }
				    catch (InvocationTargetException ite){
					JOptionPane.showMessageDialog(owner,
								      "The function \"" + methodName
								      + "\" failed.\nPlease report this problem.\n",
								      "Illegal Exception",
								      JOptionPane.ERROR_MESSAGE);
					ite.getCause().printStackTrace();
				    }
				}
			    }
			}
		    }
		});
	}
	return this;
    }
	
}
