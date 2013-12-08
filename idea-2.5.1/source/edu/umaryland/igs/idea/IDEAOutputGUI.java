package edu.umaryland.igs.idea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.awt.image.ReplicateScaleFilter;
import java.awt.image.RGBImageFilter;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.BadLocationException;

// The PDFBox library is used here to convert PDFs (in this case representing phylogenetic trees) to images.
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentCatalog;
import org.pdfbox.pdmodel.PDPage;

import edu.umaryland.igs.aegan.utils.FileClerk;
import edu.umaryland.igs.aegan.utils.FileParser;
import edu.umaryland.igs.aegan.utils.StreamGobbler;
import edu.umaryland.igs.aegan.utils.StreamSaver;
import edu.umaryland.igs.aegan.utils.SwingThreadSafeComponent;
import edu.umaryland.igs.aegan.utils.WrapperPane;
import org.tigr.antware.shared.util.ExceptionHandler;
import org.tigr.antware.shared.util.Logger;

/**
 * <code>IDEAOutputGUI</code> is the main class for the output portion of
 * the IDEA suite, which is allow users to view results of completed analyses.
 * It is invoked when the user selects "View Results of Previous Analysis" or
 * "Load previous results" from the input GUI.
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
public class IDEAOutputGUI extends JFrame {
	/**
	 * The variable <code>logger</code> holds the instance of the logger for this class.
	 */
	private static Logger logger = new Logger("edu.umaryland.igs.idea.IDEAOutputGUI");
    
	// This is the directory containing standard configuration files for IDEA.
	static final String IDEA_DIR = System.getProperty("ideaDir");

	static final ImageIcon LOGO;  // the logo displayed above the tree area in standard mode

	static{
		// This logo is only used within the output GUI, so don't bother loading and scaling it until this class is loaded,
		// since it may never be loaded.
		LOGO =
	    new ImageIcon(new ImageIcon(ClassLoader.getSystemResource("idea-logo-original.jpg"))
										.getImage().getScaledInstance(-1, IDEAConstants.SCREEN_SIZE.height * 10 / 100, Image.SCALE_SMOOTH));
	}

	private String currentName;  // name of tree file being displayed
	protected JTable dataTable;  // the data table containing summary results from a completed IDEA run
	protected JScrollPane tablePane;  // a scroll pane for the data table
	protected JSplitPane mainPane;  // a split pane with the table and tree display on top and selected sites display below
	protected JPanel selectedSitesArea;  // a panel in which a display of selected sites may be shown
	protected JPanel treeArea; // a panel in which the IDEA logo is shown; combo boxes, a button and a tree may also appear
	private SummaryTableModel dataModel;  // the table data and associated logic
	JComboBox datasetSelector;  // a drop-down box that allows the user to select a dataset for which to display a tree
	JComboBox modelSelector;  // a drop-down box that allows the user to select a model for which to display a tree
	private String outputDirectory;  // the directory containing the previously generated results to display
	private String drawnTree; // "<dataset>, <model>" for which a successfully drawn tree is visible; may be null
    
	/**
	 * This creates a new <code>IDEAOutputGUI</code> instance, builds the UI
	 * components and displays them.
	 *
	 */
	IDEAOutputGUI(){
		super();
		if (logger.isInfoEnabled()){
	    logger.info("Launching IDEA output GUI...");
		}
		setTitle("IDEA " + IDEAConfiguration.IDEA_VERSION);
		setIconImage(IDEAConstants.LOGO_THUMB);
      
		// Initialize the GUI.
		initialize();  
	}

	/**
	 * This creates a new <code>IDEAOutputGUI</code> instance and loads results from the specified directory.
	 *
	 * @param resultsDir the directory containing the results to load
	 */
	IDEAOutputGUI(String resultsDir){
		this();
		if (resultsDir != null){
	    load(resultsDir);
		}
	}

	/**
	 * The <code>initialize</code> method builds and displays a GUI with no data loaded.  It is called when the output
	 * GUI is launched and is also called when loading results fails (to clear invalid results and/or remnants of the
	 * previously loaded results).
	 */    
	private void initialize() {
		outputDirectory = null;
		drawnTree = null;
		dataModel = new StandardSummaryTableModel();
		datasetSelector = new JComboBox(new Vector<String>(dataModel.getDatasetNames()));
		modelSelector = new JComboBox(new Vector<String>(((StandardSummaryTableModel) dataModel).getModelNames()));

		addWindowListener(new WindowAdapter()  {
				public void windowClosing(WindowEvent e)  {
					System.exit(0);
				}
	    });
        
		// Build the GUI panels.
		setJMenuBar(menuBar());
		createSplitPane(true, true, true, null);
        
		setLocation(IDEAConstants.SCREEN_SIZE.x, IDEAConstants.SCREEN_SIZE.y);
		setSize(IDEAConstants.SCREEN_SIZE.width, IDEAConstants.SCREEN_SIZE.height - 20);
		setVisible(true);  
        
	}

	/**
	 * The <code>setSelectedTree</code> method updates the dataset and model selectors to the specified values.
	 * This is used when a tree is displayed by clicking a Tree button in the table.
	 *
	 * @param dataset the dataset whose tree is being displayed
	 * @param model the model for the tree being displayed
	 */
	void setSelectedTree(String dataset, String model){
		datasetSelector.setSelectedItem(dataset);
		modelSelector.setSelectedItem(model);
	}

	/**
	 * The <code>showProgressBar</code> method creates a new progress bar in indeterminate mode, arranges it within panels,
	 * sets the top-level panel as the bottom component of the main pane and returns the newly created progress bar.
	 * This method sets the cursor to a wait cursor and disables the GUI using a glass pane.  The caller is responsible
	 * for undoing both of these actions in both expected and error cases.  This method should only be called from the
	 * event dispatch thread.
	 *
	 * @return a newly created progress bar in indeterminate mode, already incorporated into the GUI
	 */
	private SwingThreadSafeComponent<JProgressBar> showProgressBar(){
		// A wait cursor is used while the long-running task executes.
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		// A progress bar (in indeterminate mode) in the selected sites area conveys to the user that a long-running
		// operation is occurring.
		JPanel progressBarPanel = new JPanel();
		progressBarPanel.setLayout(new BoxLayout(progressBarPanel, BoxLayout.X_AXIS));
		progressBarPanel.add(Box.createHorizontalGlue());
		JPanel innerProgressBarPanel = new JPanel();
		innerProgressBarPanel.setLayout(new BoxLayout(innerProgressBarPanel, BoxLayout.Y_AXIS));
		innerProgressBarPanel.add(Box.createVerticalGlue());
		JLabel pleaseWait = new JLabel("Please Wait");
		pleaseWait.setForeground(IDEAColors.average(pleaseWait.getForeground(), pleaseWait.getBackground()));
		pleaseWait.setAlignmentX(Component.CENTER_ALIGNMENT);
		pleaseWait.setMaximumSize(pleaseWait.getPreferredSize());
		innerProgressBarPanel.add(pleaseWait);
		try{
	    SwingThreadSafeComponent<JProgressBar> progressBar =
				new SwingThreadSafeComponent<JProgressBar>(JProgressBar.class, 0, 1);
	    progressBar.callSafeMethod("setIndeterminate", true);
	    progressBar.callSafeMethod("setAlignmentX", Component.CENTER_ALIGNMENT);
	    innerProgressBarPanel.add(progressBar.getComponent());
	    innerProgressBarPanel.add(Box.createVerticalGlue());
	    progressBarPanel.add(innerProgressBarPanel);
	    progressBarPanel.add(Box.createHorizontalGlue());
	    if ((mainPane != null) && mainPane.isVisible() && getContentPane().isAncestorOf(mainPane)){
				mainPane.setBottomComponent(progressBarPanel);
				mainPane.invalidate();
				mainPane.validate();
	    }
	    else{
				getContentPane().removeAll();
				getContentPane().add(progressBarPanel);
	    }

	    // The GUI is disabled while the long-running task executes.
	    getGlassPane().addMouseListener(new MouseAdapter(){});
	    getGlassPane().addMouseMotionListener(new MouseMotionAdapter(){});
	    getGlassPane().setVisible(true);

	    return progressBar;
		}
		catch (InvocationTargetException ite){
	    // No exception is expected to be thrown from JProgressBar(0, 1).
	    throw new RuntimeException(ite);
		}
	}

	/**
	 * The <code>load</code> method loads results in the specified directory.
	 * The loading is performed in a background thread so the GUI stays responsive.
	 *
	 * @param dirName the directory containing the .mlc files to use
	 */
	private void load(String dirName){
		if (logger.isFinestEnabled()){
	    logger.finest("Loading results from " + dirName + "...");
		}
		// Show a wait cursor while results are loading.
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		outputDirectory = dirName;
		drawnTree = null;

		// Load results in a background thread so the GUI stays responsive.
		Thread resultsLoadingThread = new Thread(){
				public void run(){
					try{
						final SwingThreadSafeComponent<JProgressBar> loadingProgress = showProgressBar();
						dataModel = SummaryTableModel.load(outputDirectory, IDEAOutputGUI.this, loadingProgress);
						SwingUtilities.invokeLater(new Thread(){
								public void run(){
									currentName = null;
									datasetSelector = new JComboBox(new Vector<String>(dataModel.getDatasetNames()));
									if (dataModel instanceof StandardSummaryTableModel){
										modelSelector = new JComboBox(new Vector<String>(((StandardSummaryTableModel) dataModel).getModelNames()));
									}
									if ((dataModel instanceof StandardSummaryTableModel)
											&& (((StandardSummaryTableModel) dataModel).lrtExceptions != null)){
										for (IOException lrtException : ((StandardSummaryTableModel) dataModel).lrtExceptions){
											String message = lrtException.getMessage();
											String[] subparts = message.split("\\:", 2);
											String title = (subparts.length == 0) ? message : subparts[0];
											message = (subparts.length > 1) ? subparts[1] : message;
											WrapperPane.showMessageDialog(IDEAOutputGUI.this, message, title, JOptionPane.WARNING_MESSAGE);
										}
									}
								}
							});
						SwingUtilities.invokeLater(new Thread(){
								public void run(){
									createSplitPane(true, true, true, loadingProgress);
									IDEAOutputGUI.this.invalidate();
									int oldWidth = getWidth();
									int oldHeight = getHeight();
									setSize(0, 0);
									setSize(oldWidth, oldHeight);
									repaint();
								}
							});
					}
					catch (final IOException ioe){
						StackTraceElement[] stackTrace = ioe.getStackTrace();
						final boolean ideaGenerated =
							(stackTrace != null)
							&& (stackTrace.length > 0)
							&& stackTrace[0].getClassName().startsWith("edu.umaryland.igs.idea");
						try{
							SwingUtilities.invokeAndWait(new Thread(){
									public void run(){
										JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																									"An error occurred while attempting to load results from "
																									+ outputDirectory
																									+ ".\n\n"
																									+ (ideaGenerated ? ioe.getMessage() : "See the console for details."),
																									"Error Loading Results",
																									JOptionPane.ERROR_MESSAGE);
									}
								});
						}
						catch (InterruptedException ie){
							// N/A
						}
						catch (InvocationTargetException ite){  // JOptionPane.showMessageDialog is not expected to throw anything.
							Throwable cause = ite.getCause();
							if (cause instanceof RuntimeException){
								throw (RuntimeException) cause;
							}
							else{
								throw (Error) cause;
							}
						}
						if (! ideaGenerated){
							ioe.printStackTrace();
						}
						initialize();
					}
					finally{
						SwingUtilities.invokeLater(new Thread(){
								public void run(){
									setCursor(null);
									getGlassPane().setVisible(false);
								}
							});
					}
				}
	    };
		resultsLoadingThread.start();
	}

	/**
	 * This creates and returns the menu bar.  The actions to take in response to menu-option selections are
	 * specified here.
	 *
	 * @return the menu bar
	 */
	private JMenuBar menuBar(){
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem loadResults = new JMenuItem("Load Results");
		loadResults.setFont(loadResults.getFont().deriveFont(10.0f));
		loadResults.addActionListener(new ActionListener(){
				// When the user selects a dataset name list file, the input directory is updated to be the directory containing
				// that file, and the configuration panel is accordingly recreated.
				public void actionPerformed(ActionEvent e){
					JFileChooser resultsDirectoryChooser = new JFileChooser(System.getProperty("user.dir")){
							public void setSelectedFile(File file){
								super.setSelectedFile(file);
								if (file == null){
									StackTraceElement[] stack = Thread.currentThread().getStackTrace();
									for (int i = 0; i < stack.length - 1; i++){
										if (stack[i].getClassName().startsWith("edu.umaryland.igs.idea")
												&& stack[i].getMethodName().equals("setSelectedFile")
												&& stack[i + 1].getClassName().endsWith("proveSelectionAction")
												&& stack[i + 1].getMethodName().equals("actionPerformed")){
											JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																										"You must select a directory, not a file.",
																										"Select Directories Only",
																										JOptionPane.ERROR_MESSAGE);
											break;
										}
									}
								}
							}
						};
					resultsDirectoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					resultsDirectoryChooser.setDialogTitle("Select a Directory");
					int outcome = resultsDirectoryChooser.showDialog(IDEAOutputGUI.this, "Load");
					if (outcome == JFileChooser.APPROVE_OPTION){
						load(resultsDirectoryChooser.getSelectedFile().getAbsolutePath());
					}
				}
	    });
		fileMenu.add(loadResults);
		fileMenu.addSeparator();

		// The table may be saved in a tab-delimited text format.  The resulting file differs from the .summary file
		// produced as the last step of analysis.
		JMenuItem saveTable = new JMenuItem("Save Table");
		saveTable.setFont(saveTable.getFont().deriveFont(10.0f));
		saveTable.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					JFileChooser outputFileChooser = new JFileChooser(System.getProperty("user.dir"));
					if (outputFileChooser.showSaveDialog(IDEAOutputGUI.this) == JFileChooser.APPROVE_OPTION){
						try{
							PrintStream ps =
								new PrintStream(new FileOutputStream(outputFileChooser.getSelectedFile().getAbsolutePath()));
							StringBuffer outputLine = new StringBuffer();
							for (int j = 0; j < dataModel.getColumnCount(); j++){
								outputLine.append(String.format("%" + dataModel.columnWidthInCharacters(j) + "s",
																								dataModel.TEXT_COLUMN_NAMES()[j]));
								if (j < dataModel.getColumnCount() - 1){
									outputLine.append("\t");
								}
							}
							ps.println(outputLine);
							for (int i = 0; i < dataModel.getRowCount(); i++){
								outputLine = new StringBuffer();
								for (int j = 0; j < dataModel.getColumnCount(); j++){
									outputLine.append(String.format("%" + dataModel.columnWidthInCharacters(j) + "s",
																									dataModel.getValueAt(i, j)));
									if (j < dataModel.getColumnCount() - 1){
										outputLine.append("\t");
									}
								}
								ps.println(outputLine);
							}
							ps.close();
						}
						catch (FileNotFoundException fnfe){
							JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																						outputFileChooser.getSelectedFile() + " could not be created.",
																						"File Could Not Be Created",
																						JOptionPane.ERROR_MESSAGE);
						}
					}
				}
	    });
		fileMenu.add(saveTable);
		fileMenu.addSeparator();

		// This menu option allows the user to switch back to the input GUI.
		JMenuItem startNewAnalysis = new JMenuItem("Start New Analysis");
		startNewAnalysis.setFont(startNewAnalysis.getFont().deriveFont(10.0f));
		startNewAnalysis.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					dispose();
					new IDEAInputGUI();
				}
	    });
		fileMenu.add(startNewAnalysis);
		menuBar.add(fileMenu);

		// The "IDEA Documentation" item in the Help menu launches the user's default browser to bring up the online help.
		JMenu helpMenu = new JMenu("Help");
		JMenuItem help = new JMenuItem("IDEA Documentation");
		help.addActionListener(new ActionListener(){
				// The user should be referred to the internal website for documentation.
				public void actionPerformed(ActionEvent e){
					BrowserLauncher.launchBrowser("http://ideanalyses.sourceforge.net/user_guide.php", IDEAOutputGUI.this);
				}
	    });
		helpMenu.add(help);
		menuBar.add(helpMenu);
		return menuBar;
	}

	/**
	 * The <code>createSplitPane</code> method creates the data table, tree area and filter area
	 * and arranges them in a hierarchical split pane.
	 *
	 * @param createNewTable whether to create a new table (false if the table has already been created)
	 * @param redrawTree whether to redraw the tree (false if the selected tree is already drawn)
	 * @param redrawSelectedSites whether to redraw the selected sites display (false if the row selection has not changed)
	 * @param tableProgress a progress bar for table creation; may be null; ignored if createNewTable is false
	 */    
	private void createSplitPane(boolean createNewTable, boolean redrawTree,
															 boolean redrawSelectedSites, SwingThreadSafeComponent<JProgressBar> tableProgress){
		if (createNewTable){
	    dataTable = dataTable(tableProgress);
	    tablePane = new JScrollPane(dataTable);
	    Border etchedBorder = new EtchedBorder(EtchedBorder.RAISED, IDEAColors.LAVENDER, IDEAColors.HYACINTH);
	    tablePane.setBorder(etchedBorder);
		}
		if (dataModel instanceof StandardSummaryTableModel){
	    int tablePaneWidth = tablePane.getPreferredSize().width;
	    if (redrawTree){
				treeArea = treeArea(currentName);
	    }
	    treeArea.setPreferredSize(new Dimension(IDEAConstants.SCREEN_SIZE.width - tablePaneWidth, treeArea.getPreferredSize().height));
	    final JSplitPane topPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePane, treeArea);
	    topPane.setResizeWeight(1.0);
	    if (redrawSelectedSites){
				int selectedRow = dataTable.getSelectedRow();
				selectedSitesArea
					= (selectedRow == -1)
					? new JPanel()
					: selectedSitesArea((String) dataTable.getValueAt(selectedRow, dataModel.COLUMN_INDICES().get("Dataset")),
															(String) dataTable.getValueAt(selectedRow, dataModel.COLUMN_INDICES().get("Model")));
	    }
	    selectedSitesArea.setMinimumSize(selectedSitesArea.getPreferredSize());
	    mainPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPane, selectedSitesArea);
	    getContentPane().removeAll();
	    getContentPane().add(mainPane);
		}
		else{  // In pairwise mode, there is no tree display or selected-sites display.
	    getContentPane().removeAll();
	    getContentPane().add(tablePane);
		}
	}

	/**
	 * The <code>dataTable</code> method returns the data table.
	 * The data in the data table are summary results from a completed IDEA run.
	 *
	 * @param progress a progress bar indicating the progress of table creation; may be null
	 *
	 * @return the data table
	 */    
	private JTable dataTable(SwingThreadSafeComponent<JProgressBar> progress){
		JTable table = new JTable(dataModel){
				public String getToolTipText(MouseEvent e){
					Point mouseLocationOnTable = e.getPoint();
					int viewColumnIndex = columnAtPoint(mouseLocationOnTable);
					int modelColumnIndex = convertColumnIndexToModel(viewColumnIndex);
					TableCellRenderer delegate = getColumnModel().getColumn(modelColumnIndex).getCellRenderer();
					return (delegate instanceof JComponent) ? ((JComponent) delegate).getToolTipText(e) : super.getToolTipText(e);
				}
	    };
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setGridColor(IDEAColors.BORDER_GRAY);
		if (dataModel != null){
	    table.sizeColumnsToFit(-1);
	    table.getTableHeader().setReorderingAllowed(false);
	    SummaryTableHeaderRenderer headerRenderer = new SummaryTableHeaderRenderer(this);
	    SummaryTableCellRenderer bodyCellRenderer = new SummaryTableCellRenderer(this);
	    int i = 0;
	    for (Enumeration<TableColumn> columns = table.getColumnModel().getColumns(); columns.hasMoreElements(); /** */){
				int columnWidth = 0;
				TableColumn nextColumn = columns.nextElement();
				nextColumn.setHeaderRenderer(headerRenderer);
				nextColumn.setCellRenderer(bodyCellRenderer);
				nextColumn.sizeWidthToFit();
				Component comp = headerRenderer.getTableCellRendererComponent(table, nextColumn.getHeaderValue(),
																																			false, false, 0, 0);
				columnWidth = comp.getPreferredSize().width;
				for (int j = 0; j < dataModel.getRowCount(); j++){
					comp = table.getCellRenderer(j, i).getTableCellRendererComponent(table, dataModel.getValueAt(j, i),
																																					 false, false, j, i);
					progress.callSafeMethod("setValue", ((Integer) progress.callSafeMethod("getValue")) + 1);
					progress.callSafeMethod("paintImmediately", 0, 0,
																	progress.callSafeMethod("getWidth"), progress.callSafeMethod("getHeight"));
					columnWidth = Math.max(comp.getPreferredSize().width, columnWidth);
				}
				nextColumn.setPreferredWidth(columnWidth);
				nextColumn.setMinWidth(columnWidth);
				if (table.getColumnName(i).equals("n")){
					nextColumn.setMaxWidth(columnWidth * 2);
				}
				i++;
	    }
		}
		return table;
	}

	/**
	 * The <code>displayDetails</code> method brings up a scrollable window with the merged output of the PAML runs.
	 * It is automatically scrolled to the model corresponding to the selected row.
	 *
	 * @param selectedRow the row in which the user clicked the hyperlink in the Model column
	 */
	public void displayDetails(final int selectedRow){
		JDialog details = new JDialog(this, "Details", false);
		details.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		String datasetName = (String) dataTable.getValueAt(selectedRow, dataModel.COLUMN_INDICES().get("Dataset"));
		String modelNumber =
	    modelNumber((String) dataTable.getValueAt(selectedRow, dataModel.COLUMN_INDICES().get("Model")));
		FileParser fp = mlcFileParser(datasetName);
		if (fp == null){
	    return;
		}
		JTextArea pamlOutput = new JTextArea();
		int lines = 0;
		try{
	    while (true){
				pamlOutput.append(fp.nextLine());
				pamlOutput.append("\n");
				lines++;
	    }
		}
		catch (EOFException eofe){
	    // This exception is expected.
		}
		catch (IOException fnfe){
	    JOptionPane.showMessageDialog(this,
																		"There was some problem reading " + fp.filename + ".",
																		"I/O Error",
																		JOptionPane.ERROR_MESSAGE);
	    return;
		}
		finally{
	    fp.closeFile();
		}
		pamlOutput.setFont(new Font("Monospaced", Font.BOLD, 10));
		JScrollPane detailsScrollPane = new JScrollPane(pamlOutput);
		detailsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		detailsScrollPane.setPreferredSize(new Dimension(IDEAConstants.SCREEN_SIZE.width * 45 / 100,
																										 IDEAConstants.SCREEN_SIZE.height / 4));
		details.add(detailsScrollPane);
		details.pack();
		details.setLocationRelativeTo(null);  // Center the dialog box on the screen.
		details.setVisible(true);
		int startOfDataForModelOfInterest =
	    pamlOutput.getText().indexOf(((dataModel instanceof PairwiseSummaryTableModel) // extra cautious; should not occur
																		|| (((StandardSummaryTableModel) dataModel).getModelNames().size() == 1))
																	 ? "TREE #"
																	 : ("Model " + modelNumber + ":"));
		try{
	    Rectangle view = pamlOutput.modelToView(startOfDataForModelOfInterest);
	    view.setSize(pamlOutput.getVisibleRect().getSize());
	    pamlOutput.scrollRectToVisible(view);
	    pamlOutput.setCaretPosition(startOfDataForModelOfInterest);
		}
		catch (BadLocationException ble){
	    JOptionPane.showMessageDialog(this,
																		"No results were found for that model.\nThis may mean PAML's output is malformatted.\nPAML's output will be displayed anyway.\n",
																		"No Results for Model",
																		JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * The <code>displaySelectedSites</code> method calls the <code>selectedSitesArea</code> method to display selected
	 * sites for the selected row.
	 *
	 * @param selectedRow the selected row in the table
	 * @param drawTree true if the tree for the selected row should be drawn after the selected sites display is drawn
	 */
	public void displaySelectedSites(final int selectedRow, final boolean drawTree){
		final String dataset = (String) dataTable.getValueAt(selectedRow, dataModel.COLUMN_INDICES().get("Dataset"));
		final String model = (String) dataTable.getValueAt(selectedRow, dataModel.COLUMN_INDICES().get("Model"));

		if (modelNumber(model).equals("0")){
	    selectedSitesArea = new JPanel();
	    mainPane.setBottomComponent(selectedSitesArea);
	    mainPane.invalidate();
	    mainPane.validate();
	    if (drawTree){
				displayTree(dataset, model);
	    }
	    return;
		}

		showProgressBar();  // The return value isn't needed here.

		// The selected sites display is updated in a background thread so the GUI stays responsive.
		Thread selectedSitesDrawingThread = new Thread(){
				public void run(){
					final JPanel ssDisplay = selectedSitesArea(dataset, model);
					try{
						SwingUtilities.invokeAndWait(new Thread(){
								public void run(){
									selectedSitesArea = ssDisplay;
									mainPane.setBottomComponent(selectedSitesArea);
									mainPane.invalidate();
									mainPane.validate();
								}
							});
					}
					catch (InterruptedException ie){
						// N/A
					}
					catch (InvocationTargetException ite){
						ExceptionHandler.handleException(ite);
					}
					SwingUtilities.invokeLater(new Thread(){
							public void run(){
								if (drawTree){
									displayTree(dataset, model);
								}
								getGlassPane().setVisible(false);
								setCursor(null);
							}
						});
				}
	    };
		selectedSitesDrawingThread.start();
	}

	/**
	 * The <code>mlcFileParser</code> method returns a parser (class edu.umaryland.igs.aegan.utils.FileParser) on the .mlc
	 * (merged PAML output) file for the specified dataset.
	 *
	 * @param datasetName the dataset for which to read data from a file
	 *
	 * @return a parser on the .mlc file (merged output of one or more PAML runs)
	 */
	FileParser mlcFileParser(String datasetName){
		try{
	    return new FileParser(outputDirectory + "/" + datasetName + ".mlc");
		}
		catch (FileNotFoundException fnfe){
	    try{
				return new FileParser(outputDirectory + "/" + datasetName + ".merged");
	    }
	    catch (FileNotFoundException fnfe2){
				JOptionPane.showMessageDialog(this,
																			"No merged output file was found.\nPerhaps the run of PAML did not complete successfully.",
																			"File Not Found",
																			JOptionPane.ERROR_MESSAGE);
				return null;
	    }
		}
	}
	
	/**
	 * The <code>modelNumber</code> method returns a string containing just the numeric part of the given model name.
	 *
	 * @param modelName the model name to parse
	 *
	 * @return the # part of the model name (as a string), or the whole model name if it doesn't match the expected pattern
	 */
	public static String modelNumber(String modelName){
		try{
	    return modelName.substring(0, modelName.indexOf("-"));
		}
		catch (IndexOutOfBoundsException ioobe){
	    return modelName;
		}
	}

	/**
	 * The <code>treeIsVisibleAndCurrent</code> method returns true if a tree for the requested dataset and model is
	 * currently part of the display and was successfully drawn based on a tree file that is newer than the merged output
	 * (.mlc or .merged) file.
	 *
	 * @param datasetName the name of the requested dataset
	 * @param modelName the name of the requested model
	 *
	 * @return whether a current tree for the requested dataset and model is already drawn
	 */
	private boolean treeIsVisibleAndCurrent(String datasetName, String modelName){
		if ((drawnTree != null) && drawnTree.equals(datasetName + ", " + modelName)){
	    FileParser fp = mlcFileParser(datasetName);
	    if (fp == null){
				return false;
	    }
	    File existingTreeFile = new File(outputDirectory + "/" + datasetName + ".m" + modelNumber(modelName) + ".tree");
	    if (existingTreeFile.exists() && existingTreeFile.canRead() && FileClerk.datedInOrder(fp, existingTreeFile)){
				return true;
	    }
		}
		return false;
	}

	/**
	 * The <code>displayTree</code> method parses the tree for the specified dataset and model out of the merged output
	 * file, writes the tree to a separate file, runs the Ruby program newickDraw to create a visual representation of the
	 * tree in PDF format and passes along the name of the newly-created PDF file so <code>treeArea</code> can display it.
	 * The data in the data table are summary results from a completed IDEA run.
	 *
	 * @param datasetName the name of the dataset whose tree should be displayed
	 * @param modelName the name of the model whose branch-length estimates should be used
	 */    
	void displayTree(String datasetName, String modelName){
		if (treeIsVisibleAndCurrent(datasetName, modelName)){
	    return;
		}			
		FileParser fp = mlcFileParser(datasetName);
		if (fp == null){
	    drawnTree = null;
	    return;
		}

		// A wait cursor is used while the tree is being drawn.
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		String modelNumber = modelNumber(modelName);
		Pattern modelHeaderPattern = Pattern.compile("\\s*Model\\s+(\\w+)\\:");
		try{
	    while (true){
				String nextLine = fp.nextLine();
				boolean onlyOneModel = ((StandardSummaryTableModel) dataModel).getModelNames().size() == 1;
				Matcher modelHeaderPatternMatcher = null;
				if (! onlyOneModel){
					modelHeaderPatternMatcher = modelHeaderPattern.matcher(nextLine);
				}
				if (onlyOneModel ||
						(modelHeaderPatternMatcher.lookingAt() && modelHeaderPatternMatcher.group(1).equals(modelNumber))){
					while (true){
						nextLine = fp.nextLine();
						if (nextLine.contains("tree length =")){
							for (int i = 0; i < 3; i++){
								fp.nextLine();
							}
							String newickTree = fp.nextLine().replace(": ", ":");
							String newickTreeFilename
								= outputDirectory + "/" + datasetName + ".m" + modelNumber + ".tree";
							try{
								PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(newickTreeFilename)));
								ps.println(newickTree);
								ps.close();
							}
							catch (FileNotFoundException fnfe){
								JOptionPane.showMessageDialog(this,
																							newickTreeFilename + " could not be created.\nIt may already exist.",
																							"File Could Not Be Created",
																							JOptionPane.ERROR_MESSAGE);
								drawnTree = null;
								return;
							}

							// The Ruby program newickDraw is called to draw the tree.
							String newickDraw = IDEA_DIR + "newickDraw -l " + newickTreeFilename;
							try{
								int exitValue = Runtime.getRuntime().exec(newickDraw).waitFor();
								if (exitValue != 0){
									JOptionPane.showMessageDialog(this,
																								"Some error occurred during tree visualization.\nPlease report this problem.\n" + newickDraw,
																								"Tree Visualization Error",
																								JOptionPane.ERROR_MESSAGE);
									drawnTree = null;
									return;
								}
							}
							catch (InterruptedException ioe){
								// N/A
							}
							currentName = newickTreeFilename + ".pdf";
							setSelectedTree(datasetName, modelName);
							createSplitPane(false, true, false, null);// This implicitly calls the treeArea method to display the tree.
							int oldWidth = getWidth();
							int oldHeight = getHeight();
							setSize(0, 0);
							setSize(oldWidth, oldHeight);
							repaint();
							return;
						}
					}
				}
	    }
		}
		catch (EOFException eofe){
	    JOptionPane.showMessageDialog(this,
																		"The necessary tree information was not found in the merged output file " + fp.filename + ".\nThis may mean that PAML did not complete successfully.",
																		"Malformatted Output File",
																		JOptionPane.ERROR_MESSAGE);
	    drawnTree = null;
		}
		catch (IOException ioe){
	    JOptionPane.showMessageDialog(this,
																		"An I/O error occurred during tree visualization.\nSee the console for details.",
																		"I/O Error",
																		JOptionPane.ERROR_MESSAGE);
	    ioe.printStackTrace();
	    drawnTree = null;
		}
		finally{
	    setCursor(null);
	    fp.closeFile();
		}
	}

	/**
	 * The <code>treeArea</code> method returns a panel containing the IDEA logo, dataset and model selectors and
	 * a Display button above an image of the currently selected tree (if any).  When the user clicks the Display button, 
	 * a new tree is generated by calling the <code>displayTree</code> method, and then this panel is updated.
	 *
	 * @param filename name of the tree file to be displayed
	 *
	 * @return a panel containing the IDEA logo, dataset and model selectors, Display button and tree image (if any)
	 */    
	private JPanel treeArea(String filename){
		String treeFilename = filename;

		// Create a new panel with a vertical layout.
		JPanel rv = new JPanel();
		rv.setBackground(Color.WHITE);
		rv.setLayout(new BoxLayout(rv, BoxLayout.Y_AXIS));

		// Display a scaled version of the logo on the panel and a divider below it.
		JLabel logoLabel = (LOGO == null) ? new JLabel("IDEA") : new JLabel(LOGO);
		JPanel logoPanel = new JPanel();
		logoPanel.setLayout(new BorderLayout());
		logoPanel.add(logoLabel, BorderLayout.CENTER);
		logoPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, IDEAColors.DARK_GREEN));
		logoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		logoPanel.setBackground(Color.WHITE);
		rv.add(logoPanel);
		if ((dataModel == null) || (dataModel.getColumnCount() == 0)){
	    rv.add(Box.createVerticalStrut(IDEAConstants.SCREEN_SIZE.height * 4 / 10));
	    drawnTree = null;
	    return rv;
		}

		// Display a label, the dataset and model selectors and the display button.
		JLabel treeAreaTitle = new JLabel("Phylogenetic Tree");
		treeAreaTitle.setFont(treeAreaTitle.getFont().deriveFont(treeAreaTitle.getFont().getStyle(), 14));
		rv.add(treeAreaTitle);
		JPanel selectorAndButtonPanel = new JPanel(new BorderLayout());
		JPanel datasetSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		JLabel datasetLabel = new JLabel("Dataset:");
		Font originalFont = datasetLabel.getFont();
		Font selectionLabelFont = new Font("Monospaced", Font.PLAIN, originalFont.getSize());
		datasetLabel.setFont(selectionLabelFont);
		datasetSelectionPanel.add(datasetLabel);
		datasetSelectionPanel.add(datasetSelector);
		datasetSelectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		datasetSelectionPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
		datasetSelectionPanel.setMaximumSize(datasetSelectionPanel.getPreferredSize());
		datasetSelectionPanel.setBackground(Color.WHITE);
		selectorAndButtonPanel.add(datasetSelectionPanel, BorderLayout.WEST);
		JButton displayButton = new JButton("Display");
		displayButton.setMargin(new Insets(0, 0, 0, 0));
		displayButton.setMaximumSize(displayButton.getPreferredSize());
		displayButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					displayTree((String) datasetSelector.getSelectedItem(), (String) modelSelector.getSelectedItem());
				}
	    });
		JPanel innerWrapper = new JPanel();
		innerWrapper.setLayout(new BoxLayout(innerWrapper, BoxLayout.Y_AXIS));
		int halfHeightDifference
	    = (datasetSelectionPanel.getPreferredSize().height - displayButton.getPreferredSize().height) / 2;
		innerWrapper.add(Box.createVerticalStrut(halfHeightDifference));
		innerWrapper.add(displayButton);
		innerWrapper.add(Box.createVerticalStrut(halfHeightDifference));
		innerWrapper.setBackground(Color.WHITE);
		FlowLayout wrapperLayout = new FlowLayout(FlowLayout.LEADING);
		wrapperLayout.setVgap(0);
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
		wrapper.add(innerWrapper);
		wrapper.setAlignmentY(Component.CENTER_ALIGNMENT);
		wrapper.setBackground(Color.WHITE);
		selectorAndButtonPanel.add(wrapper, BorderLayout.EAST);
		selectorAndButtonPanel.setMaximumSize(new Dimension(selectorAndButtonPanel.getMaximumSize().width,
																												selectorAndButtonPanel.getPreferredSize().height));
		selectorAndButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectorAndButtonPanel.setBackground(Color.WHITE);
		rv.add(selectorAndButtonPanel);
		JPanel modelSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		JLabel modelLabel = new JLabel("  Model:");
		modelLabel.setFont(selectionLabelFont);
		modelSelectionPanel.add(modelLabel);
		modelSelectionPanel.add(modelSelector);
		modelSelectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		modelSelectionPanel.setMaximumSize(modelSelectionPanel.getPreferredSize());
		modelSelectionPanel.setBackground(Color.WHITE);
		rv.add(modelSelectionPanel);
		try{
	    if (treeFilename == null){
				throw new IllegalArgumentException("");
	    }

	    // Use the PDFBox library to convert the PDF generated by newickDraw to an image.
	    PDDocument document = PDDocument.load(treeFilename);
	    PDDocumentCatalog catalog = document.getDocumentCatalog();
	    List pages = catalog.getAllPages();
	    if (pages.size() != 1){
				throw new IllegalArgumentException("Too many pages in PDF file (" + pages.size() + " instead of 1)!");
	    }
	    BufferedImage treeImage = ((PDPage) pages.get(0)).convertToImage();
	    document.close();

	    // Crop of white parts on the bottom and right of the image.
	    Dimension sizeOfInterestingArea = filledSpace(treeImage);
	    final BufferedImage croppedImage = treeImage.getSubimage(0,
																															 0,
																															 sizeOfInterestingArea.width,
																															 sizeOfInterestingArea.height);

	    // Scale the cropped image to 30% of the screen height.
	    ImageIcon treeIcon
				= new ImageIcon(croppedImage.getScaledInstance(IDEAConstants.SCREEN_SIZE.height * 3 / 10,
																											 -1,
																											 Image.SCALE_SMOOTH),
												"Phylogenetic Tree");
	    JLabel treePicture = new JLabel(treeIcon);
	    treePicture.setAlignmentX(Component.LEFT_ALIGNMENT);
	    treePicture.setBorder(BorderFactory.createLineBorder(IDEAColors.PURPLE));
	    rv.add(treePicture);

	    // Add a "click to enlarge" label below the image.  Clicking this label brings up a larger version of the image
	    // in a child window.
	    JPanel enlargementPanel = new JPanel(new BorderLayout());
	    enlargementPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
	    JLabel enlargementLabel = new JLabel("click to enlarge");
	    enlargementLabel.setFont(enlargementLabel.getFont().deriveFont(Font.PLAIN, 8.0f));
	    enlargementLabel.setMaximumSize(enlargementLabel.getPreferredSize());
	    enlargementLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	    enlargementLabel.addMouseListener(new MouseAdapter(){
					public void mouseClicked(MouseEvent e){
						final JDialog largerVersion =
							new JDialog(IDEAOutputGUI.this,
													"Phylogenetic Tree:  " + datasetSelector.getSelectedItem()
													+ ", Model " +  modelSelector.getSelectedItem());
						largerVersion.getContentPane().setLayout(new BoxLayout(largerVersion.getContentPane(), BoxLayout.Y_AXIS));
						ImageIcon largerTreeIcon =
							new ImageIcon(croppedImage.getScaledInstance(IDEAConstants.SCREEN_SIZE.height * 3 / 5,
																													 -1,
																													 Image.SCALE_SMOOTH),
														"Phylogenetic Tree (Larger View)");
						JLabel largerPicture = new JLabel(largerTreeIcon);
						largerPicture.setAlignmentX(Component.CENTER_ALIGNMENT);
						largerVersion.add(largerPicture);
						JButton dismissButton = new JButton("Dismiss");
						dismissButton.addActionListener(new ActionListener(){
								public void actionPerformed(ActionEvent e){
									largerVersion.dispose();
								}
							});
						dismissButton.setAlignmentX(Component.CENTER_ALIGNMENT);
						largerVersion.add(dismissButton);
						largerVersion.pack();
						largerVersion.setLocationRelativeTo(null);  // Center the dialog box on the screen.
						largerVersion.setVisible(true);
					}
				});
	    JPanel labelWrapper = new JPanel();
	    labelWrapper.setLayout(new BoxLayout(labelWrapper, BoxLayout.Y_AXIS));
	    labelWrapper.setBackground(Color.WHITE);
	    labelWrapper.add(enlargementLabel);
	    enlargementPanel.add(labelWrapper, BorderLayout.CENTER);
	    enlargementPanel.setBackground(Color.WHITE);
	    rv.add(enlargementPanel);

	    // If necessary, add a vertical strut to ensure that the tree panel's size is correct.
	    int gapOfBlankness =
				(IDEAConstants.SCREEN_SIZE.height * 4 / 10) - treePicture.getPreferredSize().height + 1; // factor in border
	    if (gapOfBlankness > 0){
				rv.add(Box.createVerticalStrut(gapOfBlankness));
	    }
	    drawnTree = datasetSelector.getSelectedItem() + ", " + modelSelector.getSelectedItem();
		}
		catch (IllegalArgumentException iae){
	    JLabel errorMessage = new JLabel(iae.getMessage());
	    rv.add(errorMessage);
	    rv.add(Box.createVerticalStrut(IDEAConstants.SCREEN_SIZE.height * 4 / 10 - errorMessage.getPreferredSize().height + 1));
	    drawnTree = null;
		}
		catch (IOException ioe){
	    JLabel errorMessage = new JLabel(ioe.getMessage());
	    rv.add(errorMessage);
	    rv.add(Box.createVerticalStrut(IDEAConstants.SCREEN_SIZE.height * 4 / 10 - errorMessage.getPreferredSize().height + 1));
	    drawnTree = null;
		}
		rv.setAlignmentY(Component.TOP_ALIGNMENT);
		rv.setBorder(BorderFactory.createLineBorder(IDEAColors.DARK_GREEN));
		rv.setMaximumSize(rv.getPreferredSize());
		return rv;
	}

	/**
	 * The static method <code>svgFilenames</code> returns a sorted array of all currently existing SVG filenames for the
	 * supplied dataset and model number in the supplied directory.
	 *
	 * @param dataset the dataset whose SVG files are sought
	 * @param modelNumber the model number for which SVG files are sought
	 * @param directory the directory in which SVG files are sought
	 * 
	 * @return a sorted array of the names of all applicable SVG files in the directory
	 */
	private static String[] svgFilenames(final String dataset, final String modelNumber, File directory){
		String[] rv = directory.list(new FilenameFilter(){
				public boolean accept(File dir, String name){
					return name.startsWith(dataset + "-w") && name.endsWith("-m" + modelNumber + "-BEB.svg");
				}
	    });
		if ((rv != null) && (rv.length == 0)){
	    rv = directory.list(new FilenameFilter(){
					public boolean accept(File dir, String name){
						return name.startsWith(dataset + "-w") && name.endsWith("-m" + modelNumber + "-NEB.svg");
					}
				});
		}
		Arrays.sort(rv);
		return rv;
	}

	/**
	 * The static method <code>filledSpace</code> returns the dimensions of the `interesting area' of the supplied image,
	 * which is defined as the smallest rectangular subset of the image including the upper left corner of the image
	 * such that the area of the image outside the subset is entirely white.
	 *
	 * @param image the image to crop
	 * 
	 * @return the dimensions of the image's interesting area (a cropping of the image which includes its top left corner)
	 */
	private static Dimension filledSpace(BufferedImage image){
		int width = image.getWidth();
		int height = image.getHeight();
		int maxHeight = 0;
		int maxWidth = 0;
		for (int i = width - 1; i >= 0; i--){
	    for (int j = height - 1; j >= 0; j--){
				if ((j > maxHeight) && (image.getRGB(i, j) != Color.WHITE.getRGB())){
					maxHeight = j;
				}
				if ((i > maxWidth) && (image.getRGB(i, j) != Color.WHITE.getRGB())){
					maxWidth = i;
				}
	    }
		}
		return new Dimension(maxWidth, maxHeight);
	}

	/**
	 * The <code>selectedSitesArea</code> method returns a panel in which a display of selected sites may be shown.
	 * Two programs are executed to create the display.  Because this process is time-consuming, this method should
	 * be called from a background thread, not the event dispatch thread.  Although this method performs operations
	 * on Swing components, most of the operations are considered safe to perform in a background thread under the
	 * old Sun policy because they do not involve realized components (components in a hierarchy under a visible or
	 * packed window).  The only exceptions are the error dialogs, which are created on the event dispatch thread.
	 *
	 * @param datasetIdentifier the dataset for which to display selected sites
	 * @param model the model on which to base the selected sites display
	 *
	 * @return a panel in which selected sites may be displayed
	 */    
	private JPanel selectedSitesArea(final String datasetIdentifier, String model){
		final String modelNumber = modelNumber(model);
		if (logger.isFinestEnabled()){
	    logger.finest("Displaying selected sites for " + model + " (model " + modelNumber + ")...");
		}
		if (modelNumber.equals("0")){
	    return new JPanel();
		}
		final JPanel selectedSitesPanel = new JPanel();
		selectedSitesPanel.setLayout(new BoxLayout(selectedSitesPanel, BoxLayout.Y_AXIS));
		final File directory = new File(outputDirectory);
		if (! directory.canWrite()){
	    selectedSitesPanel.add(new JLabel("To view selected sites, make the directory "
																				+ outputDirectory + " writable by you."));
	    return selectedSitesPanel;
		}
		JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		JLabel selectedSitesTitle = new JLabel("Selected Sites:  Bayes Empirical Bayes Analysis for " + datasetIdentifier
																					 + ", Model " + model); 
		titlePanel.add(selectedSitesTitle);
		JButton saveButton = new JButton("Save");
		saveButton.setEnabled(false);
		titlePanel.add(saveButton);
		titlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean currentSVGFileExists = false;
		boolean currentImageExists = false;
		String[] existingSVGFilenames = svgFilenames(datasetIdentifier, modelNumber, directory);
		String svgFilename = null;
		String keySVGFilename = null;
		String jpegFilename = null;
		String keyJPEGFilename = null;
		FileParser mlcParser = mlcFileParser(datasetIdentifier);
		if ((existingSVGFilenames != null) && (existingSVGFilenames.length > 0)){
	    long formatSwitchDate = new SimpleDateFormat("dd-MMM-yyyy h:mma").parse("24-FEB-2009 3:00PM",
																																							new ParsePosition(0)).getTime();
	    svgFilename = outputDirectory + "/" + existingSVGFilenames[0];
	    File existingSVGFile = new File(svgFilename);
			keySVGFilename = outputDirectory + "/" + existingSVGFilenames[0].substring(0, existingSVGFilenames[0].length() - 4) + ".key.svg";
			File existingKeySVGFile = new File(keySVGFilename);
	    if (existingSVGFile.isFile()
					&& existingSVGFile.canRead()
					&& (existingSVGFile.lastModified() > formatSwitchDate)
					&& FileClerk.datedInOrder(mlcParser, existingSVGFile)
					&& existingKeySVGFile.isFile()
					&& existingKeySVGFile.canRead()
					&& (existingKeySVGFile.lastModified() > formatSwitchDate)
					&& FileClerk.datedInOrder(mlcParser, existingKeySVGFile)){
				currentSVGFileExists = true;
	    }
	    jpegFilename =
				outputDirectory + "/" + existingSVGFilenames[0].substring(0, existingSVGFilenames[0].length() - 4) + ".jpg";
	    File existingJPEGFile = new File(jpegFilename);
			keyJPEGFilename = outputDirectory + "/" + existingSVGFilenames[0].substring(0, existingSVGFilenames[0].length() - 4) + ".key.jpg";
			File existingKeyJPEGFile = new File(keyJPEGFilename);
	    // A change was made to the JPEG formats.  Files made before the format change should be recreated.
	    if (existingJPEGFile.isFile()
					&& existingJPEGFile.canRead()
					&& (existingJPEGFile.lastModified() > formatSwitchDate)
					&& FileClerk.datedInOrder(mlcParser, existingJPEGFile)
					&& existingKeyJPEGFile.isFile()
					&& existingKeyJPEGFile.canRead()
					&& (existingKeyJPEGFile.lastModified() > formatSwitchDate)
					&& FileClerk.datedInOrder(mlcParser, existingJPEGFile)){
				currentImageExists = true;
	    }
		}
		mlcParser.closeFile();
		if (! currentImageExists){

	    if (! currentSVGFileExists){
				// First create the SVG file.
				final String svgCreationCommand =
					IDEA_DIR + "ss.make-idea-svg-graphs.pl --file_prefix=" + datasetIdentifier
					+ " --data_dir=" + outputDirectory + " --key_alignment=start 1>/dev/null 2>/dev/null";
				if (logger.isFinestEnabled()){
					logger.finest("Executing [" + svgCreationCommand + "] in [" + directory + "]...");
				}
				String[] svgCreationCmdArray =
					{IDEA_DIR + "ss.make-idea-svg-graphs.pl",
					 "--file_prefix=" + datasetIdentifier,
					 "--data_dir=" + outputDirectory,
					 "--key_alignment=start"};
				try{
					Process process = Runtime.getRuntime().exec(svgCreationCmdArray, null, directory);
				
					// Gobble up the STDOUT and STDERR output.
					final StreamSaver errorSaver = new StreamSaver(process.getErrorStream(), "ERROR");            
					final StreamSaver outputSaver = new StreamSaver(process.getInputStream(), "OUTPUT");
					errorSaver.start();
					outputSaver.start();
				
					// Run the program and see if one of the expected SVG files results.  Fail otherwise.
					int exitValue = process.waitFor();
					String[] svgFilenamesForDatasetAndModel = svgFilenames(datasetIdentifier, modelNumber, directory);
					if (svgFilenamesForDatasetAndModel == null){
						SwingUtilities.invokeLater(new Thread(){
								public void run(){
									JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																								"An I/O error occurred during visualization.\n",
																								"I/O Error",
																								JOptionPane.ERROR_MESSAGE);
								}
							});
						return selectedSitesPanel;
					}
					if (svgFilenamesForDatasetAndModel.length == 0){
						SwingUtilities.invokeLater(new Thread(){
								public void run(){
									Object[] choices = {"View Command-Line Output", "Cancel"};
									int response = JOptionPane.showOptionDialog(IDEAOutputGUI.this,
																															"An error occurred during visualization:\nNo SVG files for "
																															+ datasetIdentifier + ", model " + modelNumber + " were found in "
																															+ directory
																															+ ".\nPlease look for an error in the process's text output.\n\nSVG creation command:\n\n"
																															+ svgCreationCommand,
																															"Visualization Error:  Selected Sites",
																															JOptionPane.YES_NO_OPTION,
																															JOptionPane.ERROR_MESSAGE,
																															null,
																															choices,
																															choices[0]);
									if (response == JOptionPane.YES_OPTION){
										JDialog commandLineOutput = new JDialog(IDEAOutputGUI.this, "Command-Line Output:  Error and Standard Output", false);
										commandLineOutput.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
										JTextArea outputTextArea = new JTextArea();
										outputTextArea.setFont(new Font("Monospaced", Font.BOLD, 10));
										outputTextArea.append("Error Output:\n\n");
										outputTextArea.append(errorSaver.getStreamContents());
										outputTextArea.append("\n");
										outputTextArea.append("Standard Output:\n\n");
										outputTextArea.append(outputSaver.getStreamContents());
										outputTextArea.setEditable(false);
										JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
										outputScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
										outputScrollPane.setPreferredSize(new Dimension(IDEAConstants.SCREEN_SIZE.width * 45 / 100,
																																		IDEAConstants.SCREEN_SIZE.height / 4));
										commandLineOutput.add(outputScrollPane);
										commandLineOutput.pack();
										commandLineOutput.setLocationRelativeTo(null);  // Center the dialog box on the screen.
										commandLineOutput.setVisible(true);
									}
								}
							});
						selectedSitesPanel.add(new JLabel("error"));
						return selectedSitesPanel;
					}
				
					// It doesn't matter which omega value we use. 
					svgFilename = outputDirectory + "/" + svgFilenamesForDatasetAndModel[0];
					final String finalSVGFilename = svgFilename;
					keySVGFilename = svgFilename.substring(0, svgFilename.length() - 4) + ".key.svg";
					final String finalKeySVGFilename = keySVGFilename;
					if ((exitValue != 0) || (! new File(svgFilename).canRead()) || (! new File(keySVGFilename).canRead())){
						SwingUtilities.invokeLater(new Thread(){
								public void run(){
									JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																								"An error occurred during visualization:\nThe command ["
																								+ svgCreationCommand + "] was unsuccessful, and/or the file "
																								+ finalSVGFilename + " or " + finalKeySVGFilename
																								+ " does not exist or cannot be read.\nPlease report this problem.\n",
																								"Visualization Error:  Selected Sites",
																								JOptionPane.ERROR_MESSAGE);
								}
							});
						selectedSitesPanel.add(new JLabel("error"));
						return selectedSitesPanel;
					}
				}
				catch (InterruptedException ioe){
					// N/A
				}
				catch (IOException ioe){
					SwingUtilities.invokeLater(new Thread(){
							public void run(){
								JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																							"An I/O error occurred during visualization.\n",
																							"I/O Error",
																							JOptionPane.ERROR_MESSAGE);
							}
						});
					return selectedSitesPanel;
				}
	    }

	    // Then convert the SVG file into a JPEG file.
	    final String fileConversionCommand = IDEA_DIR + "ss.convert-svg-files.pl --svg_files="
				+ svgFilename + " --format=jpg --quality=0.99 --output_height=130 --force";
			long keyWidth = Math.round(IDEAConstants.SCREEN_SIZE.getWidth() / 2);
	    final String fileConversionCommand2 = IDEA_DIR + "ss.convert-svg-files.pl --svg_files="
				+ keySVGFilename + " --format=jpg --quality=0.99 --output_height=25 --force";
			jpegFilename = svgFilename.substring(0, svgFilename.length() - 4) + ".jpg";
	    final File jpegFile = new File(jpegFilename);
	    keyJPEGFilename = svgFilename.substring(0, svgFilename.length() - 4) + ".key.jpg";
			final File keyJPEGFile = new File(keyJPEGFilename);
	    try{
				if (logger.isFinestEnabled()){
					logger.finest("Running [" + fileConversionCommand + "]...");
				}
				ProcessBuilder fileConversionProcessBuilder = new ProcessBuilder(fileConversionCommand.split(" "));
				fileConversionProcessBuilder.environment().put("IDEA_maxHeapSize", System.getProperty("maxHeapSize"));
				Process fileConversionProcess = fileConversionProcessBuilder.start();
				StreamGobbler errorGobbler = new StreamGobbler(fileConversionProcess.getErrorStream(), "ERROR");            
				StreamGobbler outputGobbler = new StreamGobbler(fileConversionProcess.getInputStream(), "OUTPUT");
				errorGobbler.start();
				outputGobbler.start();
				fileConversionProcess.waitFor();
				if (! jpegFile.canRead()){
					SwingUtilities.invokeLater(new Thread(){
							public void run(){
								JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																							"A JPEG file could not be created.\nPlease report this problem.\n" + fileConversionCommand + "\n",
																							"Visualization Error:  File Conversion",
																							JOptionPane.ERROR_MESSAGE);
							}
						});
					selectedSitesPanel.add(new JLabel("error"));
					return selectedSitesPanel;
				}
				fileConversionProcessBuilder = new ProcessBuilder(fileConversionCommand2.split(" "));
				fileConversionProcessBuilder.environment().put("IDEA_maxHeapSize", System.getProperty("maxHeapSize"));
				fileConversionProcess = fileConversionProcessBuilder.start();
				errorGobbler = new StreamGobbler(fileConversionProcess.getErrorStream(), "ERROR");            
				outputGobbler = new StreamGobbler(fileConversionProcess.getInputStream(), "OUTPUT");
				errorGobbler.start();
				outputGobbler.start();
				fileConversionProcess.waitFor();
				if (! keyJPEGFile.canRead()){
					SwingUtilities.invokeLater(new Thread(){
							public void run(){
								JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																							"A JPEG file could not be created.\nPlease report this problem.\n" + fileConversionCommand2 + "\n",
																							"Visualization Error:  File Conversion",
																							JOptionPane.ERROR_MESSAGE);
							}
						});
					selectedSitesPanel.add(new JLabel("error"));
					return selectedSitesPanel;
				}
	    }
	    catch (InterruptedException ie){
				// N/A
	    }
	    catch (IOException ioe){
				SwingUtilities.invokeLater(new Thread(){
						public void run(){
							JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																						"An I/O error occurred during visualization.\nSee the console for details.",
																						"I/O Error",
																						JOptionPane.ERROR_MESSAGE);
							if (jpegFile.exists() && (! jpegFile.delete())){
								JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																							"Because of the I/O error, the JPEG file generated ("
																							+ jpegFile.getAbsolutePath()
																							+ ") is invalid.\nIDEA attempted to delete this file but failed.\nTo avoid problems recreating it, it is recommended that you delete it yourself.",
																							"Invalid JPEG File Could Not Be Deleted",
																							JOptionPane.WARNING_MESSAGE);
							}
							if (keyJPEGFile.exists() && (! keyJPEGFile.delete())){
								JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																							"Because of the I/O error, a JPEG file generated ("
																							+ keyJPEGFile.getAbsolutePath()
																							+ ") is invalid.\nIDEA attempted to delete this file but failed.\nTo avoid problems recreating it, it is recommended that you delete it yourself.",
																							"Invalid JPEG File Could Not Be Deleted",
																							JOptionPane.WARNING_MESSAGE);
							}
						}
					});
				ioe.printStackTrace();
				return selectedSitesPanel;
	    }
		}
		selectedSitesTitle.setText("Selected Sites:  "
															 + (jpegFilename.endsWith("NEB.jpg") ? "Naïve" : "Bayes")
															 + " Empirical Bayes Analysis for "
															 + datasetIdentifier
															 + ", Model "
															 + model);
	
		ImageIcon keyIcon = new ImageIcon(keyJPEGFilename, "Key");
		int maxKeyWidth = (int) IDEAConstants.SCREEN_SIZE.getWidth();
		if (keyIcon.getIconWidth() > maxKeyWidth){
			Image key = keyIcon.getImage();
			int widthToCropTo = (int) (maxKeyWidth * 3 / 2);
			if (keyIcon.getIconWidth() > widthToCropTo){
				CropImageFilter cropper = new CropImageFilter(0, 0, widthToCropTo, keyIcon.getIconHeight());
				Image croppedKey = createImage(new FilteredImageSource(key.getSource(), cropper));
				key = croppedKey;
			}
			ReplicateScaleFilter scaler = new ReplicateScaleFilter(maxKeyWidth, keyIcon.getIconHeight());
			Image scaledKey = createImage(new FilteredImageSource(key.getSource(), scaler));
			RGBImageFilter whiteToGray = new RGBImageFilter(){
					//this.canFilterIndexColorModel = true;

					public int filterRGB(int x, int y, int rgb){
						return (rgb == 0xffffffff) ? selectedSitesPanel.getBackground().getRGB() : rgb;
					}
				};
			Image scaledKeyOnGray = createImage(new FilteredImageSource(scaledKey.getSource(), whiteToGray));
			keyIcon = new ImageIcon(scaledKeyOnGray, keyIcon.getDescription());
		}
				
		JLabel keyPicture = new JLabel(keyIcon);
		keyPicture.setIconTextGap(0);
		keyPicture.setOpaque(false);
		keyPicture.setBackground(selectedSitesPanel.getBackground());
		keyPicture.setMaximumSize(new Dimension((int) IDEAConstants.SCREEN_SIZE.getWidth(), keyIcon.getIconHeight()));
		selectedSitesPanel.add(titlePanel);
		selectedSitesPanel.add(keyPicture);
		ImageIcon selectedSitesIcon =	new ImageIcon(jpegFilename, selectedSitesTitle.getText());
		JLabel selectedSitesPicture = new JLabel(selectedSitesIcon);
		selectedSitesPicture.setIconTextGap(0);
		selectedSitesPicture.setOpaque(true);
		selectedSitesPicture.setBackground(Color.WHITE);
		Border lineBorder = BorderFactory.createLineBorder(new Color(128, 0, 160));
		selectedSitesPicture.setBorder(lineBorder);
		Insets borderInsets = lineBorder.getBorderInsets(selectedSitesPicture);
		selectedSitesPicture.setMaximumSize(new Dimension(selectedSitesIcon.getIconWidth()
																											+ borderInsets.left
																											+ borderInsets.right,
																											selectedSitesIcon.getIconHeight()
																											+ borderInsets.top
																											+ borderInsets.bottom));
		JScrollPane picturePane = new JScrollPane(selectedSitesPicture,
																							JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
																							JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		picturePane.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectedSitesPanel.add(picturePane);
		final String jpegNameReference = jpegFilename;
		saveButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					JOptionPane.showMessageDialog(IDEAOutputGUI.this,
																				"This picture was automatically saved as\n" + jpegNameReference + " .",
																				"File Automatically Saved",
																				JOptionPane.INFORMATION_MESSAGE);
				}
	    });
		saveButton.setEnabled(true);																				
		return selectedSitesPanel;
	}

}
