package edu.umaryland.igs.idea;

// The XStream library is used to perform XML serialization of configurations (saving in IDEA format).  It works via
// the same mechanisms that Java's object serialization works, but objects aren't required to implement Serializable.
// XStream allows private fields and anonymous classes to be converted to and from XML.
import com.thoughtworks.xstream.XStream;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.Properties;

import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import edu.umaryland.igs.aegan.utils.WrapperPane;
import org.tigr.antware.shared.exceptions.InvalidFileFormatException;

/**
 * <code>IDEAConfiguration</code> encapsulates all the configuration for a
 * particular run of IDEA, including the parameter sets.  Configurations may be loaded
 * and saved either in PAML's .ctl format (which preserves only the PAML parameters) or in
 * the alterate .idea format (which preserves all settings).
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
public class IDEAConfiguration implements Cloneable, Serializable{

	static final long serialVersionUID = 8238397299629302502L;

	// This is the current version of IDEA.  It is incremented by one minor revision (by updating this source file)
	// when a new revision is installed.  Contrast this with the instance field <code>ideaVersion</code> below.
	public static final String IDEA_VERSION = "2.5.1";

	// Although PAML's .ctl file contains only one value for omega (dN/dS, the ratio of nonsynonymous substitution rate to
	// synonymous substitution rate), IDEA by default runs two runs of PAML with different omega values when there are
	// three or more species.  The additional omega value suggested will be whichever of these two defaults is further
	// from the supplied omega value.
	private static final double[] DEFAULT_OMEGA_VALUES = {.1, 1.5};

	// Configurations may be saved in either PAML mode or IDEA mode.  Each of these modes has its own file extension and
	// user-friendly file-format description.
	static final int PAML_MODE = 1;
	static final int IDEA_MODE = 0;
	static final String[] FILE_EXTENSIONS = {".ctl"};
	static final String[] FILE_FORMATS = {"PAML Configuration"};  // IDEA-format configurations are disabled.
	static final boolean PHYML_FOUND = (System.getProperty("PHYML") != null) && (System.getProperty("PHYML").length() > 0);
	static final boolean PHYLIP_FOUND = (System.getProperty("PHYLIP") != null);

	protected static final String[] REQUIRED_PAML_PARAMETERS = {"outfile", "seqfile", "treefile"};
	protected static final String[] REQUIRED_PARAMETERS = {"Create tree", "Create tree (single-dataset mode)",
																												 "Dataset name list", "IDEA mode",
																												 "Input directory", "outfile",
																												 "Output directory", "PAML program",
																												 "seqfile", "treefile"};

	protected PAMLParameterSet pamlParameters;  // all parameters read from the .ctl file
	protected ParameterSet additionalParameters;  // all other parameters; default null

	// The parameter sets below are visual groupings of parameters in the first two sets.
	protected ParameterSet followUpPageParameters;
	protected ParameterSet firstPageTopParameters;
	protected ParameterSet multipleDatasetParameters;
	protected ParameterSet singleDatasetParameters;
	protected ParameterSet firstPageMiddleParameters;
	protected ParameterSet firstPageBottomParameters;
	protected ParameterSet dataParameters;
	protected ParameterSet modelParameters;
	protected ParameterSet otherParameters;

	// This is a set of parameters not to be displayed; it is composed of parameters in the first two sets.
	// Currently, no parameters are omitted.
	protected ParameterSet omittedParameters;

	// The default running mode is multi-dataset; there is also a single-dataset mode.
	protected String runMode = "Multi-Dataset";

	// This is the filename associated with this configuration.
	protected String filename;

	// The IDEA version for this particular IDEA configuration is possibly older than the current version
	// because it may have been loaded as a serialized object from a file.  Keeping track of this may help
	// ensure backward compatibility with old configuration files as IDEA evolves.
	protected String ideaVersion = IDEA_VERSION;

	/**
	 * This constructs a <code>IDEAConfiguration</code> from the specified parameter sets
	 * and then creates the visual groupings.
	 *
	 * @param pamlParams the set of all parameter read from the .ctl file
	 * @param aParams the set of all other parameters
	 */
	public IDEAConfiguration(PAMLParameterSet pamlParams, ParameterSet aParams){
		pamlParameters = pamlParams;
		additionalParameters = aParams;
		addInputVerifiers();
		createVisualGroupings();
	}

	/**
	 * This constructs a <code>IDEAConfiguration</code> from the specified file.
	 * The file can be in either .ctl or .idea format.
	 *
	 * @param filename the name of the file to load
	 * @throws InvalidFileFormatException if the file is malformatted
	 * @throws FileNotFoundException if the file does not exist, is a directory or cannot be opened
	 * @throws IOException
	 */
	public IDEAConfiguration(String filename) throws InvalidFileFormatException, FileNotFoundException, IOException{
		this.filename = filename;
		load(filename);
	}

	/**
	 * @deprecated
	 */
	protected Object clone(){
		PAMLParameterSet newPAMLParameters = (PAMLParameterSet) pamlParameters.clone();
		ParameterSet newAdditionalParameters = (ParameterSet) additionalParameters.clone();
		IDEAConfiguration newConfiguration = new IDEAConfiguration(newPAMLParameters, newAdditionalParameters);
		newConfiguration.setCloneConfig();
		return newConfiguration;
	}

	/**
	 * @deprecated
	 */
	void setCloneConfig(){
		pamlParameters.setCloneConfig(this);
		additionalParameters.setCloneConfig(this);
	}

	/**
	 * The <code>configurationFileFilter</code> method returns a file filter which accepts files of the specified
	 * format.  Directories are also accepted.
	 *
	 * @param fileFormat the only configuration-file format to accept when this filter is selected (PAML_MODE or IDEA_MODE)
	 * 
	 * @return a file filter which accepts only files of the specified type (in addition to directories)
	 */
	public static FileFilter configurationFileFilter(final int fileFormat){
		return new FileFilter(){
				public String getDescription(){
					return FILE_FORMATS[fileFormat] + " Files";
				}
				public boolean accept(File f){
					if (f.isDirectory()){
						return true;
					}
					return IDEAConfiguration.isValidConfig(f.getName(), fileFormat);
				}
			};
	}

	/**
	 * The <code>load</code> method reads the parameters for this configuration
	 * from the specified file.  The file can be in either .ctl or .idea format.
	 *
	 * @param filename the name of the file to load
	 * @throws InvalidFileFormatException if the file is malformatted
	 * @throws FileNotFoundException if the file does not exist, is a directory or cannot be opened
	 * @throws IOException
	 */
	public void load(String filename) throws InvalidFileFormatException, FileNotFoundException, IOException{
		this.filename = filename;
		if (isFullConfiguration()){ // We're loading an XML (.idea) file representing the full configuration.
			Object[] streamAndVersion = null;
			ObjectInputStream in = null;
			IDEAConfiguration loadedConfiguration = null;
			try{
				streamAndVersion = BackwardCompatibilityManager.backwardCompatibleStreamAndVersion(filename);
				XStream xstream = (XStream) streamAndVersion[0];
				in = xstream.createObjectInputStream(new FileReader(filename));
				loadedConfiguration = (IDEAConfiguration) in.readObject();
			}
			catch (ClassNotFoundException cnfe){
				throw new InvalidFileFormatException(filename + " does not contain an IDEA configuration.");
			}
			catch (ClassCastException cce){
				throw new InvalidFileFormatException(filename + " does not contain an IDEA configuration.");
			}
			catch (RuntimeException re){
				Package exceptionPackage = re.getClass().getPackage();
				if (exceptionPackage == null){
					throw re;
				}
				if (exceptionPackage.getName().startsWith("com.thoughtworks.xstream.")){
					if (streamAndVersion == null){  // An XStream initialization problem has occurred.
						IOException ioe = new IOException();
						ioe.initCause(re);
						throw ioe;
					}
					String version = (String) streamAndVersion[1];
					if (version.equals("unknown")){
						throw new InvalidFileFormatException("The configuration file " + filename + " lacks a version number and could not be loaded.  It may have been modified or corrupted.");
					}
					if (version.equals(IDEA_VERSION)){
						re.printStackTrace();
						throw new InvalidFileFormatException("IDEA could not load this configuration file.  If you have not edited the file, please report this problem.  Please include "
																								 + filename
																								 + " and the stack trace printed on the console in your report.");
					}
					else{
						throw new InvalidFileFormatException("IDEA-format configuration files saved with IDEA " + version
																								 + " cannot be loaded with IDEA " + IDEA_VERSION + ".");
					}
				}
				else{
					throw re;
				}
			}
			finally{
				if (in != null){
					in.close();
				}
			}
			pamlParameters = loadedConfiguration.pamlParameters;
			additionalParameters = loadedConfiguration.additionalParameters;
			this.filename = loadedConfiguration.filename;
			BackwardCompatibilityManager.deriveFromOldVersion(this, loadedConfiguration.ideaVersion);
			initializeActionListeners();
			addInputVerifiers();  // Input verifiers are NOT loaded from the saved configuration!
			setRunMode(loadedConfiguration.runMode);  // This creates the visual groupings.
		}
		else{ // We're loading a PAML-style control (.ctl) file representing the PAML parameters only.
			pamlParameters = new PAMLParameterSet(filename);
			boolean baseml = filename.contains("baseml") || filename.contains("BASEML")
				|| (getParameter("nparK") != null) || (getParameter("nhomo") != null);
			initializeAdditionalParameters(baseml);  // Set IDEA-specific parameters to initial values.
			addInputVerifiers();
			createVisualGroupings();
		}
		this.filename = filename;  // This shouldn't be necessary, but it shouldn't hurt.
	}

	/**
	 * The <code>save</code> method saves the parameters for this configuration
	 * to the specified file.  The file can be in either .ctl format or .idea format.
	 *
	 * @param filename the name of the file to load
	 * @throws FileNotFoundException if the file is a directory, cannot be created or cannot be opened
	 * @throws IOException if some other I/O error occurs
	 */
	public void save(String filename) throws FileNotFoundException, IOException{
		this.filename = filename;
		if (isFullConfiguration()){
			XStream xstream = new XStream();
			ObjectOutputStream out = xstream.createObjectOutputStream(new FileWriter(filename));
			out.writeObject(this);
			out.close();
		}
		else{
			pamlParameters.save(filename);
		}
	}

	/**
	 * The <code>setRunMode</code> method sets the running mode for this configuration.
	 *
	 * @param runMode the running mode
	 */
	public void setRunMode(String runMode){
		this.runMode = runMode;
		
		// The visual groupings need to be recreated because not all options are available in all modes.
		createVisualGroupings();
	}

	/**
	 * The <code>isFullConfiguration</code> method returns true if the running mode
	 * is IDEA_MODE.
	 *
	 * @return true in IDEA_MODE; false in PAML_MODE
	 */
	public boolean isFullConfiguration(){
		// (IDEA-format configuration) files disabled return (filename != null) && isValidConfig(filename, IDEA_MODE);
		return false;
	}

	/**
	 * This <code>isValidConfig</code> method returns true if the specified
	 * file name ends with the correct extension for the specified mode.
	 * 
	 * @param fn the filename to test for validity
	 * @param configurationMode the mode to assume for testing
	 * @return true if the filename has the correct extension for the mode
	 */
	public static boolean isValidConfig(String fn, int configurationMode){
		return fn.endsWith(FILE_EXTENSIONS[configurationMode]);
	}

	/**
	 * This <code>isValidConfig</code> method returns true if the specified
	 * file name ends with the correct extension for any running mode.
	 * 
	 * @param fn the filename to test for validity
	 * @return true if the filename has a valid extension for a configuration file
	 */
	public static boolean isValidConfig(String fn){
		for (int i = 0; i < FILE_FORMATS.length; i++){
			if (isValidConfig(fn, i)){
				return true;
			}
		}
		return false;
	}

	/**
	 * The <code>pamlParameters</code> method returns the set of all parameters
	 * read from the .ctl file.
	 * 
	 * @return the set of all parameters read from the .ctl file
	 */
	public ParameterSet pamlParameters(){
		return pamlParameters;
	}

	/**
	 * The <code>additionalParameters</code> method returns the set of all
	 * parameters not read from the .ctl file.
	 * 
	 * @return the set of all parameters not read from the .ctl file
	 */
	public ParameterSet additionalParameters(){
		return additionalParameters;
	}

	/**
	 * The <code>getParameter</code> method looks up the given name in pamlParameters
	 * or, if it is not found there, in additionalParameters.  If the parameter is found,
	 * it is returned; otherwise, null is returned.
	 *
	 * @param name the name of the parameter to look up
	 * @return the PAML parameter with the given name, or the IDEA parameter with the given name, or null
	 */
	public Parameter getParameter(String name){
		Parameter rv = pamlParameters.getParameter(name);
		return (rv == null) ? ((additionalParameters == null) ? null : additionalParameters.getParameter(name)) : rv;
	}

	/**
	 * The <code>initializeAdditionalParameters</code> method initializes
	 * the additional parameters; this depends on the PAML parameters.
	 *
	 * @param baseml true if the PAML program should be set to baseml
	 */
	protected void initializeAdditionalParameters(boolean baseml){
		requireParameters(REQUIRED_PAML_PARAMETERS);
		additionalParameters = new ParameterSet("Additional Parameters");

		Parameter omega = pamlParameters.getParameter("omega");
		if (omega != null){
			// Add the extra-omega-values parameter.  See note on DEFAULT_OMEGA_VALUES.
			String firstOmegaValueString = omega.valueString();
			ParameterExplanation additionalOmegaExplanation =
				new ParameterExplanation("PAML will be run once for each omega value specified,");
			additionalOmegaExplanation.addLine("including the first value above.");
			if (firstOmegaValueString != null){
				double firstOmegaValue = Double.parseDouble(firstOmegaValueString);  // To do:  The NFE should be handled.
				double minDistance = Math.abs(firstOmegaValue - DEFAULT_OMEGA_VALUES[0]);
				int closestMatch = 0;
				for (int i = 1; i < DEFAULT_OMEGA_VALUES.length; i++){
					double distance = Math.abs(firstOmegaValue - DEFAULT_OMEGA_VALUES[i]);
					if (distance < minDistance){
						minDistance = distance;
						closestMatch = i;
					}
				}
				double[] additionalOmegaValues = new double[DEFAULT_OMEGA_VALUES.length - 1];
				for (int i = 0; i < closestMatch; i++){
					additionalOmegaValues[i] = DEFAULT_OMEGA_VALUES[i];
				}
				for (int i = closestMatch + 1; i < DEFAULT_OMEGA_VALUES.length; i++){
					additionalOmegaValues[i - 1] = DEFAULT_OMEGA_VALUES[i];
				}
				additionalParameters.addParameter("Extra omega values", additionalOmegaValues, additionalOmegaExplanation);
			}
			else{
				additionalParameters.addParameter("Extra omega values", DEFAULT_OMEGA_VALUES, additionalOmegaExplanation);
			}
		}
		// Add the remaining parameters.
		ParameterExplanation createTreeExplanation = new ParameterExplanation("If you specify an existing tree, it must be in unrooted format.");
		createTreeExplanation.addLine("In multi-dataset mode, a tree file for each dataset may be specified");
		createTreeExplanation.addLine("as a second column in the dataset-name list file.");
		String[] createTreeChoices = {"Create tree with PhyML", "Create tree with PHYLIP", "Use existing tree in file"};
		MultipleChoiceParameter createTree = new MultipleChoiceParameter("Create tree",
																																		 PHYML_FOUND
																																		 ? "Create tree with PhyML"
																																		 : (PHYLIP_FOUND ? "Create tree with PHYLIP" : "Use existing tree in file"),
																																		 createTreeExplanation,
																																		 createTreeChoices,
																																		 true);
		additionalParameters.addParameter(createTree);
		MultipleChoiceParameter createTreeHorn = new MultipleChoiceParameter("Create tree (single-dataset mode)",
																																				 PHYML_FOUND
																																				 ? "Create tree with PhyML"
																																				 : (PHYLIP_FOUND
																																						? "Create tree with PHYLIP"
																																						: "Use existing tree in file"),
																																				 createTreeExplanation,
																																				 createTreeChoices,
																																				 true);
		additionalParameters.addParameter(createTreeHorn);
		Parameter emailAddress = new Parameter("Email address (optional)",
																					 null,
																					 "Notifications of completion and/or errors will be sent to this address.");
		emailAddress.setMinTextAreaSize(19);
		additionalParameters.addParameter(emailAddress);
		final Parameter inputDirectory =
			new FileParameter("Input directory",
												IDEAInputGUI.invocationDir,
												"This defaults to the directory containing the list of dataset names.");
		additionalParameters.addParameter(inputDirectory);
		Parameter outputDirectory = new FileParameter("Output directory",
																									IDEAInputGUI.invocationDir,
																									"All output, including temporary output, will be written here."){
			};
		additionalParameters.addParameter(outputDirectory);
		Parameter datasetNameList = new FileParameter("Dataset name list",
																							 "",
																							 "This file should have one dataset name per line.");
		additionalParameters.addParameter(datasetNameList);
		ParameterExplanation programExplanation =
			new ParameterExplanation("Use codeml for codon- or amino-acid-based analysis");
		programExplanation.addLine("or baseml for nucleotide-based analysis.");
		Parameter programToRun = new BooleanParameter("PAML program",
																									baseml ? "baseml" : "codeml",
																									programExplanation,
																									"codeml",
																									"baseml");
		additionalParameters.addParameter(programToRun);
		ParameterExplanation modeExplanation = new ParameterExplanation("In multi-dataset mode, you must supply a file containing a list of dataset names.");
		modeExplanation.addLine("The input and output filenames for each dataset are based on its name in the list file.");
		BooleanParameter ideaMode = new BooleanParameter("IDEA mode",
																										 "Multi-Dataset",
																										 modeExplanation,
																										 "Multi-Dataset",
																										 "Single Dataset");
		additionalParameters.addParameter(ideaMode);
		setIDEAMode(ideaMode.valueString());
		initializeActionListeners();
		ParameterExplanation gridExplanation = new ParameterExplanation("Either SGE or Condor is required for grid submissions.");
		gridExplanation.addLine("SGE submissions will be disabled until SGE is initialized for IDEA, as described in the installation guide.");
		gridExplanation.addLine("Users with grid support may choose \"Run All Jobs Locally\" to bypass the grid.");
		BooleanParameter useGrid = new BooleanParameter("Use grid",
																										"Distribute Jobs on Grid",
																										gridExplanation,
																										"Distribute Jobs on Grid",
																										"Run All Jobs Locally",
																										true);
		additionalParameters.addParameter(useGrid);
		String workflowConfigFilename = System.getProperty("project.config.file");
		if (workflowConfigFilename != null){
			Properties workflowProperties = new Properties();
			String gridType = "";  // Make no grid support the default.
			try{
				workflowProperties.load(new BufferedInputStream(new FileInputStream(workflowConfigFilename)));
				gridType = workflowProperties.getProperty("grid.type");
			}
			catch (FileNotFoundException fnfe){
				WrapperPane.showMessageDialog(null,
																			"The configuration file " + workflowConfigFilename
																			+ " was not found.  Grid submissions will be disabled.",
																			"Configuration File Not Found",
																			JOptionPane.WARNING_MESSAGE);
			}
			catch (IOException ioe){
				WrapperPane.showMessageDialog(null,
																			"An I/O Error occurred while reading the configuration file "
																			+ workflowConfigFilename + ".  Grid submissions will be disabled.",
																			"I/O Error",
																			JOptionPane.WARNING_MESSAGE);
			}
			if (gridType.equals("")){
				useGrid.updateValue("Run All Jobs Locally");
				useGrid.setEnabled(false);
			}
			else if (gridType.equals("sge")){
				String workflowBinDir = System.getProperty("wf.root") + "/bin";
				String[] scripts = {"prolog", "epilog"};
				for (String script : scripts){
					if (new File(workflowBinDir + "/" + script + "_uninitialized").exists()){
						useGrid.updateValue("Run All Jobs Locally");
						useGrid.setEnabled(false);
						break;
					}
				}
			}
		}
	}

	/**
	 * The <code>requireParameters</code> method checks that every parameter in the supplied list of
	 * required parameters exists and has a non-null value.  If any required parameter does not
	 * exist or has a null value, the user is given an error message, and a default configuration
	 * file is loaded.  If a default configuration file cannot be loaded (perhaps because
	 * that file is itself invalid, IDEA terminates.
	 */
	protected void requireParameters(String[] requiredParameters){
		LinkedList<String> missingParameters = new LinkedList<String>();
		for (String requiredParameter : requiredParameters){
			if (getParameter(requiredParameter) == null){
				missingParameters.add(requiredParameter);
			}
		}
		if (missingParameters.size() != 0){
			StringBuffer errorMessage =
				new
				StringBuffer("The configuration file you loaded is missing the following required parameter(s):");
			for (String missingParameter : missingParameters){
				errorMessage.append("\n").append(missingParameter);
			}
			boolean fatal = false;
			if (IDEAInputGUI.DEFAULT_CONTROL_FILENAMES.contains(filename)){
				errorMessage.append("\n\n").append("Do not remove parameters from default configuration files.");
				fatal = true;
			}
			else{
				String defaultFilename = IDEAInputGUI.DEFAULT_CONTROL_FILENAMES.getFirst();
				try{
					load(defaultFilename);
					errorMessage.append("\n\n").append("The default configuration file ").append(defaultFilename);
					errorMessage.append(" has been loaded.");
				}
				catch (FileNotFoundException fnfe){
					errorMessage.append("\n\n").append("The default configuration file ").append(defaultFilename);
					errorMessage.append(" is missing.  IDEA cannot continue.");
					fatal = true;
				}
				catch (InvalidFileFormatException iffe){
					errorMessage.append("\n\n").append("The default configuration file ").append(defaultFilename);
					errorMessage.append(" is altered or corrupted.  IDEA cannot continue.");
					fatal = true;
				}
				catch (IOException fnfe){
					errorMessage.append("\n\n").append("The default configuration file ").append(defaultFilename);
					errorMessage.append(" could not be loaded.  IDEA cannot continue.");
					fatal = true;
				}
			}
			JOptionPane.showMessageDialog(null,
																		errorMessage.toString(),
																		"Required Parameter(s) Missing",
																		JOptionPane.ERROR_MESSAGE);
			if (fatal){
				System.exit(1);
			}
		}
	}

	/**
	 * The <code>initializeActionListeners</code> method adds action listeners to various parameters;
	 * each of these action listeners performs an action other than input validation on
	 * the parameter to which it is added, such as disabling another parameter or
	 * updating its value.
	 */
	protected void initializeActionListeners(){
		requireParameters(REQUIRED_PARAMETERS);
		Parameter createTreeSDM = getParameter("Create tree (single-dataset mode)");
		createTreeSDM.resetActionListeners();
		createTreeSDM.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					getParameter("Create tree").updateValue(e.getActionCommand()) ;
					if (e.getActionCommand().startsWith("Create tree")){
						getParameter("treefile").updateValue("MyDataset.PAMLtree");
					}
					getParameter("treefile").setEnabled(e.getActionCommand().startsWith("Use"));
				}
			});
		Parameter createTree = getParameter("Create tree");
		createTree.resetActionListeners();
		createTree.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					getParameter("Create tree (single-dataset mode)").updateValue(e.getActionCommand());
				}
			});
		Parameter datasetNameList = getParameter("Dataset name list");
		datasetNameList.addActionListener(new ActionListener(){
				// When the user selects a gene name list file, the input directory is updated to be the directory containing
				// that file, and the display is accordingly refreshed.
				public void actionPerformed(ActionEvent e){
					Parameter inputDirectory = getParameter("Input directory");
					if (inputDirectory.valueString().equals("")
							|| inputDirectory.valueString().equals(IDEAInputGUI.invocationDir)){
						String inputDir = new File(getParameter("Dataset name list").valueString()).getParent();
						inputDirectory.updateValue(inputDir);
					}
				}
			});
		Parameter ideaMode = getParameter("IDEA mode");
		ideaMode.resetActionListeners();
		ideaMode.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					setIDEAMode(e.getActionCommand());
				}
			});
		final Parameter runmode = getParameter("runmode");
		if (runmode != null){
			runmode.addActionListener(new ActionListener(){
					public void actionPerformed(ActionEvent e){
						Parameter nsSites = getParameter("NSsites");
						if ((nsSites != null) && (runmode.valueString().trim().equals("-2"))){
							nsSites.updateValue("0");
						}
					}
				});
		}
	}

	/**
	 * The <code>addInputVerifiers</code> method adds input verifiers for several parameters.
	 * These verifiers are not saved with configurations; old saved configurations must conform
	 * to new validation requirements.
	 */
	protected void addInputVerifiers(){
		requireParameters(REQUIRED_PARAMETERS);
		Parameter seqfile = getParameter("seqfile");
		if (seqfile != null){
			seqfile.addInputVerifier(new FilenameLengthLimiter(this){
					int maxAbsolutePathLength(){
						return 113;
					}
					String relativeTo(){
						return IDEAInputGUI.invocationDir;
					}
					int maxRelativePathLength(){
						return 113;
					}
				});
		}
		Parameter treefile = getParameter("treefile");
		if (treefile != null){
			treefile.addInputVerifier(new FilenameLengthLimiter(this){
					int maxAbsolutePathLength(){
						return 114;
					}
					String relativeTo(){
						Parameter pCreateTree = currentState.getParameter("Create tree");
						boolean createTree = (pCreateTree == null) || pCreateTree.valueString().startsWith("Create tree");
						String treeDirectory = IDEAInputGUI.invocationDir;  // Look here for trees when using an existing tree.
						if (createTree){  // When creating a new tree, look in the output directory.
							Parameter outfile = currentState.getParameter("outfile");
							if (outfile != null){
								String outputFilename = outfile.valueString();
								if (outputFilename.startsWith(File.separator)){
									treeDirectory = outputFilename.substring(0, outputFilename.lastIndexOf(File.separator));
								}
							}
						}
						return treeDirectory;
					}
					int maxRelativePathLength(){
						Parameter pCreateTree = currentState.getParameter("Create tree");
						boolean createTree = (pCreateTree == null) || pCreateTree.valueString().startsWith("Create tree");
						return createTree ? 110 : 113;
					}
				});
		}
		Parameter outfile = getParameter("outfile");
		FilenameLengthLimiter outfileChecker = new OmegaBasedFilenameLengthLimiter(this){
				int maxAbsolutePathLength(){
					return Math.max(111 - maxCharsForOmegaValue(), 0);
				}
				String relativeTo(){
					return IDEAInputGUI.invocationDir;
				}
				int maxRelativePathLength(){
					return Math.max(108 - maxCharsForOmegaValue(), 0);
				}
			};
		outfile.addInputVerifier(outfileChecker);
		String[] omegaParamNames = {"omega", "Extra omega values"};
		for (String omegaParamName : omegaParamNames){
			Parameter omegaParam = getParameter(omegaParamName);
			if (omegaParam != null){
				omegaParam.addInputVerifier(new AffixLengthLimiter("Dataset name list", omegaParamName, this));
				omegaParam.addInputVerifier(new AffixLengthLimiter("outfile", omegaParamName, this));
			}
		}
		Parameter datasetList = getParameter("Dataset name list");
		if (datasetList != null){
			datasetList.addInputVerifier(new DatasetListVerifier(this));
		}
		Parameter inputDirectory = getParameter("Input directory");
		if (inputDirectory != null){
			inputDirectory.addInputVerifier(new AffixLengthLimiter("Dataset name list", "Input directory", this));
		}
		Parameter nsSites = getParameter("NSsites");
		if (nsSites != null){
			nsSites.addInputVerifier(new StateDependentInputVerifier(this){
					public boolean verifyText(String text){
						Parameter runmode = currentState.getParameter("runmode");
						if (runmode == null){
							return true;
						}
						if (runmode.valueString().trim().equals("-2")){
							String[] values = text.trim().split("\\s+");
							return values.length <= 1;
						}
						return true;
					}
					String errorMessage(String text){
						return "When performing a pairwise analysis (runmode -2), you may only give one value for NSsites.";
					}
				});
		}
	}

	/**
	 * The <code>createVisualGroupings</code> method creates the
	 * visual groupings out of parameters in the two basic
	 * parameter sets (PAML parameters and additional parameters).
	 */
	protected void createVisualGroupings(){
		omittedParameters = new ParameterSet();  // Currently, no parameters are omitted.
		followUpPageParameters = new ParameterSet();  // no title
		followUpPageParameters.addParameter(getParameter("PAML program"));
		
		firstPageTopParameters = new ParameterSet("Input and Output Parameters");
		firstPageTopParameters.addParameter(getParameter("IDEA mode"));

		multipleDatasetParameters = new ParameterSet();  // no title
		multipleDatasetParameters.addParameter(getParameter("Dataset name list"));
		multipleDatasetParameters.addParameter(getParameter("Input directory"));
	  multipleDatasetParameters.addParameter(getParameter("Output directory"));
		multipleDatasetParameters.addParameter(BlankParameter.SOLE_INSTANCE);
		multipleDatasetParameters.addParameter(getParameter("Create tree"));

		singleDatasetParameters = new ParameterSet();  // no title
		singleDatasetParameters.addParameter(BlankParameter.SOLE_INSTANCE);
		singleDatasetParameters.addParameter(getParameter("seqfile"));
		singleDatasetParameters.addParameter(getParameter("outfile"));
		singleDatasetParameters.addParameter(getParameter("treefile"));
		singleDatasetParameters.addParameter(getParameter("Create tree (single-dataset mode)"));

		firstPageMiddleParameters = new ParameterSet();  // no title

		firstPageBottomParameters = new ParameterSet();
		firstPageBottomParameters.addParameter(getParameter("noisy"));
		firstPageBottomParameters.addParameter(getParameter("Email address (optional)"));
		firstPageBottomParameters.addParameter(getParameter("verbose"));
		firstPageBottomParameters.addParameter(getParameter("Use grid"));

		boolean codeml = getParameter("PAML program").valueString().equals("codeml");

		dataParameters = new ParameterSet("Data");
		dataParameters.addParameter(getParameter("runmode"));
		dataParameters.addParameter(getParameter("CodonFreq"));
		dataParameters.addParameter(getParameter("seqtype"));
		dataParameters.addParameter(getParameter("aaDist"));
		if (codeml){
			dataParameters.addParameter(getParameter("icode"));
		}
		dataParameters.addParameter(getParameter("aaRatefile"));
		dataParameters.addParameter(getParameter("Mgene"));
		if (! codeml){
			dataParameters.addParameter(getParameter("icode"));
		}

		modelParameters = new ParameterSet("Model");
		if (codeml){
			modelParameters.addParameter(getParameter("clock"));
			modelParameters.addParameter(getParameter("fix_kappa"));
			modelParameters.addParameter(getParameter("model"));
			modelParameters.addParameter(getParameter("kappa"));
			modelParameters.addParameter(getParameter("fix_omega"));
			modelParameters.addParameter(getParameter("fix_alpha"));
			modelParameters.addParameter(getParameter("omega"));
			modelParameters.addParameter(getParameter("alpha"));
			modelParameters.addParameter(getParameter("Extra omega values"));
			modelParameters.addParameter(getParameter("Malpha"));
			modelParameters.addParameter(getParameter("NSsites"));
			modelParameters.addParameter(getParameter("fix_rho"));
			modelParameters.addParameter(getParameter("ncatG"));
			modelParameters.addParameter(getParameter("rho"));
		}
		else{
			modelParameters.addParameter(getParameter("model"));
			modelParameters.addParameter(getParameter("clock"));
			modelParameters.addParameter(getParameter("fix_kappa"));
			modelParameters.addParameter(getParameter("fix_alpha"));
			modelParameters.addParameter(getParameter("kappa"));
			modelParameters.addParameter(getParameter("alpha"));
			modelParameters.addParameter(getParameter("nhomo"));  // baseml
			modelParameters.addParameter(getParameter("Malpha"));
			modelParameters.addParameter(getParameter("fix_rho"));
			modelParameters.addParameter(getParameter("ncatG"));
			modelParameters.addParameter(getParameter("rho"));
			modelParameters.addParameter(getParameter("nparK"));  // baseml
		}
		// Add all remaining parameters to the `other' parameter area.
		otherParameters = new ParameterSet("Options");
		otherParameters.addParameter(getParameter("method"));
		otherParameters.addParameter(getParameter("RateAncestor"));
		otherParameters.addParameter(getParameter("fix_blength"));
		otherParameters.addParameter(getParameter("getSE"));
		otherParameters.addParameter(getParameter("Small_Diff"));
		otherParameters.addParameter(getParameter("cleandata"));
		for (Parameter p : pamlParameters.getParameterList()){
			if ((followUpPageParameters.getParameter(p.name) == null)
					&& (firstPageTopParameters.getParameter(p.name) == null)
					&& (multipleDatasetParameters.getParameter(p.name) == null)
					&& (singleDatasetParameters.getParameter(p.name) == null)
					&& (firstPageMiddleParameters.getParameter(p.name) == null)
					&& (firstPageBottomParameters.getParameter(p.name) == null)
					&& (dataParameters.getParameter(p.name) == null)
					&& (modelParameters.getParameter(p.name) == null)
					&& (otherParameters.getParameter(p.name) == null)
					&& (omittedParameters.getParameter(p.name) == null)){
				otherParameters.addParameter(p);
			}
		}
	}

	/**
	 * The <code>setIDEAMode</code> method enables either the single-dataset or the
	 * multiple-dataset parameters based on the IDEA mode and updates the values
	 * for seqfile, treefile and outfile.
	 *
	 * @param ideaMode the value of the "IDEA mode" parameter, either "Single Dataset" or "Multi-Dataset"
	 */
	private void setIDEAMode(String ideaMode){
		boolean singleDataset = ideaMode.equals("Single Dataset");
		String filenamePrefix = singleDataset ? "MyDataset" : "<dataset name>";
		getParameter("Dataset name list").setEnabled(! singleDataset);
		getParameter("Input directory").setEnabled(! singleDataset);
		getParameter("Output directory").setEnabled(! singleDataset);
		getParameter("Create tree").setEnabled(! singleDataset);
		getParameter("seqfile").updateValue(filenamePrefix + ".PAMLseq");
		getParameter("treefile").updateValue(filenamePrefix + ".PAMLtree");
		getParameter("outfile").updateValue((singleDataset ? (IDEAInputGUI.invocationDir + "/") : "") + filenamePrefix + ".PAMLout");
		getParameter("seqfile").setEnabled(singleDataset);
		getParameter("outfile").setEnabled(singleDataset);
		getParameter("treefile").setEnabled(singleDataset && (! ((String) getParameter("Create tree").valueString()).startsWith("Create tree")));
		getParameter("Create tree (single-dataset mode)").setEnabled(singleDataset);
		MultipleChoiceParameter currentTreeParameter =
			(MultipleChoiceParameter) (singleDataset ? getParameter("Create tree (single-dataset mode)") : getParameter("Create tree"));
		if (PHYML_FOUND && (! PHYLIP_FOUND)){
			currentTreeParameter.setEnabled(0, 2);
		}
		if (PHYLIP_FOUND && (! PHYML_FOUND)){
			currentTreeParameter.setEnabled(1, 2);
		}
		if ((! PHYML_FOUND) && (! PHYLIP_FOUND)){
			currentTreeParameter.setEnabled(2);
		}
	}

}
