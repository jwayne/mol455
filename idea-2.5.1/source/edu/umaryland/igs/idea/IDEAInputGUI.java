package edu.umaryland.igs.idea;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

// Apache Commons IO is used to recursively delete the directory containing old results to be overwritten.
import org.apache.commons.io.FileUtils;

import edu.umaryland.igs.aegan.utils.FileClerk;
import edu.umaryland.igs.aegan.utils.FileParser;
import edu.umaryland.igs.aegan.utils.WrapperPane;
import org.tigr.antware.shared.exceptions.InvalidArgumentException;
import org.tigr.antware.shared.exceptions.InvalidFileFormatException;
import org.tigr.antware.shared.util.ExceptionHandler;
import org.tigr.antware.shared.util.ExtractArguments;
import org.tigr.antware.shared.util.Logger;
import org.tigr.antware.shared.util.StringUtils;

/**
 * <code>IDEAInputGUI</code> is the main class in the IDEA suite, which
 * is designed to allow users to perform analyses of molecular evolution and
 * view their results.
 * When it is invoked via the "idea" Perl wrapper, a display allowing
 * the user either to run a new analysis or to view existing results
 * (via <code>IDEAOutputGUI</code>) is brought up.
 * When running a new analysis, the user may input parameters to the
 * underlying analysis programs, run the programs and monitor their progress.
 * The underlying programs are executed using the Workflow system.
 * Much of the code governing the display is in classes <code>Parameter</code> and <code>ParameterSet</code>.
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
public class IDEAInputGUI extends JFrame{

	/**
	 * The variable <code>logger</code> holds the instance of the logger for this class.
	 */
	private static Logger logger;
	private static Properties loggingProperties; // the logger's properties, read from a log4j.properties file if available

	// This is a hint on how to call IDEAInputGUI (which is normally called from the Perl script idea).
	protected static String usage =
		"Usage:\n" 
		+ " IDEAInputGUI \n"
		+ "   [-c <configuration file>\n"
		+ "   [-l=<log file>] \n"
		+ "   [-v=<log level> (default INFO or 3)] \n"
		+ "   [--help < Print help message> ]\n"
		+ "   [<file listing dataset names>]\n"; 

	// Variable that holds the description of the class
	protected static String classDescription =
		"IDEAInputGUI is used to invoke IDEA with a graphical user interface.";
	static String invocationDir = System.getProperty("invocationDirectory"); // the invocation directory

	/**
	 * IDEA's status can take one of four values:  NOT_STARTED, RUNNING, SUCCEEDED or FAILED.
	 * Each status corresponds to one action available to the user from the final input GUI page.
	 */
	private enum Status{
		NOT_STARTED("Start"),
		RUNNING("Monitor"),
		SUCCEEDED("View Results"),
		FAILED("View Log");

		String availableAction;

		Status(String s){
			availableAction = s;
		}
	}
	private Status ideaStatus = Status.NOT_STARTED;

	// This is the directory containing standard configuration files for IDEA.
  static final String IDEA_DIR = System.getProperty("ideaDir");

	static final StringBuffer PAML_BIN_DIR = new StringBuffer(32);

	// The default control/configuration file may be overridden by users.
  static final LinkedList<String> DEFAULT_CONTROL_FILENAMES = new LinkedList<String>();

	// This hash stores the name of the method necessary to recreate each recreatable display window.
	// A display window may need to be recreated if the user loads a new configuration file.
	private HashMap<JFrame, String> recreationMethods = new HashMap<JFrame, String>();

	// A static initializer is used to set up the PAML bin directory, the default control filenames and logging.
	static{
		FileParser pamlRunnerProgramFileParser = null;
		try{
			pamlRunnerProgramFileParser = new FileParser(IDEA_DIR + "idea-B-run-paml.pl");
			Pattern pamlBinDirPattern = Pattern.compile("^\\$options\\{\"paml_bin_dir\"\\} \\= \"(\\S+)\"\\;");
			while (true){
				String nextLine = pamlRunnerProgramFileParser.nextLine();
				Matcher pamlBinDirPatternMatcher = pamlBinDirPattern.matcher(nextLine);
				if (pamlBinDirPatternMatcher.lookingAt()){
					PAML_BIN_DIR.insert(0, pamlBinDirPatternMatcher.group(1));
					pamlRunnerProgramFileParser.closeFile();
					break;
				}
			}
		}
		catch (EOFException eofe){
			pamlRunnerProgramFileParser.closeFile();
			System.err.println("Invalid IDEA installation:  PAML directory could not be located.");
			System.exit(1);
		}
		catch (FileNotFoundException fnfe){
			System.err.println("Invalid IDEA installation:  missing idea-B-run-paml.pl");
			System.exit(1);
		}
		catch (IOException IOE){
			pamlRunnerProgramFileParser.closeFile();
			System.err.println("An I/O error interfered with launching IDEA.  It may be transient.");
			System.exit(1);
		}
		DEFAULT_CONTROL_FILENAMES.add(IDEA_DIR + "DEFAULT-CODEML.ctl");
		DEFAULT_CONTROL_FILENAMES.add(IDEA_DIR + "DEFAULT-BASEML.ctl");
		try{
			loggingProperties = new Properties();
			loggingProperties.load(new FileInputStream(IDEA_DIR + "log4j.properties"));
			 loggingProperties.setProperty("log4j.appender.FILE.File",
																		 System.getProperty("invocationDirectory")
																		 + "/"
																		 + loggingProperties.getProperty("log4j.appender.FILE.File"));
			org.apache.log4j.PropertyConfigurator.configure(loggingProperties);
		}
		catch (IOException fnfe){
			org.apache.log4j.BasicConfigurator.configure();
		}
		logger = new Logger("edu.umaryland.igs.idea.IDEAInputGUI");
	}

	// The configuration object represents all configuration parameters.
	private IDEAConfiguration config;

	// This is a separate thread for running the Workflow engine, which executes the analysis scripts.
	private Thread engineRunningThread;

	// This is the name of the instance file the Workflow process uses to store information.
  private String instanceFilename;

	// This holds any exception thrown by the engine-running thread for examination by the main thread.
	private IOException saveException = null;

	// This is the name of a file specifying configuration parameters for IDEA;
	// it may be either in PAML's .ctl format or in IDEA's format, an XML-based format representing the
	// config object.
	private String controlFilename;

	private String outputDirName;  // This is the name of the directory where IDEA's output files will be stored.

	// This panel represents the main page of the GUI.  It is a member of IDEAInputGUI so that various subroutines can
	// recreate it as needed.
	private JPanel configurationPanel;
	
	/**
	 * This creates a new <code>IDEAInputGUI</code> instance, sets the filename for
	 * the dataset name list, loads the configuration from the supplied file and brings up
	 * the first page of the display.
	 *
	 * @param gnlFilename the name of the file containing the list of dataset names
	 * @param cFilename the name of the configuration file
	 */
	private IDEAInputGUI(String gnlFilename, String cFilename) {
		super();
		
		if (logger.isInfoEnabled()){
			logger.info("Launching input GUI for " + gnlFilename + " ...");
		}
    String datasetNameListFilename = gnlFilename;
		if (! datasetNameListFilename.equals("")){
			datasetNameListFilename = new File(datasetNameListFilename).getAbsolutePath();
		}
		controlFilename = new File(cFilename).getAbsolutePath();
		loadConfiguration(this, "codeml");
		config.additionalParameters.getParameter("Dataset name list").updateValue(datasetNameListFilename);
                
		setTitle("IDEA Input");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
		// Initialize the GUI.
		initialize();  
	}

	/**
	 * This convenience constructor creates an IDEAInputGUI with no starting dataset name list and
	 * the default control file.
	 */
	IDEAInputGUI(){
		this("", DEFAULT_CONTROL_FILENAMES.getFirst());
	}

	/**
	 * This <code>loadConfiguration</code> method loads the configuration from the current
	 * control filename.  It handles any checked exceptions.
	 * 
	 * @param owner the window from which this method was called
	 * @param programString the value of the "PAML program" parameter (baseml or codeml) before loading the configuration
	 */
	private void loadConfiguration(JFrame owner, String programString){
		try{
			config = new IDEAConfiguration(controlFilename);
			if (! config.isFullConfiguration()){
				// The program must be reset because it is overwritten when a .ctl-format configuration is loaded.
				BooleanParameter newProgram = (BooleanParameter) config.getParameter("PAML program");
				if (! newProgram.value.equals(programString)){
					String newProgramName = newProgram.valueString();
					WrapperPane.showMessageDialog(owner,
																				"The file " + controlFilename + " seems to be a " + newProgramName
																				+ " control file.  PAML program has been switched to " + newProgramName + ".",
																				"PAML Program Mismatch",
																				JOptionPane.WARNING_MESSAGE);
				}
			}
			String recreationMethodName = recreationMethods.get(owner);
			if (recreationMethodName != null){
				// Although the method to invoke never takes an argument, Java requires that these null pointers be cast to
				// array types to avoid getting a warning.
				getClass().getDeclaredMethod(recreationMethodName, (Class[]) null).invoke(this, (Object[]) null);
				if (owner != this){  // Don't accumulate secondary windows.
					owner.dispose();
				}
			}
		}
		catch (InvalidFileFormatException iffe){
			WrapperPane.showMessageDialog(owner,
																		"The file "
																		+ controlFilename
																		+ " is not a valid configuration file or is in the wrong format:\n\n"
																		+ iffe.getMessage(),
																		"Invalid File Format",
																		JOptionPane.ERROR_MESSAGE);
		}
		catch (FileNotFoundException fnfe){
			JOptionPane.showMessageDialog(owner,
																		"The file " + controlFilename + " was not found.",
																		"File Not Found",
																		JOptionPane.ERROR_MESSAGE);
		}
		catch (IOException ioe){
			JOptionPane.showMessageDialog(owner,
																		"An I/O error occurred.  Your file was not loaded.",
																		"I/O Error",
																		JOptionPane.ERROR_MESSAGE);
		}
		catch (IllegalAccessException iae){
			JOptionPane.showMessageDialog(owner,
																		"An internal error occurred.  Your file was not loaded.\n"
																		+ iae.getMessage()
																		+ "\nPlease report this problem.",
																		"Internal Error",
																		JOptionPane.ERROR_MESSAGE);
		}
		catch (InvocationTargetException ite){
			JOptionPane.showMessageDialog(owner,
																		"An internal error occurred.  Your file was not loaded.\n"
																		+ ite.getCause().getMessage()
																		+ "\nPlease report this problem.",
																		"Internal Error",
																		JOptionPane.ERROR_MESSAGE);
		}
		catch (NoSuchMethodException nsme){
			JOptionPane.showMessageDialog(owner,
																		"An internal error occurred.  Your file was not loaded.\n"
																		+ nsme.getMessage()
																		+ "\nPlease report this problem.",
																		"Internal Error",
																		JOptionPane.ERROR_MESSAGE);
		}
	}
   
	/**
	 * This <code>saveConfiguration</code> method saves the configuration to the current
	 * control filename.  It handles any checked exceptions.
	 * 
	 * @param owner the window from which this method was called
	 */
	private void saveConfiguration(JFrame owner){
		try{
			config.save(controlFilename);
		}
		catch (FileNotFoundException fnfe){
			JOptionPane.showMessageDialog(owner,
																		controlFilename + " could not be created.",
																		"File Could Not Be Created",
																		JOptionPane.ERROR_MESSAGE);
		}
		catch (IOException ioe){
			JOptionPane.showMessageDialog(owner,
																		"An I/O error occurred.  Your file was not saved.",
																		"I/O Error",
																		JOptionPane.ERROR_MESSAGE);
		}
	}
    
	/**
	 * This adds a window-closing listener and then brings up the first display page. 
	 */    
	private void initialize(){
		addWindowListener(new WindowAdapter(){
				public void windowClosing(WindowEvent e){
					if ((ideaStatus == Status.RUNNING) && (instanceFilename != null)){
						// If analysis has started, warn the user that closing the window will abort the analysis.
						int choice = JOptionPane.showConfirmDialog(IDEAInputGUI.this,
																											 "Closing this window will abort your analysis.  Proceed?",
																											 "Abort Analysis?",
																											 JOptionPane.YES_NO_OPTION,
																											 JOptionPane.WARNING_MESSAGE);
						if (choice == JOptionPane.YES_OPTION){
							setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
							try{
								Runtime.getRuntime().exec("KillWorkflow -i=" + instanceFilename).waitFor();
							}
							catch (IOException ioe){
								JOptionPane.showMessageDialog(IDEAInputGUI.this,
																							"An I/O error occurred while attempting to terminate your analyis.\nYour analysis may not have been terminated.\nPlease use KillWorkflow to terminate it.",
																							"I/O Error",
																							JOptionPane.ERROR_MESSAGE);
							}
							catch (InterruptedException ie){
								// N/A
							}
							setCursor(null);
							System.exit(0);
						}
					}
					else{
						System.exit(0);
					}
				}
			});
		showIntroPage();
	}
 
	/**
	 * This brings up the second display page, which contains the configuration panel.
	 * The second page is represented by <code>this</code>, an <code>IDEAInputGUI</code>
	 * that extends <code>JFrame</code>.
	 */
	private void showSecondPage(){
		createConfigurationPanel();
		getContentPane().removeAll();
		getContentPane().add(configurationPanel);
		setJMenuBar(menuBar(this));
		pack();
		setLocationRelativeTo(null);  // Center the window on the screen.
		setIconImage(IDEAConstants.LOGO_THUMB);
		recreationMethods.put(this, "showSecondPage");
		setVisible(true);
	}		


	/**
	 * This brings up the intro page, which allows the user to choose to start a new analyis or
	 * view previous results.  The IDEA logo is displayed in the background.
	 * The first page is represented by a local <code>JFrame</code> object.
	 */
	private void showIntroPage(){
		String title = "IDEA " + IDEAConfiguration.IDEA_VERSION;
		final JFrame introPage = new JFrame(title);
		introPage.setIconImage(IDEAConstants.LOGO_THUMB);
		introPage.addWindowListener(new WindowAdapter()  {
				public void windowClosing(WindowEvent e)  {
					System.exit(0);
				}
			});

		// Attempt to display the IDEA logo in the background.
		int imageHeight = IDEAConstants.SCREEN_SIZE.height * 2 / 3;
		int imageWidth = imageHeight * 4 / 3;
		Image backgroundImageAsRead = null;
		try{
			// The ImageIO class is used to read the image to ensure that there is no delay in displaying it.
			backgroundImageAsRead = ImageIO.read(ClassLoader.getSystemResource("idea-logo-large.jpg"));
		}
		catch (IOException ioe){
			// Let the program continue with a null image.
		}
		final Image backgroundImage =
			(backgroundImageAsRead == null)
			? null
			: backgroundImageAsRead.getScaledInstance(-1, imageHeight, Image.SCALE_SMOOTH);
		JPanel introPagePanel = new JPanel(){
				public void paintComponent(Graphics g) {
					super.paintComponent(g);

					// Attempting to draw a null image will have no effect.
					g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
				}
			};
		introPagePanel.setLayout(new BoxLayout(introPagePanel, BoxLayout.Y_AXIS));

		// Add the buttons.  The button panel is used to customize the buttons' locations.
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
		buttonPanel.setOpaque(false);
		final JButton startNewAnalysisButton = new JButton("Start New Analysis");
		startNewAnalysisButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
		startNewAnalysisButton.setFont(startNewAnalysisButton.getFont().deriveFont(15.0f));
		startNewAnalysisButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					introPage.setVisible(false);
					IDEAInputGUI.this.showFollowUpPage();
				}
			});
		JButton viewResultsOfPreviousAnalysisButton = new JButton("View Results of Previous Analysis");
		viewResultsOfPreviousAnalysisButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
		viewResultsOfPreviousAnalysisButton.setFont(startNewAnalysisButton.getFont().deriveFont(15.0f));
		viewResultsOfPreviousAnalysisButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					introPage.dispose();
					new IDEAOutputGUI();
				}
			});
		if (IDEAConstants.OS.startsWith("Mac OS")){
			startNewAnalysisButton.setBackground(Color.WHITE);
			viewResultsOfPreviousAnalysisButton.setBackground(Color.WHITE);
		}
		buttonPanel.add(startNewAnalysisButton);
		buttonPanel.add(Box.createVerticalStrut(startNewAnalysisButton.getPreferredSize().height * 1 / 3));
		buttonPanel.add(viewResultsOfPreviousAnalysisButton);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		introPagePanel.setLayout(null);
		introPagePanel.add(Box.createVerticalStrut(imageHeight - buttonPanel.getPreferredSize().height));
		introPagePanel.add(Box.createHorizontalStrut(backgroundImage.getWidth(null)));
		introPagePanel.add(buttonPanel);
		buttonPanel.setBounds(imageWidth - buttonPanel.getPreferredSize().width - 18,
													imageHeight * 74 / 100,
													buttonPanel.getPreferredSize().width,
													buttonPanel.getPreferredSize().height);
		introPagePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		introPage.getContentPane().add(introPagePanel);
		introPage.pack();
		introPage.setSize(Math.max(introPage.getWidth(), backgroundImage.getWidth(null)),
											Math.max(introPage.getHeight(), imageHeight));
		introPage.setLocationRelativeTo(null);  // Center the window on the screen.
		introPage.setVisible(true);
	}

	/**
	 * This brings up a page which allows the user to select a PAML program (codeml or baseml).
	 * The follow-up page is represented by a local <code>JFrame</code> object.
	 */
	private void showFollowUpPage(){
		final JFrame followUpPage = new JFrame("Select PAML Program");
		followUpPage.addWindowListener(new WindowAdapter()  {
				public void windowClosing(WindowEvent e)  {
					System.exit(0);
				}
			});
		JPanel followUpPagePanel = new JPanel();
		followUpPagePanel.setLayout(new BoxLayout(followUpPagePanel, BoxLayout.Y_AXIS));
		JPanel followUpPageParameterEntryArea = config.followUpPageParameters.entryArea(1);
		followUpPageParameterEntryArea.setAlignmentX(Component.CENTER_ALIGNMENT);
		followUpPageParameterEntryArea.setMaximumSize(followUpPageParameterEntryArea.getPreferredSize());
		followUpPagePanel.add(followUpPageParameterEntryArea);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
		final JButton continueButton = new JButton("Continue");
		continueButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		continueButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					followUpPage.setVisible(false);
					if (DEFAULT_CONTROL_FILENAMES.contains(controlFilename)){
						// If the user hasn't specified a custom control file, use the default corresponding to the correct program.
						for (String option : DEFAULT_CONTROL_FILENAMES){
							String programString = config.additionalParameters.getParameter("PAML program").valueString();
							if (option.contains(programString.toUpperCase())
									&& (! option.equals(controlFilename))){
								controlFilename = option;
								loadConfiguration(followUpPage, programString);
							}
						}
					}
					IDEAInputGUI.this.showFirstPage();
				}
			});
		buttonPanel.add(Box.createVerticalStrut(continueButton.getPreferredSize().height * 2 / 3));
		buttonPanel.add(continueButton);
		final JButton backButton = new JButton("Back");
		backButton.addActionListener(new ActionListener(){
				// When the user clicks "Back", hide the first page and bring up the intro page.
				public void actionPerformed(ActionEvent e){
					followUpPage.setVisible(false);
					IDEAInputGUI.this.showIntroPage();
				}
			});
		backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		buttonPanel.add(Box.createVerticalStrut(backButton.getPreferredSize().height * 2 / 3));
		buttonPanel.add(backButton);
		followUpPagePanel.add(buttonPanel);
		followUpPage.getContentPane().add(followUpPagePanel);
		followUpPage.pack();
		followUpPage.setSize(new Dimension(IDEAConstants.SCREEN_SIZE.width / 4, followUpPage.getSize().height));
		followUpPage.setLocationRelativeTo(null);  // Center the window on the screen.
		followUpPage.setIconImage(IDEAConstants.LOGO_THUMB);
		followUpPage.setVisible(true);
	}
		

	/**
	 * This brings up the first page, which allows the user to select the running mode and
	 * input/output options.  The first page is represented by a local <code>JFrame</code> object.
	 */
	private void showFirstPage(){
		final JFrame firstPage = new JFrame("Input/Output Options");
		firstPage.addWindowListener(new WindowAdapter()  {
				public void windowClosing(WindowEvent e)  {
					System.exit(0);
				}
			});
		firstPage.setIconImage(IDEAConstants.LOGO_THUMB);
		firstPage.getContentPane().setLayout(new BorderLayout());

		// Display the top section with a divider below it.
		final JPanel variablePanel = new JPanel();
		variablePanel.setLayout(new BoxLayout(variablePanel, BoxLayout.Y_AXIS));
		JPanel firstPageTopParametersEntryArea = config.firstPageTopParameters.entryArea(1);
		firstPageTopParametersEntryArea.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, IDEAColors.GRAY_BLUE));
		variablePanel.add(firstPageTopParametersEntryArea);
		variablePanel.add(new JSeparator());

		// Display the middle section with a divider below it.
		JPanel gridPanel = new JPanel(new GridLayout(1, 2));
		JPanel multipleDatasetPanel = config.multipleDatasetParameters.entryArea(1);
		JPanel multipleDatasetPanelWrapper = new JPanel();
		FlowLayout wrapperLayout = new FlowLayout(FlowLayout.LEADING);
		wrapperLayout.setVgap(0);
		multipleDatasetPanelWrapper.setLayout(wrapperLayout);
		multipleDatasetPanelWrapper.add(multipleDatasetPanel);
		multipleDatasetPanelWrapper.add(Box.createHorizontalStrut(30));
		gridPanel.add(multipleDatasetPanelWrapper);
		gridPanel.add(config.singleDatasetParameters.entryArea(1));
		gridPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, IDEAColors.GRAY_BLUE));
		variablePanel.add(gridPanel);
		variablePanel.add(new JSeparator());

		// Display the bottom section.  Java's focus subsystem is used to ensure all paramter changes are recorded.
		JPanel firstPageBottomParametersEntryArea = config.firstPageBottomParameters.entryArea(2);
		variablePanel.add(firstPageBottomParametersEntryArea);
		variablePanel.add(Box.createVerticalStrut(firstPageBottomParametersEntryArea.getPreferredSize().height / 4));
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
		final JButton nextButton = new JButton("Continue");
		nextButton.addActionListener(new ActionListener(){
				// When the user clicks "Continue", hide the first page and bring up the second.
				public void actionPerformed(ActionEvent e){
					KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner(); // Record all parameter changes.
					firstPage.setVisible(false);
					IDEAInputGUI.this.showSecondPage();
				}
			});
		nextButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		buttonPanel.add(nextButton);
		buttonPanel.add(Box.createVerticalStrut(nextButton.getPreferredSize().height * 2 / 3));
		final JButton backButton = new JButton("Back");
		backButton.addActionListener(new ActionListener(){
				// When the user clicks "Back", hide the first page and bring up the intro page.
				public void actionPerformed(ActionEvent e){
					KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner(); // Record all parameter changes.
					firstPage.setVisible(false);
					IDEAInputGUI.this.showFollowUpPage();
				}
			});
		backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		buttonPanel.add(backButton);
		buttonPanel.setMinimumSize(buttonPanel.getPreferredSize());
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		variablePanel.add(buttonPanel);
		variablePanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		variablePanel.setMinimumSize(variablePanel.getPreferredSize());
		firstPage.getContentPane().add(variablePanel, BorderLayout.CENTER);
		firstPage.getContentPane().setMinimumSize(firstPage.getContentPane().getPreferredSize());
		firstPage.getContentPane().setSize(firstPage.getContentPane().getPreferredSize());
		firstPage.setJMenuBar(menuBar(firstPage));
		firstPage.pack();
		firstPage.setSize(Math.min(firstPage.getPreferredSize().width, IDEAConstants.SCREEN_SIZE.width),
											Math.min(firstPage.getPreferredSize().height, IDEAConstants.SCREEN_SIZE.height));
		firstPage.setLocationRelativeTo(null);  // Center the window on the screen.
		recreationMethods.put(firstPage, "showFirstPage");
		firstPage.setVisible(true);
	}

	/**
	 * This creates and returns the menu bar.  The actions to take in response to menu-option selections are
	 * specified here.
	 *
	 * @param owner the frame with which this menu bar is associated
	 *
	 * @return the menu bar
	 */
	private JMenuBar menuBar(final JFrame owner){
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");

		// The "Load dataset list..." menu option has been removed.  Now the user must use the corresponding
		// parameter to load the list.

// 		JMenuItem loadDatasetList = new JMenuItem("Load dataset list...");
// 		loadDatasetList.setFont(new Font(loadDatasetList.getFont().getName(), loadDatasetList.getFont().getStyle(), 10));
// 		loadDatasetList.addActionListener(new ActionListener(){
// 				// When the user selects a dataset name list file, the input directory is updated to be the directory containing
// 				// that file, and the configuration panel is accordingly recreated.
// 				public void actionPerformed(ActionEvent e){
// 					JFileChooser datasetNameListFileChooser =
// 						new JFileChooser(config.additionalParameters.getParameter("Dataset name list").valueString());
// 					if (datasetNameListFileChooser.showDialog(IDEAInputGUI.this, "Select") == JFileChooser.APPROVE_OPTION){
// 						config.additionalParameters.getParameter("Dataset name list").updateValue
// 							(datasetNameListFileChooser.getSelectedFile().getAbsolutePath());
// 						String inputDirectory =
// 							new File(config.additionalParameters.getParameter("Dataset name list").valueString()).getParent();
// 						config.additionalParameters.getParameter("Input directory").updateValue(inputDirectory);
// 						getContentPane().remove(configurationPanel);
// 						createConfigurationPanel();
// 						getContentPane().add(configurationPanel);
// 						int oldWidth = getWidth();
// 						int oldHeight = getHeight();
// 						setSize(0, 0);
// 						setSize(oldWidth, oldHeight);
// 						repaint();
// 					}
// 				}
// 			});
// 		fileMenu.add(loadDatasetList);
// 		fileMenu.addSeparator();
		JMenuItem loadConfiguration = new JMenuItem("Load configuration...");
		loadConfiguration.setFont(new Font(loadConfiguration.getFont().getName(),
																			 loadConfiguration.getFont().getStyle(),
																			 10));
		loadConfiguration.addActionListener(new ActionListener(){
				// When the user loads a configuration file, the configuration panel is recreated.
				public void actionPerformed(ActionEvent e){
					JFileChooser controlFileChooser = new JFileChooser(controlFilename);
					controlFileChooser.setAcceptAllFileFilterUsed(false);
					for (int i = 0; i < IDEAConfiguration.FILE_FORMATS.length; i++){
						controlFileChooser.addChoosableFileFilter(IDEAConfiguration.configurationFileFilter(i));
					}
					if (controlFileChooser.showDialog(owner, "Select") == JFileChooser.APPROVE_OPTION){
						controlFilename = controlFileChooser.getSelectedFile().getAbsolutePath();
						loadConfiguration(owner, config.additionalParameters.getParameter("PAML program").valueString());
						if (configurationPanel != null){
							getContentPane().remove(configurationPanel);
							createConfigurationPanel();
							getContentPane().add(configurationPanel);
							int oldWidth = getWidth();
							int oldHeight = getHeight();
							setSize(0, 0);
							setSize(oldWidth, oldHeight);
							repaint();
						}
						if (logger.isFinestEnabled()){
							logger.finest("The display should be updated.");
						}
					}
				}
			});
		fileMenu.add(loadConfiguration);
		JMenuItem saveConfiguration = new JMenuItem("Save configuration...");
		saveConfiguration.setFont(new Font(saveConfiguration.getFont().getName(),
																			 saveConfiguration.getFont().getStyle(),
																			 10));
		saveConfiguration.addActionListener(new ActionListener(){
				// Saving the configuration is an interactive process.
				public void actionPerformed(ActionEvent e){
					// The name of the current configuration file is suggested unless the default file is being used.
					JFileChooser controlFileChooser = new JFileChooser(DEFAULT_CONTROL_FILENAMES.contains(controlFilename)
																														 ? ""
																														 : controlFilename);
					// The file chooser only shows files in the accepted format (.ctl or .idea).
					// The user chooses one or the other.
					controlFileChooser.setAcceptAllFileFilterUsed(false);
					for (int i = 0; i < IDEAConfiguration.FILE_FORMATS.length; i++){
						final int j = i;
						controlFileChooser.addChoosableFileFilter(IDEAConfiguration.configurationFileFilter(j));
					}
					try{
						if (controlFileChooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION){
							File selectedFile = controlFileChooser.getSelectedFile();
							if (selectedFile.isDirectory()){
								throw new InvalidFileFormatException(selectedFile.getName() + " is a directory.");
							}
							boolean proceed = true;
							if (! (controlFileChooser.accept(selectedFile))){  // if not accepted under the chosen filter
								// If the user supplies a file name that does not match the chosen format but instead matches the other
								// format, two options are given:  to use the format matching the filename or to cancel.
								String questionableFN = selectedFile.getName();
								String chosenFilter = controlFileChooser.getFileFilter().getDescription();
								proceed = false;
								for (int i = 0; i < IDEAConfiguration.FILE_FORMATS.length; i++){
									proceed = true;
									if (IDEAConfiguration.isValidConfig(questionableFN, i)){
										String warning = "It seems that the filename you entered or selected is actually for a file in "
											+ IDEAConfiguration.FILE_FORMATS[i] + " format.";
										String[] choices = {"Save in " + IDEAConfiguration.FILE_FORMATS[i] + " Format",
																				"Cancel"};
										int choice = JOptionPane.showOptionDialog(owner,
																															warning,
																															"Format Discrepancy",
																															JOptionPane.YES_NO_OPTION,
																															JOptionPane.WARNING_MESSAGE,
																															null,
																															choices,
																															choices[1]);
										if (choice != JOptionPane.YES_OPTION){
											proceed = false;
										}
									}
								}
								if (proceed){
									// Supply the appropriate file extension for the chosen format.
									for (int i = 0; i < IDEAConfiguration.FILE_FORMATS.length; i++){
										if (chosenFilter.startsWith(IDEAConfiguration.FILE_FORMATS[i])){
											selectedFile = new File(questionableFN + IDEAConfiguration.FILE_EXTENSIONS[i]);
											break;  // Only one format should have been chosen, so it's not necessary to continue.
										}
									}
								}
							}
							if (proceed){
								// Actually save the configuration.
								controlFilename = selectedFile.getAbsolutePath();
								saveConfiguration(owner);
							}
						}
					}
					catch (InvalidFileFormatException iffe){
						JOptionPane.showMessageDialog(owner,
																					iffe.getMessage(),
																					"Directories Not Allowed",
																					JOptionPane.ERROR_MESSAGE);
					}
				}
			});
		fileMenu.add(saveConfiguration);
		fileMenu.addSeparator();

		// This menu option allows the user to switch to viewing previous results.
		JMenuItem loadPreviousResults = new JMenuItem("Load previous results");
		loadPreviousResults.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					owner.dispose();
					new IDEAOutputGUI();
				}
			});
		fileMenu.add(loadPreviousResults);
		menuBar.add(fileMenu);

		// The "IDEA Documentation" item in the Help menu launches the user's default browser to bring up the online help.
		JMenu helpMenu = new JMenu("Help");
		JMenuItem help = new JMenuItem("IDEA Documentation");
		help.addActionListener(new ActionListener(){
				// The user should be referred to the internal website for documentation.
				public void actionPerformed(ActionEvent e){
					BrowserLauncher.launchBrowser("http://ideanalyses.sourceforge.net/user_guide.php", owner);
				}
			});
		helpMenu.add(help);
		menuBar.add(helpMenu);
		return menuBar;
	}

	/**
	 * The <code>createConfigurationPanel</code> method recreates the configuration panel.
	 */
	public void createConfigurationPanel() {
		//configurationPanel = new ConfigurationPanel(config.pamlParameters(), config.additionalParameters());
		configurationPanel = buildConfigurationPanel();
	}

	/**
	 * The <code>buildConfigurationPanel</code> method creates and returns a new configuration panel.
	 *
	 * @return the new configuration panel
	 */
	protected JPanel buildConfigurationPanel(){
		// The parameters are grouped as data parameters, model parameters and other parameters.
		JPanel rv = new JPanel();
		rv.setLayout(new BoxLayout(rv, BoxLayout.Y_AXIS));
		rv.setAlignmentX(Component.LEFT_ALIGNMENT);
		ParameterSet[] secondPageParameterSets = {config.dataParameters, config.modelParameters, config.otherParameters};
		int maxNameLengthOnSecondPage = ParameterSet.maxNameLength(secondPageParameterSets);
		LinkedList<JPanel> secondPageEntryAreas = new LinkedList<JPanel>();
		secondPageEntryAreas.add(config.dataParameters.entryArea(2, maxNameLengthOnSecondPage));
		secondPageEntryAreas.add(config.modelParameters.entryArea(2, maxNameLengthOnSecondPage));
		secondPageEntryAreas.add(config.otherParameters.entryArea(2, maxNameLengthOnSecondPage));
		for (JPanel entryArea : secondPageEntryAreas){
			entryArea.setAlignmentX(Component.LEFT_ALIGNMENT);
			rv.add(entryArea);
			rv.add(Box.createVerticalStrut(rv.getFont().getSize()));
		}
		GridBagLayout buttonPanelLayout = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		final JPanel buttonPanel = new JPanel(buttonPanelLayout);
		final JButton backButton = new JButton("Back");
		backButton.setFont(new Font(backButton.getFont().getName(), backButton.getFont().getStyle(), 10));
		if (ideaStatus == Status.RUNNING){
			backButton.setEnabled(false);
		}
		buttonPanelLayout.setConstraints(backButton, constraints);
		final JLabel rejectionLabel = new JLabel();
		rejectionLabel.setText("<html><div align=center>" + ((ideaStatus == Status.RUNNING) ? "You may not change parameter values once execution<br>has begun.  To abort, close this window." : "&nbsp;") + "</div></html>");
		rejectionLabel.setFont(new Font(rejectionLabel.getFont().getName(), rejectionLabel.getFont().getStyle(), 10));
		rejectionLabel.setForeground((ideaStatus == Status.RUNNING) ? Color.GRAY : rejectionLabel.getBackground());
		buttonPanelLayout.setConstraints(rejectionLabel, constraints);

		// The text of this button (and what happens when the user presses it) depends on IDEA's current status.
		final JButton startButton = new JButton(ideaStatus.availableAction);
		startButton.setFont(new Font(startButton.getFont().getName(), startButton.getFont().getStyle(), 10));
		buttonPanelLayout.setConstraints(startButton, constraints);
		final JLabel warningLabel = new JLabel("<html><br>&nbsp;<br></html>");
		warningLabel.setForeground(warningLabel.getBackground());
		warningLabel.setFont(new Font(warningLabel.getFont().getName(), warningLabel.getFont().getStyle(), 10));
		buttonPanelLayout.setConstraints(warningLabel, constraints);

		switch (ideaStatus){
		case NOT_STARTED:
			// The start button is disabled until a dataset list is loaded.
			String datasetNameListFilename = config.additionalParameters.getParameter("Dataset name list").valueString();
			String ideaMode = config.additionalParameters.getParameter("IDEA mode").valueString();
			if (((datasetNameListFilename == null) || (! new File(datasetNameListFilename).canRead()))
					&& ideaMode.equals("Multi-Dataset")){
				startButton.setEnabled(false);
				warningLabel.setForeground(Color.GRAY);
				warningLabel.setText("<html>You must load a dataset list before starting.<br><br></html>");
			}
			break;
		case RUNNING:
			// Don't display any message below the Monitor button.
			warningLabel.setForeground(warningLabel.getBackground());
			warningLabel.setText("<html><br>&nbsp;<br></html>");
			break;
		case SUCCEEDED:
			warningLabel.setForeground(Color.BLACK);
			warningLabel.setText("<html>Your analysis is finished.<br><br></html>");
			break;
		case FAILED:
			warningLabel.setForeground(Color.RED);
			warningLabel.setText("<html>Your analysis failed.<br><br></html>");
			break;
		}  // end of switch statement

		startButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					// The button may actually have a different function if the analysis has already started,
					// so it's necessary to check.
					//if (((JButton) e.getSource()).getText().equals("Start")){
					switch (ideaStatus){
					case NOT_STARTED:
						KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();//Record all parameter changes.
						startButton.setEnabled(false);
						backButton.setEnabled(false);
						String program = config.additionalParameters().getParameter("PAML program").valueString();
						if (logger.isInfoEnabled()){
							logger.info("Starting with program = [" + program + "]!");
						}
						try{
							// The method recordInstanceFilename calls startIDEA, kicking off the workflow.
							recordInstanceFilename();
							if (instanceFilename == null){  // The user heeded a warning and canceled.
								startButton.setEnabled(true);
								backButton.setEnabled(true);
							}
						}
						catch (IOException ioe){
							if (logger.isFinestEnabled()){
								logger.finest("An IOException occurred while trying to create or run the workflow:  "
															+ ioe.getMessage());
							}
							JOptionPane.showMessageDialog(IDEAInputGUI.this,
																						"An I/O error occurred.\nThis may be a transient problem.\nSee the console for details.\n",
																						"I/O Error",
																						JOptionPane.ERROR_MESSAGE);
							ioe.printStackTrace();
							startButton.setEnabled(true);
							backButton.setEnabled(true);
						}
						break;
					case RUNNING:
						// If the user clicks "Monitor", a Workflow Monitor is called up by executing a system command.
						String instanceFilename = getInstanceFilename();
						if (logger.isInfoEnabled()){
							logger.info("Calling MonitorWorkflow on " + instanceFilename + "...");
						}
						String monitorCommand = "MonitorWorkflow -i " + getInstanceFilename() + " --refresh 120";
						File instanceFile = new File(instanceFilename);
						while (! instanceFile.exists()){
							try{
								Thread.sleep(1000);
							}
							catch (InterruptedException ie){
								// Oh, well.
							}
						}
						try{
							Runtime.getRuntime().exec(monitorCommand);
						}
						catch (IOException ioe){
							if (logger.isFinestEnabled()){
								logger.finest("An IOException occurred while trying to call MonitorWorkflow:  " + ioe.getMessage());
							}
							JOptionPane.showMessageDialog(IDEAInputGUI.this,
																						"The monitor could not be started because of an I/O error.\nThis may be a transient problem.",
																						"I/O Error Starting Monitor",
																						JOptionPane.ERROR_MESSAGE);
						}
						break;
					case SUCCEEDED:
						dispose();
						new IDEAOutputGUI(outputDirName);
						break;
					case FAILED:
						String logFilename = getInstanceFilename() + ".log";  // First try to read the Workflow log file.
						File logFile = new File(logFilename);
						if (! (logFile.exists() && logFile.canRead() && logFile.length() > 0L)){
							// If that fails, try the IDEA log file.
							logFilename = loggingProperties.getProperty("log4j.appender.FILE.File");
							logFile = new File(logFilename);
							if (! (logFile.exists() && logFile.canRead() && logFile.length() > 0L)){
								JOptionPane.showMessageDialog(IDEAInputGUI.this,
																							"The log file did not exist or was unreadable or empty.",
																							"No Log Available",
																							JOptionPane.WARNING_MESSAGE);
								break;
							}
						}
						// Show the log file.
						JDialog logDialog = new JDialog(IDEAInputGUI.this, logFilename, false);
						logDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
						FileParser fp = null;
						JTextArea log = new JTextArea();
						int lines = 0;
						try{
							fp = new FileParser(logFilename);
							while (true){
								log.append(fp.nextLine());
								log.append("\n");
								lines++;
							}
						}
						catch (EOFException eofe){
							// This exception is expected.
						}
						catch (FileNotFoundException fnfe){
							// Normally, this catch clause should not be reached because previous tests will have failed.
							JOptionPane.showMessageDialog(IDEAInputGUI.this,
																						"The log file did not exist or was unreadable.",
																						"No Log Available",
																						JOptionPane.WARNING_MESSAGE);
							break;
						}
						catch (IOException fnfe){
							JOptionPane.showMessageDialog(IDEAInputGUI.this,
																						"There was some problem reading " + fp.filename + ".",
																						"I/O Error Reading Log",
																						JOptionPane.ERROR_MESSAGE);
							break;
						}
						finally{
							if (fp != null){
								fp.closeFile();
							}
						}
						log.setFont(new Font("Monospaced", Font.BOLD, 10));
						JScrollPane logScrollPane = new JScrollPane(log);
						logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
						logScrollPane.setPreferredSize(new Dimension(IDEAConstants.SCREEN_SIZE.width * 2 / 3,
																												 IDEAConstants.SCREEN_SIZE.height * 3 / 4));
						logDialog.add(logScrollPane);
						logDialog.pack();
						logDialog.setLocationRelativeTo(null);  // Center the dialog box on the screen.
						logDialog.setVisible(true);
						break;
					}  // end of switch statement
				}
			});
		backButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner(); // Record all parameter changes.
					setVisible(false);
					showFirstPage();
				}
			});
		buttonPanel.add(startButton);
		buttonPanel.add(warningLabel);
		buttonPanel.add(backButton);
		buttonPanel.add(rejectionLabel);
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonPanel.setMaximumSize(new Dimension(buttonPanel.getMaximumSize().width, buttonPanel.getPreferredSize().height));
		rv.add(buttonPanel);
		return rv;
	}

	/**
	 * The <code>recordInstanceFilename</code> method calls <code>startIDEA</code> and sets the
	 * instance filename to the instance filename returned by <code>startIDEA</code>.
	 */
	private void recordInstanceFilename() throws IOException{
		instanceFilename = startIDEA();
	}

	/**
	 * The <code>getInstanceFilename</code> method returns the member variable.
	 * 
	 * @return the name of the workflow instance file
	 */
	private String getInstanceFilename(){
		return instanceFilename;
	}

	/**
	 * The <code>setSaveException</code> method records the exception thrown by the engine-running thread.
	 *
	 * @param ioe the exception thrown by the engine-running thread
	 *
	 */
	private void setSaveException(IOException ioe){
		saveException = ioe;
	}

	/**
	 * The <code>setIDEAStatus</code> method is called by the engine-running thread when IDEA starts or
	 * finishes (whether it succeeds or fails).  It should not be called from the event-dispatching thread.
	 *
	 * @param status IDEA's status
	 */
	void setIDEAStatus(Status status){
		ideaStatus = status;
		// The display must be redrawn with the new button text.
		try{
			SwingUtilities.invokeAndWait(new Thread(){
					public void run(){
						showSecondPage();
					}
				});
		}
		catch (InterruptedException ie){
			// N/A
		}
		catch (InvocationTargetException ite){
			// This indicates that an exception occurred while executing showSecondPage() on the event-dispatching thread.
			// Since that method throws no checked exceptions and, in the case of a runtime exception, the page may not be
			// available as an owner, just let ExceptionHandler handle this exception if it ever occurs.
			ExceptionHandler.handleException(ite);
		}
		if (saveException != null){
			SwingUtilities.invokeLater(new Thread(){
					public void run(){
						JOptionPane.showMessageDialog(IDEAInputGUI.this,
																					"The following error occurred during program execution:\n"
																					+ saveException.getMessage(),
																					"Error Executing Program",
																					JOptionPane.ERROR_MESSAGE);
						setSaveException(null);
					}
				});
		}
	}

	/**
	 * The <code>startIDEA</code> method prepares the files necessary for the analysis process and then starts it.
	 *
	 * @return the name of the instance file for the workflow
	 */
	private String startIDEA() throws IOException{
		String instanceFilename = prepareWorkflow();
		if (engineRunningThread != null){
			engineRunningThread.start();
			if (logger.isInfoEnabled()){
				logger.info("Analysis has begun.");
			}
		}
		return instanceFilename;
	}

	/**
	 * The <code>prepareWorkflow</code> method creates files necessary to run Workflow, which runs the underlying
	 * analysis scripts.  These files are created by fleshing out templates with user-specified customizations.
	 *
	 * @return the name of the instance file for the workflow
	 *
	 * @throws IOException if an I/O error occurs
	 */
	protected String prepareWorkflow() throws IOException{
		if (logger.isFinerEnabled()){
			logger.finer("Preparing workflow...");
		}
		// 1. Create the output directory and workflow subdirectory if necessary.
		outputDirName = config.additionalParameters().getParameter("Output directory").valueString();
		if (logger.isFinestEnabled()){
			logger.finest("Given outputDirName = [" + outputDirName + "]!");
		}
		String ideaMode = config.additionalParameters.getParameter("IDEA mode").valueString();
		if (ideaMode.equals("Single Dataset")){
			String outfile = config.pamlParameters().getParameter("outfile").valueString();
			if (outfile.startsWith("/")){
				outputDirName = outfile.substring(0, outfile.lastIndexOf("/"));
			}
			else{
				// If no output directory is supplied or implied by outfile's path,
				// use the directory from which IDEA was invoked.
				outputDirName = invocationDir;
			}
		}
		if (! outputDirName.endsWith("/")){
			outputDirName = outputDirName + "/";
		}
		if (ideaMode.equals("Multi-Dataset")){
			File outputDirectory = new File(outputDirName);
			String inputDirName = config.additionalParameters().getParameter("Input directory").valueString();
			if (inputDirName.equals("")){
				inputDirName = invocationDir;
			}
			File inputDirectory = new File(inputDirName);
			String[] proceedOrCancel = {"Proceed", "Cancel"};
			if (inputDirectory.equals(outputDirectory)){
				// In multi-dataset mode, warn the user if the output directory is the same as the input directory.
				int choice = JOptionPane.showOptionDialog(this,
																									"Your output directory is the same as your input directory.  This is not recommended.\nPrograms run by IDEA may overwrite files in your input directory.  Proceed?",
																									"Identical Input and Output Directories Not Recommended",
																									JOptionPane.YES_NO_OPTION,
																									JOptionPane.WARNING_MESSAGE,
																									null,  // Use the default icon.
																									proceedOrCancel,
																									proceedOrCancel[1]);
				if (choice == JOptionPane.NO_OPTION){
					return null;
				}
			}
			else{
				String[] filesInOutputDirectory = outputDirectory.list();
				if ((filesInOutputDirectory != null) && (filesInOutputDirectory.length > 0)){
					// In multi-dataset mode, warn the user if the output directory is not empty.
					int choice = JOptionPane.showOptionDialog(this,
																										"The output directory " + outputDirName + " is not empty.\nAny existing IDEA results in this directory will be overwritten.\nPrograms run by IDEA may also overwrite other files in this directory.  Proceed at your own risk.",
																										"Files in Output Directory May Be Overwritten",
																										JOptionPane.YES_NO_OPTION,
																										JOptionPane.WARNING_MESSAGE,
																										null,  // Use the default icon.
																										proceedOrCancel,
																										proceedOrCancel[1]);
					if (choice == JOptionPane.NO_OPTION){
						return null;
					}
				}
			}
		}					
																	 
		String workflowDirName = outputDirName + "workflow/";
		File workflowDir = new File(workflowDirName);
		if (workflowDir.isDirectory()){  // The user has already been warned, so delete the old workflow directory.
			FileUtils.deleteDirectory(workflowDir);  // This throws an IOException if the deletion is not successful.
		}
		boolean createdDirectory = false;
		createdDirectory = workflowDir.mkdirs();
		if (! createdDirectory){
			throw new FileNotFoundException("The directory "
																			+ workflowDirName
																			+ " did not exist and could not be created.");
		}
		if (logger.isInfoEnabled()){
			logger.info("Finished step 1!");
		}

		// 2. Copy the dataset name list to the output directory.
		String datasetNameList = config.additionalParameters.getParameter("Dataset name list").valueString();
		if (! datasetNameList.equals("")){
			File originalDatasetList = new File(datasetNameList);		
			if (originalDatasetList.length() == 0L){
				JOptionPane.showMessageDialog(this,
																			"The dataset list " + datasetNameList + " is empty.",
																			"Empty Dataset List",
																			JOptionPane.ERROR_MESSAGE);
				if (createdDirectory){
					workflowDir.delete();  // It's not a big deal if this doesn't succeed.
				}
				return null;
			}
			File newDatasetList = new File(outputDirName + originalDatasetList.getName());
			if (! newDatasetList.equals(originalDatasetList)){ // Overwriting a file with itself destroys it, so don't do that.
				FileClerk.copyToDir(datasetNameList, outputDirName);
			}
		}
		if (logger.isInfoEnabled()){
			logger.info("Finished step 2!");
		}

		// 3. Save the control file to the output directory.
		config.save(outputDirName
								+ "/"
								+ config.additionalParameters.getParameter("PAML program").valueString()
								+ ".ctl.template");
		if (logger.isInfoEnabled()){
			logger.info("Finished step 3!");
		}

		// 4. Copy the top-level and subflow template files to the workflow subdirectory.
		FileClerk.copyFile(IDEA_DIR + "idea"
											 + (config.getParameter("Use grid").valueString().equals("Distribute Jobs on Grid") ? "" : "_local") + "_template.xml",
											 workflowDirName + "idea_template.xml");
		if (config.additionalParameters.getParameter("Create tree").valueString().startsWith("Create tree")){
			FileClerk.copyFile(IDEA_DIR + "idea"
												 + (config.getParameter("Use grid").valueString().equals("Distribute Jobs on Grid") ? "" : "_local")
												 + "_gene_template.xml", workflowDirName + "idea_gene_template.xml");
		}
		else{  // use user's tree
			FileClerk.copyFile(IDEA_DIR + "idea"
												 + (config.getParameter("Use grid").valueString().equals("Distribute Jobs on Grid") ? "" : "_local")
												 + "_gene_template_user_tree.xml", workflowDirName + "idea_gene_template.xml");
		}
		if (logger.isInfoEnabled()){
			logger.info("Finished step 4!");
		}

		// Steps 5 and 6 are in createINIFiles.
		if (logger.isFinestEnabled()){
			logger.finest("Creating INI files in workflow directory " + workflowDirName
										+ " in output directory " + outputDirName + "!");
		}
		createINIFiles(datasetNameList, outputDirName, workflowDirName);

		if (logger.isInfoEnabled()){
			logger.info("Finished preparing workflow!");
		}
		final String instanceFilename = workflowDirName + "/idea.xml";
		final String[] workflowArgs = {"-t", workflowDirName + "idea_template.xml",
																	 "-c", workflowDirName + "idea_config.ini",
																	 "-i", instanceFilename,
																	 "--delaybuild=false --delete-old"};
		final StringBuffer alternateCommand = new StringBuffer(System.getProperty("wf.root"));
		alternateCommand.append("/RunWorkflow");
		for (int i = 0; i < workflowArgs.length; i++){
			alternateCommand.append(" ").append(workflowArgs[i]);
		}
		String notificationAddress = config.additionalParameters.getParameter("Email address (optional)").valueString();
		if (! notificationAddress.equals("")){
			alternateCommand.append(" --notify=").append(notificationAddress);
		}

		// For now, alternate dispatcher look-up files are not supported by Workflow.  Change the instance file instead.
// 		if (config.getParameter("Use grid").valueString().startsWith("Run")){
// 			String localDispatcherDirectory =
// 				System.getProperty("wf.root") + "/properties/local_dispatcher_factory_lookup.prop";
// 			if (! new File(localDispatcherDirectory).exists()){
// 				JOptionPane.showMessageDialog(this,
// 																			"The configuration file " + localDispatcherDirectory + " was not found.",
// 																			"Configuration File Necessary for Local Execution Not Found",
// 																			JOptionPane.ERROR_MESSAGE);
// 				if (createdDirectory){
// 					workflowDir.delete();  // It's not a big deal if this doesn't succeed.
// 				}
// 				return null;
// 			}
// 			alternateCommand.append(" --dist-lookup=").append(localDispatcherDirectory);
// 		}
		if (logger.isInfoEnabled()){
			logger.info("Running command: [" + alternateCommand + "]");
		}
		engineRunningThread = new Thread(){
				public void run(){
					try{
						boolean processFinished = false;
						while (! processFinished){
							try{
								setIDEAStatus(Status.RUNNING);
								Runtime.getRuntime().exec(alternateCommand.toString()).waitFor();
								processFinished = true;
							}
							catch (InterruptedException ie){
								// Just try again.
							}
							catch (IOException ioe){
								setSaveException(ioe);
								setIDEAStatus(Status.FAILED);
								return;
							}
						}
						try{
							FileParser instanceFileParser = new FileParser(instanceFilename);
							while (true){
								String nextLine = instanceFileParser.nextLine();
								int openTagIndex = nextLine.indexOf("<state>");
								if (openTagIndex != -1){
									int closeTagIndex = nextLine.indexOf("</state>");
									if (closeTagIndex != -1){
										String state = nextLine.substring(openTagIndex + 7, closeTagIndex);
										setIDEAStatus(state.equals("complete") ? Status.SUCCEEDED : Status.FAILED);
										break;
									}
								}
							}
						}
						catch (EOFException eofe){
							// This exception is expected.
							setIDEAStatus(Status.FAILED);
						}
						catch (FileNotFoundException fnfe){
							setIDEAStatus(Status.FAILED);
						}
						System.out.println("Finished running IDEA!");
						if (logger.isInfoEnabled()){
							logger.info("Finished running IDEA!");
						}
					}
					catch (IOException ioe){
						setSaveException(ioe);
						setIDEAStatus(Status.FAILED);
					}
				}
			};
		return instanceFilename;
	}

	/**
	 * This subroutine for <code>prepareWorkflow</code> creates the INI files necessary for running Workflow.
	 * In addition to the top-level INI file, a subflow INI files is created for each dataset.
	 *
	 * @param datasetNameList the name of the file listing datasets
	 * @param outputDir the name of the output directory
	 * @param workflowDir the name of the workflow subdirectory under the output directory
	 *
	 */
	private void createINIFiles(String datasetNameList, String outputDir, String workflowDir) throws IOException{
		String inputDir = config.additionalParameters().getParameter("Input directory").valueString();
		if (inputDir.equals("")){
			inputDir = invocationDir;
		}
		LinkedList<String> datasets = new LinkedList<String>();
		LinkedList<String> treeFiles = new LinkedList<String>();
		if (datasetNameList.equals("")){  // single-dataset mode
			datasets.add("ONLY");
			String treeFilename = config.pamlParameters.getParameter("treefile").valueString();
			if (! treeFilename.startsWith("/")){
				treeFilename =
					((config.additionalParameters().getParameter("Create tree").valueString().startsWith("Create tree"))
					 ? outputDir
					 : inputDir)
					+ "/"
					+ treeFilename;
			}
			treeFiles.add(treeFilename);
		}
		else{  // multi-dataset mode
			FileParser datasetListParser = new FileParser(datasetNameList);
			try{
				while (true){
					String nextLine = datasetListParser.nextLine();
					if (nextLine.length() == 0){
						continue;
					}
					String[] nextLineSubparts = nextLine.split("\\s");
					if (nextLineSubparts.length == 1){
						datasets.add(nextLineSubparts[0]);
					}
					else if (nextLineSubparts.length == 2){
						datasets.add(nextLineSubparts[0]);
						treeFiles.add(nextLineSubparts[1]);
					}
					else{
						throw new IOException("Improperly formatted line:  " + nextLine);
					}
				}
			}
			catch (EOFException eofe){
				// This exception is expected; no action is taken.
			}
		}

		// 5. Fill in the top-level INI skeleton with datasets from the list and save the resulting INI file to the
		// workflow directory.
		String newTopLevelINIFilename = workflowDir + "/idea_config.ini";
		PrintStream iniWriter = new PrintStream(new BufferedOutputStream(new FileOutputStream(newTopLevelINIFilename)));
		String skeletonFilename = IDEA_DIR + "/idea_config_skeleton.ini";
		FileParser skeletonParser = new FileParser(skeletonFilename);
		try{
			String subflowString = "subflow_";
			while (true){
				String nextLine = skeletonParser.nextLine();
				String revisedLine = nextLine;
				if (nextLine.startsWith("fileName")){
					StringBuffer revisedLineBuffer = new StringBuffer("fileName=");
					for (String dataset : datasets){
						dataset = dataset.replace(File.separatorChar, '_');
						revisedLineBuffer.append(workflowDir).append("idea_").append(subflowString).append(dataset).append(".xml,");
					}
					revisedLine = revisedLineBuffer.toString();
					revisedLine = revisedLine.substring(0, revisedLine.length() - 1);
				}
				else if (nextLine.startsWith("config.param.configfile")){
					StringBuffer revisedLineBuffer = new StringBuffer("config.param.configfile=");
					for (String dataset : datasets){
						dataset = dataset.replace(File.separatorChar, '_');
						revisedLineBuffer.append(workflowDir).append(subflowString).append(dataset).append(".ini,");
					}
					revisedLine = revisedLineBuffer.toString();
					revisedLine = revisedLine.substring(0, revisedLine.length() - 1);
				}
				else if (nextLine.startsWith("config.param.template")){
					revisedLine = "config.param.template=" + workflowDir + nextLine.substring(nextLine.indexOf('=') + 1);
				}
				iniWriter.println(revisedLine);
			}
		}
		catch (EOFException eofe){
			// This exception is expected; no action is taken.
		}
		iniWriter.close();
		if (logger.isInfoEnabled()){
			logger.info("Finished step 5!");
		}

		// 6. Create a subflow INI file for each dataset.
		ListIterator<String> treeFileIterator = treeFiles.listIterator();
		String dateString = "." + new java.sql.Date(System.currentTimeMillis());
		for (String dataset : datasets){
			String underscoredDataset = dataset.replace(File.separatorChar, '_');
			String strippedDataset = new File(dataset).getName();
			String treeFile = null;
			try{
				treeFile = treeFileIterator.next();
			}
			catch (NoSuchElementException nsee){
				// Leave it as null.
			}
			String subflowINIFilename = workflowDir + "subflow_" + underscoredDataset + ".ini";
			iniWriter = new PrintStream(new BufferedOutputStream(new FileOutputStream(subflowINIFilename)));
			iniWriter.println("[1]\n");
			String[] configMapIDs = null;
			if (config.additionalParameters.getParameter("Create tree").valueString().startsWith("Create tree")){
				configMapIDs = new String[4];
				configMapIDs[0] = "1.1";
				configMapIDs[1] = "1.2";
				configMapIDs[2] = "1.3";
				configMapIDs[3] = "1.4";
			}
			else{  // use user's tree; skip tree creation
				configMapIDs = new String[3];
				configMapIDs[0] = "1.2";
				configMapIDs[1] = "1.3";
				configMapIDs[2] = "1.4";
			}
			// Set flags for steps A, B, C and D.
			for (int i = 0; i < configMapIDs.length; i++){
				iniWriter.println("[" + configMapIDs[i] + "]");
				if (configMapIDs[i].equals("1.3")){  // idea-C-merge-runs.pl
					iniWriter.println("param.stdout=" + outputDir + "idea.merge-runs." + strippedDataset + ".out" + dateString);
					iniWriter.println("param.stderr=" + outputDir + "idea.merge-runs." + strippedDataset + ".err" + dateString);
					iniWriter.println("arg=" + outputDir);
					if (dataset.equals("ONLY")){  // single-dataset mode
						iniWriter.println("arg=--outfile ");
						iniWriter.println("arg=" + config.pamlParameters.getParameter("outfile").valueString());
					}
					else{  // multi-dataset mode
						iniWriter.println("arg=-d");
						iniWriter.println("arg=" + strippedDataset);
					}
 				}
				else if (configMapIDs[i].equals("1.4")){  // idea-D-parse-output.pl
					iniWriter.println("param.stdout=" + outputDir + "idea.parse-output." + strippedDataset + ".out" + dateString);
					iniWriter.println("param.stderr=" + outputDir + "idea.parse-output." + strippedDataset + ".err" + dateString);
					String fullPathToOutfile = config.pamlParameters.getParameter("outfile").valueString();
					if (dataset.equals("ONLY")){  // single-dataset mode
						if (! new File(fullPathToOutfile).isAbsolute()){
							fullPathToOutfile = outputDir + fullPathToOutfile;
						}
						iniWriter.println("arg=" + fullPathToOutfile + ".merged");
						iniWriter.println("arg=" + fullPathToOutfile + ".summary");
						iniWriter.println("arg=" + fullPathToOutfile + ".lrt");
					}
					else{  // multi-dataset mode
						iniWriter.println("arg=" + outputDir + strippedDataset + ".mlc");
						iniWriter.println("arg=" + outputDir + strippedDataset + ".summary");
						iniWriter.println("arg=" + outputDir + strippedDataset + ".lrt");
					}
				}
				else{  // idea-A-create-tree.pl or idea-B-run-paml.pl
					if (configMapIDs[i].equals("1.1")){  // idea-A-create-tree.pl
						iniWriter.println("param.stdout=" + outputDir + "idea.create-tree." + strippedDataset + ".out" + dateString);
						iniWriter.println("param.stderr=" + outputDir + "idea.create-tree." + strippedDataset + ".err" + dateString);
						if (config.additionalParameters.getParameter("Create tree").valueString().equals("Create tree with PhyML")){
							iniWriter.println("flag=--phyml");
						}
					}
					iniWriter.println("flag=-t " + outputDir);
					String inputDirForDashI = inputDir + (dataset.contains(File.separator) ? (File.separator + new File(dataset).getParent()) : "");
					iniWriter.println("flag=-i " + inputDirForDashI);
					iniWriter.println("flag=-o " + outputDir);
					if (dataset.equals("ONLY")){  // single-dataset mode
						iniWriter.println("flag=--seqfile " + config.pamlParameters.getParameter("seqfile").valueString());
						if (configMapIDs[i].equals("1.1")){  // idea-A-create-tree.pl
							iniWriter.println("flag=--treefile " + treeFile);
						}
					}
					else{  // multi-dataset mode
						iniWriter.println("flag=-p " + strippedDataset);
					}
					if (configMapIDs[i].equals("1.2")){  // idea-B-run-paml.pl 
						iniWriter.println("param.stdout=" + outputDir + "idea.run-paml." + strippedDataset + ".out" + dateString);
						iniWriter.println("param.stderr=" + outputDir + "idea.run-paml." + strippedDataset + ".err" + dateString);
						String program = config.additionalParameters().getParameter("PAML program").valueString();
						if (program.equals("codeml")){
							// The PAML step requires the list of omega values as a parameter.
							StringBuffer omegaBuffer =
								new StringBuffer(config.pamlParameters().getParameter("omega").valueString());
							String[] additionalOmegas =
								config.additionalParameters().getParameter("Extra omega values").valueString().split("\\s");
							for (String additionalOmega : additionalOmegas){
								omegaBuffer.append(",").append(additionalOmega);
							}
							iniWriter.println("arg=-w");
							iniWriter.println("arg=\"" + omegaBuffer + "\"");
						}
						else{
							iniWriter.println("flag=-w STANDARD");
							iniWriter.println("flag=-a " + program);
						}
						if (config.additionalParameters.getParameter("Create tree").value.equals(Boolean.FALSE)){  // use user's tree
							String userTreeFilename = treeFile;
							if (! dataset.equals("ONLY")){  // multi-dataset mode
								if (userTreeFilename == null){  // name of tree file not given in dataset name list
									userTreeFilename = dataset + ".PAMLtree";
								}
								userTreeFilename = new File(userTreeFilename).isAbsolute() ? userTreeFilename : (inputDir + File.separator + userTreeFilename);
							}
							iniWriter.println("flag=-u " + userTreeFilename);
						}
					}
				}
				iniWriter.println("dceSpec.os=Linux");
				iniWriter.println("\n");
			}
			iniWriter.close();
		}
		if (logger.isInfoEnabled()){
			logger.info("Finished step 6!");
		}
	}

	/**
	 * NOTE:  The command-line option interface is currently unadvertised and unsupported.
	 * It is retained in the code in case it is useful for developing the upcoming 
	 * text-based interface.  Currently, users are expected to invoke "idea" with no arguments.
	 *
	 * The <code>validateOptions</code> method validates the options passed in the
	 * options hashtable. Although this method has public visibility it should not be
	 * used directly. The method has been exposed only for unit testing.
	 * 
	 * @param options a <code>HashMap</code> containing all the options passed as
	 * parameters
	 */
	public static void validateOptions(Map options)
		throws IllegalArgumentException {

		String valueString = null;

		// If the help message is requested, print it and exit.
		if (options.containsKey("help")){
			System.out.println(classDescription);
			System.out.println(usage);
			System.exit(0);
		}
		// See if a configuration file is specified. If so, make sure it is valid.
		if (options.containsKey("c")) {
			valueString = (String) options.get("c");
			if ((valueString == null) || (valueString.length() == 0)) {
				throw new IllegalArgumentException(
																					 "No configuration file specified with -c "
																					 + "command line parameter.");
			}
			if (! new File(valueString).exists()){
				throw new IllegalArgumentException("The configuration file specified, " + valueString + ", does not exist.");
			}
		}
		valueString = null;

		// See if a logger configuration file is specified. If so, make sure it is valid.
		if (options.containsKey("logconf")) {
			valueString = (String) options.get("logconf");
			if ((valueString == null) || (valueString.length() == 0)) {
				throw new IllegalArgumentException(
																					 "No logger configuration file specified with -logconf "
																					 + "command line parameter.");
			}
		}
		valueString = null;

		// See if a log file is specified. If so, make sure it is valid.
		if (options.containsKey("l")) {
			valueString = (String) options.get("l");
			if ((valueString == null) || (valueString.length() == 0)) {
				throw new IllegalArgumentException(
																					 "No log file specified with -l "
																					 + "command line parameter.");
			}
		}
		valueString = null;

		// See if the log level has been specified, if so make sure that it is a valid number
		if (options.containsKey("v")) {
			valueString = (String) options.get("v");
			if ((valueString == null) || (valueString.length() == 0)) {
				throw new IllegalArgumentException(
																					 "No log level specified with -v "
																					 + "command line parameter.");
			}
			try {
				Integer.parseInt(valueString);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException(
																					 "Log level specified with -v command line parameter '"
																					 + valueString
																					 + "' cannot be converted to a number.");
			}
		}
	}

	/**
	 * This is the entry point to the Java portion of IDEA.  Code for processing command-line options
	 * is retained, but they aren't currently supported.
	 *
	 * @param args the command-line arguments with which this class was invoked
	 */
	public static void main (String[] args) {
		Logger.initializeLogging();
		// The logger should already have been created.

		// Make a copy of the argument list for loggin purposes; the original is clobbered when getting options.
		String [] argsCopy = (String []) args.clone();
        
		try {            
			// This hash map holds the command-line parameters.
			final HashMap options = new HashMap();
			try {

				args = ExtractArguments.getOptions(args, options);

				validateOptions(options);

			}
			catch (InvalidArgumentException e) {
				System.out.println(
													 "Invalid options specified.\n" + e.getMessage());
				System.out.println(usage);
				System.exit(1);
			}
			catch (IllegalArgumentException e) {
				System.out.println(
													 "Illegal options specified.\n" + e.getMessage());
				System.out.println(usage);
				System.exit(1);
			} // end of try-catch
            
			// If a log level has been specified, then set the log level.
			int logLevel = -1;
			if (options.containsKey("v")) {
				logLevel = Integer.parseInt((String) options.get("v"));
			}
            
			// If a log file has been specified, then set the log file name.
			String logFile = null;
			if (options.containsKey("l")) {
				logFile = (String) options.get("l");
            
				// Set the exception handling mode
				ExceptionHandler.setMode(ExceptionHandler.QUIET_MODE);
			}
			if (logFile != null || logLevel >= 0) {
				// Configure the logger if a log file or level is specified.
				if (logFile != null && logLevel < 0) {
					logLevel = Logger.INFO;
				}
				Logger.initializeLogging(logFile, logLevel);    
			}
      
			if (logger.isDebugEnabled()){
				logger.debug("Parameters: " + StringUtils.toString(argsCopy));
			}
            
			final String datasetNameListFilename = (args.length > 2) ? args[1] : "";

			// The meat of the application takes place inside this call to invokeLater because it's important for it
			// to run on the event dispatch thread.  Otherwise, thread interference could cause unpredictable runtime
			// exceptions.
			SwingUtilities.invokeLater(new Thread(){
					public void run(){
						try{
							String controlFilename = DEFAULT_CONTROL_FILENAMES.getFirst();

							// If a configuration file has been specified, use it.
							if (options.containsKey("c")) {
								controlFilename = (String) options.get("c");
							}	
        
							// Set non-standard default fonts for various components.
							UIManager.put("Button.font", new Font("Dialog", Font.BOLD, 10));
							UIManager.put("ComboBox.font", new Font("Dialog", Font.BOLD, 10));
							UIManager.put("Label.font", new Font("Dialog", Font.BOLD, 10));
							UIManager.put("List.font", new Font("Dialog", Font.BOLD, 10));
							UIManager.put("MenuItem.font", new Font("Dialog", Font.BOLD, 10));
							UIManager.put("Table.font", new Font("DialogInput", Font.PLAIN, 10));
							UIManager.put("TextField.font", new Font("DialogInput", Font.PLAIN, 10));

							// Tool tips may be long explanations, so set them to stay visible for a minute
							// or until the user moves the mouse.  Also set them to come up immediately.
							ToolTipManager ttm = ToolTipManager.sharedInstance();
							ttm.setDismissDelay(60000);
							ttm.setInitialDelay(0);
							ExceptionHandler.setMode(ExceptionHandler.INTERACTIVE_MODE);
							new IDEAInputGUI(datasetNameListFilename, controlFilename);
						}
						catch (Throwable e) {
							System.out.println(e.getClass().getName() + ":" + e.getMessage());
							e.printStackTrace();
							ExceptionHandler.handleException(e);
							System.exit(1);
						}
					}
				});
		}
		catch (Throwable e) {
			System.out.println(e.getClass().getName() + ":" + e.getMessage());
			e.printStackTrace();
			ExceptionHandler.handleException(e);
			System.exit(1);
		}
	} // end of main

}

