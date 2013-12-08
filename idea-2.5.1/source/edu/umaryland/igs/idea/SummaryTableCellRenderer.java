package edu.umaryland.igs.idea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.MouseInputAdapter;
import javax.swing.table.TableCellRenderer;

import edu.umaryland.igs.aegan.utils.StreamGobbler;

/**
 * <code>SummaryTableCellRenderer</code> is the custom renderer for all
 * rows except the header row.  It displays some cells in bold or different
 * colors, including white for invisibility.  It also adds listeners for
 * mouse clicks on the cells.
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
public class SummaryTableCellRenderer extends JPanel implements TableCellRenderer{

    final private IDEAOutputGUI owner;  // the GUI containing the table this renderer handles
    private int lastSelectedRow = -1;  // the row most recently selected by the user
    private JTable domain;  // the table on which this renderer draws cells
    private JLabel text;  // experimental:  for increased memory efficiency, define one label and use it for each cell
    final private JPanel treePanel = new JPanel();;
    final private JButton treeButton = new JButton("Tree");
    private JLabel treeLabel;
    final private JPanel innerWrapper = new JPanel();
    final private JPanel modelPanel = new JPanel();
    final private JButton detailsHyperlink = new JButton();
    private JLabel substituteLabel;
    private MouseListener[][] registeredCellListeners;

    // A set of hyperlink or tree-button events already processed is maintained as a tree set
    // in order to prevent the processing of the same event by multiple mouse listeners.
    final TreeSet<MouseEvent> processedEvents = new TreeSet<MouseEvent>(new Comparator<MouseEvent>(){
									    public int compare(MouseEvent o1, MouseEvent o2){
										return o1.hashCode() - o2.hashCode();
									    }
									});

    // A set of row-selection events already processed is maintained as a tree set in order
    // to prevent the processing of the same event by multiple mouse listeners.
    final TreeSet<MouseEvent> ssProcessedEvents = new TreeSet<MouseEvent>(new Comparator<MouseEvent>(){
									      public int compare(MouseEvent o1, MouseEvent o2){
										  return o1.hashCode() - o2.hashCode();
									      }
									  });

    /**
     * This constructs a <code>SummaryTableCellRenderer</code> associated with the specified
     * GUI and sets some initial properties.
     */
    public SummaryTableCellRenderer(IDEAOutputGUI ownerWindow) {
	setOpaque(true);
	setForeground(Color.BLACK);
	setBackground(Color.WHITE);
	setFont(new Font("Monospaced", Font.PLAIN, 10));
	setAlignmentX(Component.RIGHT_ALIGNMENT);
	setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
	owner = ownerWindow;
	text = new JLabel();
	treeLabel = new JLabel();
	substituteLabel = new JLabel();
    }

    /**
     * The <code>getToolTipText</code> method passes a <code>MouseEvent</code> recognized by the <code>JTable</code>'s
     * <code>getToolTipText</code> method on to a <code>MouseOverListener</code>, which can detect whether it corresponds
     * to a mouse-over event on a mouse-over hotspot.
     *
     * @param e a <code>MouseEvent</code> that triggered <code>getToolTipText</code> on the table
     *
     * @return tool tip text to display, based, if applicable, on the <code>LikelihoodData</code> object's custom rendering
     */
    public String getToolTipText(MouseEvent e){
	Point mouseLocationOnTable = e.getPoint();
	int rowIndex = domain.rowAtPoint(mouseLocationOnTable);
	int viewColumnIndex = domain.columnAtPoint(mouseLocationOnTable);
	Object cellContents = domain.getValueAt(rowIndex, viewColumnIndex);
	if (cellContents instanceof LikelihoodData){
	    LikelihoodData lrtResults = (LikelihoodData) cellContents;
	    if (lrtResults.tested){
		for (MouseMotionListener l : domain.getMouseMotionListeners()){
		    if ((l instanceof MouseOverListener) && ((MouseOverListener) l).isWithinHotspot(e)){
			return lrtResults.details();
		    }
		}
	    }
	}
	return super.getToolTipText(e);
    }

    /**
     * The <code>getTableCellRendererComponent</code> method is the only method in interface
     * <code>TableCellRenderer</code>.  This implementation returns a <code>JPanel</code> which
     * contains, at a minimum, the string value for the cell (unless the table is null).
     * Selected cells and certain other cells are rendered specially.  In non-pairwise mode,
     * a mouse listener is added to the table.  Because this method is executed every time a
     * cell is rendered, mouse listeners could proliferate and prove problematic during a
     * prolonged execution, even though at most one listener will take any action required in
     * response to a mouse click.  I have not yet found a solution to this problem.
     *
     * @param table the table this renderer should draw; can be null
     * @param value the value in the cell to be rendered
     * @param isSelected true if this cell (for IDEA's purposes, this row) is selected
     * @param hasFocus unused
     * @param row the row index of the cell to be rendered
     * @param column the column index of the cell to be rendered
     *
     * @return a <code>JPanel</code> on which the cell's contents are appropriately rendered
     */
    public Component getTableCellRendererComponent(final JTable table, Object value, boolean isSelected,
						   boolean hasFocus, final int row, final int column){
	if (table == null){  // If there's no table, return an empty JPanel.
	    return this;
	}
	domain = table;
	if (registeredCellListeners == null){
	    registeredCellListeners = new MouseListener[table.getRowCount()][table.getColumnCount()];
	}
	final SummaryTableModel model = (SummaryTableModel) table.getModel();
	if (model == null){
	    System.err.println("FATAL ERROR:  No Table Model");
	    System.exit(1);
	}
		
	// Create a text label with the cell's value.
	final String columnName = table.getColumnName(column);
	//JLabel text = new JLabel();
	final String valueString = (value == null) ? "" : value.toString();
	text.setText(valueString);
	text.setOpaque(true);

	// Draw an outline around the model-specific cells in the selected row.
	int leftEdge = model.leftmostColumnToDisplayAsSelected();
	if (isSelected){
	    setBorder(BorderFactory.createMatteBorder((column < leftEdge) ? 0 : 1,
						      (column == leftEdge) ? 1 : 0,
						      (column < leftEdge) ? 0 : 1,
						      (column == table.getColumnCount() - 1) ? 1 : 0,
						      IDEAColors.SUBTLE_PURPLE));
	}
	else{
	    setBorder(BorderFactory.createEmptyBorder());
	}

	// Set the background color.  All rows for a dataset should have the same background color.
	final String datasetIdentifier = (String) model.getValueAt(row, model.COLUMN_INDICES().get("Dataset"));
	if (model.isStarterRow(row)){
	    model.assignColorTo(datasetIdentifier);
	}
	text.setBackground(model.getColor(datasetIdentifier));

	// Only display Dataset and n for the first model for each dataset.
	if (columnName.equals("Dataset")  ||  columnName.equals("n")){
	    if (! model.isStarterRow(row)){
		text.setForeground(text.getBackground());
	    }
	    else{
		text.setForeground(Color.BLACK);
	    }
	    text.setFont(getFont().deriveFont(Font.PLAIN));
	}
	else{
	    // If data is unavailable, display available columns in grey.
	    String stringOmegaValue =
		(String) model.getValueAt(row, model.COLUMN_INDICES().get(SummaryTableModel.OMEGA));
	    if (stringOmegaValue.equals("")){
		text.setForeground(Color.GRAY);
	    }
	    else{
		double omegaValue =
		    Double.parseDouble((String) model.getValueAt(row,
								 model.COLUMN_INDICES().get(SummaryTableModel.OMEGA)));
				
		// Highlight rows with omega > 1.
		if (omegaValue >= 1.0){
		    text.setForeground(IDEAColors.RASPBERRY);
		}
		else{
		    text.setForeground(Color.BLACK);
		}
	    }

	    // The row for the likeliest model for each dataset should be shown in bold.
	    if (model.highlightRowBasedOnLikelihood(row)){
		text.setFont(getFont().deriveFont(Font.BOLD));
	    }
	    else{
		text.setFont(getFont().deriveFont(Font.PLAIN));
	    }
	}

	// Align numbers to the right and strings to the left.
	try{
	    Double.parseDouble(valueString);
	    text.setHorizontalAlignment(SwingConstants.TRAILING);
	}
	catch (NumberFormatException nfe){
	    text.setHorizontalAlignment(SwingConstants.LEADING);
	}
	setLayout(new GridLayout(1,
				 (model.displayTreeButtonInColumn(column)
				  ? 2
				  : 1)));

	// Because there is only one renderer for a table, we must remove any components displayed in the last cell rendered
	// before adding components to be displayed in this cell.
	removeAll();
	treePanel.removeAll();
	innerWrapper.removeAll();
	modelPanel.removeAll();

	boolean mac = IDEAConstants.OS.startsWith("Mac OS");
    
	treePanel.setLayout(new BoxLayout(treePanel, BoxLayout.X_AXIS));
	treePanel.setBackground(text.getBackground());
	treePanel.add(Box.createHorizontalGlue());
	treeButton.setAlignmentY(Component.CENTER_ALIGNMENT);
	treeButton.setMargin(new Insets(-3, -1, -4, -2));
	treeButton.setFont(treeButton.getFont().deriveFont(Font.PLAIN));
	treeButton.setBackground(text.getBackground());
	if (mac){
	    //Border emptyBorder = BorderFactory.createEmptyBorder(IDEAConstants.ZERO_INSETS);
	    treeButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0),
								    new LineBorder(IDEAColors.BORDER_GRAY, 1, true)));
	}
	treeButton.setContentAreaFilled(false);
	treeButton.setOpaque(false);
	//treeButton.setMaximumSize(treeButton.getPreferredSize());
	treeLabel.setText(treeButton.getText());
	treeLabel.setBorder(BorderFactory.createLineBorder(Color.BLUE));
	treeButton.setMaximumSize(new Dimension(treeLabel.getPreferredSize().width + 5, treeLabel.getPreferredSize().height));
	treeButton.setMinimumSize(new Dimension(Math.min(treeButton.getMinimumSize().width,
							 treeButton.getMaximumSize().width),
						Math.min(treeButton.getMinimumSize().height,
							 treeButton.getMaximumSize().height)));
	treeButton.setPreferredSize(new Dimension(Math.min(treeButton.getPreferredSize().width,
							   treeButton.getMaximumSize().width),
						  Math.min(treeButton.getPreferredSize().height,
							   treeButton.getMaximumSize().height)));			
	if (model.displayTreeButtonInColumn(column)){
	    // Add a tree button if appropriate.
	    innerWrapper.setLayout(new BoxLayout(innerWrapper, BoxLayout.Y_AXIS));
	    innerWrapper.setBackground(text.getBackground());
	    innerWrapper.add(Box.createVerticalGlue());
	    innerWrapper.add(treeButton, BorderLayout.CENTER);
	    innerWrapper.add(Box.createVerticalGlue());
	    innerWrapper.setMaximumSize(new Dimension(treeButton.getMaximumSize().width,
						      innerWrapper.getMaximumSize().height));
	    innerWrapper.setMinimumSize(new Dimension(treeButton.getMinimumSize().width,
						      innerWrapper.getMinimumSize().height));
	    innerWrapper.setPreferredSize(new Dimension(treeButton.getPreferredSize().width,
							innerWrapper.getPreferredSize().height));
	    treePanel.add(innerWrapper);
	    add(treePanel);
	}
	detailsHyperlink.setText("<html><a href=\"\">" + valueString + "</a></html>");

	Component cr = null;
	if (value instanceof CustomRenderable){
	    setLayout(new BorderLayout());
	    add(text, BorderLayout.CENTER);
	    cr = ((CustomRenderable) value).customRendering();
	    cr.setBackground(text.getBackground());
	    add(cr, BorderLayout.EAST);
	}
	// The value should be displayed either as a link or as text, as appropriate.
	else if (columnName.equals("Model")){
	    modelPanel.setLayout(new BoxLayout(modelPanel, BoxLayout.X_AXIS));
	    detailsHyperlink.setAlignmentX(Component.LEFT_ALIGNMENT);
	    detailsHyperlink.setHorizontalAlignment(SwingConstants.LEADING);
	    Insets defaultInsets = detailsHyperlink.getMargin();
	    detailsHyperlink.setMargin(new Insets(-3, mac ? 3 : -2, -4, mac ? 4 : -2));
	    detailsHyperlink.setFont(detailsHyperlink.getFont().deriveFont(Font.PLAIN));
	    detailsHyperlink.setBorderPainted(false);
	    detailsHyperlink.setContentAreaFilled(false);
	    detailsHyperlink.setMaximumSize(detailsHyperlink.getPreferredSize());
	    substituteLabel.setText(detailsHyperlink.getText());
	    detailsHyperlink.setMaximumSize(substituteLabel.getPreferredSize());
	    detailsHyperlink.setMinimumSize(new Dimension(detailsHyperlink.getMinimumSize().width,
							  Math.min(detailsHyperlink.getMinimumSize().height,
								   detailsHyperlink.getMaximumSize().height)));
	    modelPanel.add(detailsHyperlink);
	    modelPanel.add(Box.createHorizontalGlue());
	    modelPanel.setBackground(text.getBackground());
	    add(modelPanel);
	}
	else{
	    add(text);
	}
	final Component customRendering = cr;
	setBackground(text.getBackground());

	// If necessary, increase the table's row height to accommodate this cell's contents.
	if (table.getRowHeight() < getMinimumSize().height){
	    table.setRowHeight(getMinimumSize().height);  // Could this be what's recreating the renderers????
	}
	if (model instanceof StandardSummaryTableModel){  // No mouse listeners are necessary in pairwise mode.
	    if ((customRendering != null) && (customRendering instanceof JComponent)){
		JComponent jcr = (JComponent) customRendering;
		if (jcr.getToolTipText() != null){
		    table.addMouseMotionListener(new MouseOverListener(table, row, column, jcr));
		}
	    }
	    MouseListener oldListener = registeredCellListeners[row][column];
	    if (oldListener != null){
		table.removeMouseListener(oldListener);
	    }
	    MouseListener newListener = new MouseAdapter(){
		    public void mouseClicked(MouseEvent e){
			// Determine the location of the click and respond appropriately.
			Rectangle cellBounds = table.getCellRect(row, column, true);
			if (! cellBounds.contains(e.getX(), e.getY())){
			    return;
			}
			Rectangle treePanelBounds = treePanel.getBounds();
			Rectangle innerWrapperBounds = innerWrapper.getBounds();
			Rectangle treeButtonBounds = treeButton.getBounds();
			treeButtonBounds.translate(innerWrapperBounds.x, innerWrapperBounds.y);
			treeButtonBounds.translate(treePanelBounds.x, treePanelBounds.y);
			treeButtonBounds.translate(cellBounds.x, cellBounds.y);
			if (model.displayTreeButtonInColumn(column) && treeButtonBounds.isEmpty()){
			    return;  // Wrong listener!!  Banish it!
			}
			
			Rectangle modelPanelBounds = modelPanel.getBounds();
			Rectangle hyperlinkBounds = detailsHyperlink.getBounds();
			hyperlinkBounds.translate(modelPanelBounds.x, modelPanelBounds.y);
			hyperlinkBounds.translate(cellBounds.x, cellBounds.y);
			
			// Row-selection events can co-occur with tree-button or hyperlink clicks.  These events can be processed
			// in parallel, but in the case of a simultaneous row selection and tree-button click, we require the
			// selected sites display to be drawn before the tree.
			boolean drawTree = model.displayTreeButtonInColumn(column) && treeButtonBounds.contains(e.getX(), e.getY()) && processedEvents.add(e);
			if (cellBounds.contains(e.getX(), e.getY()) && ssProcessedEvents.add(e) && (lastSelectedRow != row)){
			    lastSelectedRow = row;
			    owner.displaySelectedSites(row, drawTree);
			}
			else{
			    if (drawTree){
				owner.displayTree(datasetIdentifier,
						  (String) model.getValueAt(row, model.COLUMN_INDICES().get("Model")));
			    }
			}
			if (table.getColumnName(column).equals("Model") && hyperlinkBounds.contains(e.getX(), e.getY()) && processedEvents.add(e)){  // The hyperlink was clicked.
			    owner.displayDetails(row);
			}
		    }
		};
	    table.addMouseListener(newListener);
	    registeredCellListeners[row][column] = newListener;
	}
	return this;
    }

}

/**
 * The class <code>MouseOverListener</code> serves to replace the function of the tool tip because tool tips on
 * rendered components are inactive.  Each <code>MouseOverListener</code> has a table, row and column with which it
 * is associated and a component on which it is listening.
 */
class MouseOverListener extends MouseMotionAdapter{
	
    // A set of mouse-over events already processed is maintained as a tree set
    // in order to prevent the processing of the same event by multiple listeners.
    static TreeSet<MouseEvent> processedMouseOvers = new TreeSet<MouseEvent>(new Comparator<MouseEvent>(){
										 public int compare(MouseEvent o1, MouseEvent o2){
										     return o1.hashCode() - o2.hashCode();
										 }
									     });
    // A set of mouse-move events already processed is maintained as a tree set
    // in order to prevent the processing of the same event by multiple listeners.
    static TreeSet<MouseEvent> processedMouseMoves = new TreeSet<MouseEvent>(new Comparator<MouseEvent>(){
										 public int compare(MouseEvent o1, MouseEvent o2){
										     return o1.hashCode() - o2.hashCode();
										 }
									     });

    private JTable domain;  // the table on which this listener is active
    private int row;  // the row index of the cell to which this listener applies
    private int column;  // the colum index of the cell to which this listener applies
    private JComponent mouseOverHotspot;  // the component on which this listener is listening

    /**
     * The constructor for <code>MouseOverListener</code> takes a table, a row index, a column index and a component which
     * should generate events on mouse over.
     *
     * @param d the table on which this listener is active
     * @param r the row index of the cell to which this listener applies
     * @param c the column index of the cell to which this listener applies
     * @param moh the component on which this listener should listen
     */
    public MouseOverListener(JTable d, int r, int c, JComponent moh){
	super();
	domain = d;
	row = r;
	column = c;
	mouseOverHotspot = moh;
    }

    /**
     * The <code>toString</code> method in class <code>MouseOverListener</code> is for debugging purposes.
     *
     * @return a <code>String</code> useful for debugging purposes
     */
    public String toString(){
	return "MOL " + hashCode() + " (" + row + ", " + column + ")";
    }

    /**
     * This calls the two-arg <code>mouseMoved</code> method.
     *
     * @param e a mouse event on the table
     */
    public void mouseMoved(MouseEvent e){
	mouseMoved(e, false);
    }

    /**
     * This listener doesn't currently do anything; its purpose is to implement the isWithinHotspot method.
     *
     * @param e a mouse event on the table
     * @param recordAsToolTipRequest whether this method was called indirectly via <code>getToolTipText</code> in <code>SummaryTableHeaderRenderer</code>
     */
    public void mouseMoved(MouseEvent e, boolean recordAsToolTipRequest){
	if (e.getID() == MouseEvent.MOUSE_MOVED){
	    Rectangle cellBounds = domain.getCellRect(row, column, true);
	    Rectangle activeBounds = mouseOverHotspot.getBounds();
	    activeBounds.translate(cellBounds.x, cellBounds.y);
	    if ((activeBounds.contains(e.getX(), e.getY())) && (recordAsToolTipRequest || processedMouseMoves.add(e))){
		if (recordAsToolTipRequest){
		    processedMouseOvers.add(e);
		}
	    }
	}
    }
	
    /**
     * The <code>isWithinHotspot</code> method serves to pass a <code>MouseEvent</code> on to the listener.
     *
     * @param e a <code>MouseEvent</code> which triggered <code>getToolTipText</code> on the table
     *
     * @return whether the event put the mouse within the bounds of the component on which this listener is listening
     */
    public boolean isWithinHotspot(MouseEvent e){
	int oldSize = processedMouseOvers.size();
	mouseMoved(e, true);
	return processedMouseOvers.size() > oldSize;
    }

}
