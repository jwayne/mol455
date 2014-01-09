package edu.umaryland.igs.idea;

import java.awt.Color;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.table.AbstractTableModel;

import edu.umaryland.igs.aegan.utils.FileParser;
import edu.umaryland.igs.aegan.utils.SwingThreadSafeComponent;

/**
 * <code>SummaryTableModel</code> represents the back end for the table displayed by
 * <code>IDEAOutputGUI</code>.  Its contents are derived from the .mlc files
 * created by IDEA or potentially from  PAML output files not generated by IDEA.
 * The data are stored in a complex data structure described below.
 * <code>SummaryTableModel</code> is an abstract class.  It is extended by the
 * concrete classes <code>StandardSummaryTableModel</code> and <code>PairwiseSummaryTableModel</code>.
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

public abstract class SummaryTableModel extends AbstractTableModel{
	
	static final String KAPPA = "\u03BA";  // the small Greek letter kappa
	static final String OMEGA = "\u03C9";  // the small Greek letter omega
	static final String model0 = "0-one-ratio";  // the name for model 0
	static final HashMap<String, String> MODEL_NAMES = new HashMap<String, String>();

	static{
		MODEL_NAMES.put("NearlyNeutral", "1-NearlyNeutral");
		MODEL_NAMES.put("PositiveSelection", "2-PositiveSelection");
		MODEL_NAMES.put("discrete", "3-discrete");
		MODEL_NAMES.put("freqs", "4-freqs");
		MODEL_NAMES.put("gamma", "5-gamma");
		MODEL_NAMES.put("2gamma", "6-2gamma");
		MODEL_NAMES.put("beta", "7-beta");
		MODEL_NAMES.put("beta&w>1", "8-beta&w>1");
		MODEL_NAMES.put("beta&gamma", "9-beta&gamma");
		MODEL_NAMES.put("beta&gamma+1", "10-beta&gamma+1");
		MODEL_NAMES.put("beta&normal>1", "11-beta&normal>1");
		MODEL_NAMES.put("0&normal>0", "12-0&normal>0");
		MODEL_NAMES.put("3normal>0", "13-3normal>0");
	}

	static String getLongModelName(String modelName){
		String longVersion = MODEL_NAMES.get(modelName);
		return (longVersion == null) ? modelName : longVersion;
	}

	// COLUMN_NAMES, TEXT_COLUMN_NAMES and COLUMN_INDICES are conceptually constants which vary by subclass.
	// To achieve the correct inheritance behavior, they must be declared as methods rather than fields.
	// These methods are implemented identically for all subclasses, but they refer to each class's unique fields.
	abstract String[] COLUMN_NAMES();  // an array containing the Unicode name of each column header
	abstract String[] TEXT_COLUMN_NAMES();  // an array containing the ASCII name (e.g., "Omega") of each column header
	abstract HashMap<String, Integer> COLUMN_INDICES();  // a mapping of column names to their numerical indices

	protected Color backgroundColor = IDEAColors.PALE_LAVENDER; // This means the rows for the first dataset will be white.

	// Each dataset is associated with one background color.
	protected HashMap<Object, Color> datasetColors = new HashMap<Object, Color>();

	// the directory creating the data files for this dataset
	protected String directoryContainingOutputFiles;

	// The data are stored in a linked list of linked lists of vectors of objects:
	// The objects represent data items or display items such as buttons,
	// each vector represents a row of data for a single (dataset, model) pair,
	// each linked list of vectors represents the data for one dataset, and
	// the top-level linked list represents the data for all datasets.
	LinkedList<LinkedList<Vector<Object>>> data = new LinkedList<LinkedList<Vector<Object>>>();

	/**
	 * The static method <code>load</code> creates a <code>SummaryTableModel</code>
	 * from data in files in the specified directory.  This method follows the
	 * factory object pattern:  It returns an instance of a concrete subclass.
	 *
	 * @param dirName the directory containing the .mlc files to use
	 * @param owner the GUI into which results are being loaded
	 * @param progress a progress bar to update as loading progesses; may be null
	 *
	 * @throws IOException if an output file is deleted while data is being loaded or an I/O error occurs
	 */
	public static SummaryTableModel load(String dirName,
																			 JFrame owner,
																			 final SwingThreadSafeComponent<JProgressBar> progress)
		throws IOException{
		StandardSummaryTableModel rv = new StandardSummaryTableModel();  // Implicitly call AbstractTableModel's constructor.
		rv.directoryContainingOutputFiles = dirName;

		// Get a list of all .mlc files in the specified directory.
		File[] mlcFiles = new File(rv.directoryContainingOutputFiles).listFiles(new FileFilter(){
				public boolean accept(File pathname){
					return pathname.isFile()
						&& pathname.canRead()
						&& (pathname.getName().endsWith(".mlc") || pathname.getName().endsWith(".merged"));
				}
	    });

		if ((mlcFiles == null) || (mlcFiles.length == 0)){
	    throw new IOException("There are no readable IDEA output files in "
														+ dirName
														+ ".\nPossible reasons for this include:\n1. "
														+ dirName
														+ " is not an IDEA output directory.\n2. You lack read permission on "
														+ dirName
														+ ".\n3. A disk- or network-related error occurred.");
		}

		// Sort the array of files by dataset name.
		Arrays.sort(mlcFiles);

		// Parse each .mlc file and insert the desired data into the table model.
		FileParser mlcFileParser;
		Pattern pairwiseFlagPattern = Pattern.compile("pairwise comparison\\,");
		Pattern treeBasedPattern = Pattern.compile("^\\s*Model\\s+\\d+");
		Pattern alternateTreeBasedPattern = Pattern.compile("^\\s*Model\\:\\s+[A-Z]");
		Pattern nLinePattern = Pattern.compile("ns\\s+=\\s+(\\d+)\\s+");
		Pattern modelLinePattern = Pattern.compile("Model\\s+(\\d+)\\:\\s+(\\S+)");
		Pattern alternateModelLinePattern = Pattern.compile("Site-class models:  (\\S+)");
		LinkedList<Pattern> columnLinePatterns = new LinkedList<Pattern>();
		Pattern likelihoodLinePattern = Pattern.compile("lnL\\(ntime\\:\\s*\\d+\\s+np\\:\\s*\\d+\\)\\:\\s+(\\S+)\\s+");
		columnLinePatterns.add(likelihoodLinePattern);
		columnLinePatterns.add(Pattern.compile("tree length\\s+\\=\\s+(\\S+)"));
		columnLinePatterns.add(Pattern.compile("kappa\\s+\\(ts\\/tv\\)\\s+\\=\\s+(\\S+)"));
		Pattern omegaLinePattern = Pattern.compile("\\d+\\.\\.\\d+\\s+\\d+\\.\\d+\\s+\\S+\\s+\\S+\\s+(\\S+)\\s+");
		columnLinePatterns.add(omegaLinePattern);
		int pairwiseDatasets = 0;
		int treeDatasets = 0;
		LinkedList<File> treeBasedMLCFiles = new LinkedList<File>();
		DATASET: for (int i = 0; i < mlcFiles.length; i++){
	    // The first pass is to determine if we should load in pairwise mode.
	    mlcFileParser = new FileParser(mlcFiles[i].getAbsolutePath());
	    String nextLine = null;
	    try{
				while (true){
					nextLine = mlcFileParser.nextLine();
					Matcher pairwiseFlagPatternMatcher = pairwiseFlagPattern.matcher(nextLine);
					if (pairwiseFlagPatternMatcher.find()){
						pairwiseDatasets++;
						continue DATASET;
					}
				}
			}
			catch (EOFException eofe){
				mlcFileParser.reload();
				try{
					while (true){
						nextLine = mlcFileParser.nextLine();
						Matcher treeBasedPatternMatcher = treeBasedPattern.matcher(nextLine);
						Matcher alternateTreeBasedPatternMatcher = alternateTreeBasedPattern.matcher(nextLine);
						if (treeBasedPatternMatcher.find() || alternateTreeBasedPatternMatcher.find()){
							treeDatasets++;
							treeBasedMLCFiles.add(mlcFiles[i]);
							continue DATASET;
						}
					}
				}
				catch (EOFException eofe2){
					// If this exception is encountered, it means the output file did not have either of the expected
					// formats (pairwise or tree-based).  This indicates that an error occurred during PAML.
					// No action is taken.
				}
			}
		}
		boolean pairwise;
		if (pairwiseDatasets == 0){
	    pairwise = false;
		}
		else{
	    if (treeDatasets == 0){
				pairwise = true;
	    }
	    else{
				try{
					SwingThreadSafeComponent<JOptionPane> optionPane =
						new SwingThreadSafeComponent<JOptionPane>(JOptionPane.class, new Object(), JOptionPane.QUESTION_MESSAGE);
					Object[] modeChoices = {"View Pairwise Datasets", "View Non-Pairwise Datasets"};
					int modeChoice = ((Integer)
														optionPane.callSafeMethod("showOptionDialog",
																											owner,
																											"Your dataset list contains " + treeDatasets
																											+ " non-pairwise dataset" + ((treeDatasets == 1) ? "" : "s")
																											+ " (for which a tree-based analysis was performed)\nand "
																											+ pairwiseDatasets + " pairwise dataset"
																											+ ((pairwiseDatasets == 1) ? "" : "s")
																											+ " (for which a pairwise analysis was performed).\nBecause the analyses are not comparable, you may view only one type of dataset at once.",
																											"Mixed Dataset Types",
																											JOptionPane.YES_NO_OPTION,
																											JOptionPane.QUESTION_MESSAGE,
																											optionPane.callSafeMethod("getIcon"),  // use default icon
																											modeChoices,
																											modeChoices[0])).intValue();
					pairwise = (modeChoice != JOptionPane.NO_OPTION);
				}
				catch (InvocationTargetException ite){
					// No exception is expected to be thrown from JOptionPane(new Object(), JOptionPane.QUESTION_MESSAGE).
					throw new RuntimeException(ite);
				}
	    }
		}
		if (pairwise){
	    if (progress != null){
				progress.callSafeMethod("setMaximum", (2 + PairwiseSummaryTableModel.COLUMN_NAMES.length) * mlcFiles.length);
				progress.callSafeMethod("setValue", 0);
				progress.callSafeMethod("setIndeterminate", false);
	    }
	    return new PairwiseSummaryTableModel(dirName, mlcFiles, progress);
		}
		mlcFiles = treeBasedMLCFiles.toArray(mlcFiles);  // Don't process pairwise files any further.
		LinkedList<String> malformattedLRTFiles = new LinkedList<String>();
		LinkedList<String> missingLRTFiles = new LinkedList<String>();
		for (int i = 0; i < treeBasedMLCFiles.size(); i++){// For correctness, use size() of list instead of length of array.
	    String filename = mlcFiles[i].getName();
	    String datasetName = filename.substring(0,
																							Math.max(filename.lastIndexOf(".mlc"), filename.lastIndexOf(".merged")));
	    String lrtFilename = mlcFiles[i].getParent() + File.separator + datasetName + ".lrt";
	    String nextLine;
	    HashMap<SiteSubstitutionModel, LikelihoodData> lrtResults = new HashMap<SiteSubstitutionModel, LikelihoodData>();
	    try{
				FileParser lrtFileParser = new FileParser(lrtFilename);
				try{
					while (true){
						nextLine = lrtFileParser.nextLine();
						String[] subparts = nextLine.split("\\t+");
						try{
							if (subparts[0].equals(datasetName) || subparts[0].equals("ONLY")){
								SiteSubstitutionModel altModel =
									new SiteSubstitutionModel(subparts[1].substring(0, subparts[1].indexOf(" ")));
								SiteSubstitutionModel nullModel =
									new SiteSubstitutionModel(subparts[1].substring(subparts[1].lastIndexOf(" ") + 1,
																																	subparts[1].length()));
								lrtResults.put(altModel,
															 new LikelihoodData(Double.parseDouble(subparts[2]), Double.parseDouble(subparts[3]),
																									Double.parseDouble(subparts[4]), Double.parseDouble(subparts[5]),
																									subparts[6].equals("Yes"), altModel, nullModel));
							}
						}
						catch (IllegalArgumentException iae){
							malformattedLRTFiles.add(lrtFilename + "\n" + iae.getMessage());
						}
						catch (IndexOutOfBoundsException ioobe){
							malformattedLRTFiles.add(lrtFilename + "\nMalformatted line:  " + nextLine);
						}
					}
				}
				catch (EOFException eofe){
					// This exception is expected; no action is taken.
				}
				lrtFileParser.closeFile();
	    }
	    catch (FileNotFoundException fnfe){
				missingLRTFiles.add(datasetName);				
	    }
	    LinkedList<Vector<Object>> dataForDataset = new LinkedList<Vector<Object>>();
	    mlcFileParser = new FileParser(mlcFiles[i].getAbsolutePath());
	    boolean acceptOmegaLine = false;
	    Vector<Object> dataForModel = null;
	    Integer modelsForDataset = 0;
	    try{
				String n = "N/A";  // number of sequences in the dataset
				LINE: while (true){
					nextLine = mlcFileParser.nextLine();
					Matcher nLinePatternMatcher = nLinePattern.matcher(nextLine);
					if (nLinePatternMatcher.lookingAt()){
						n = nLinePatternMatcher.group(1);
						continue LINE;
					}
					Matcher modelLinePatternMatcher = modelLinePattern.matcher(nextLine);
					if (modelLinePatternMatcher.find()){
						if (dataForModel != null){  // Add the data for the previous model.
							dataForDataset.add(dataForModel);
							modelsForDataset++;
						}
						String model = modelLinePatternMatcher.group(1) + "-" + modelLinePatternMatcher.group(2);
						dataForModel = new Vector<Object>(9);
						dataForModel.add(datasetName);
						dataForModel.add(n);
						dataForModel.add(model);
						continue LINE;
					}
					Matcher alternateModelLinePatternMatcher = alternateModelLinePattern.matcher(nextLine);
					if (alternateModelLinePatternMatcher.find()){
						String model = getLongModelName(alternateModelLinePatternMatcher.group(1));
						dataForModel = new Vector<Object>(9);
						dataForModel.add(datasetName);
						dataForModel.add(n);
						dataForModel.add(model);
						continue LINE;
					}
					for (Pattern columnLinePattern : columnLinePatterns){
						Matcher columnLinePatternMatcher = columnLinePattern.matcher(nextLine);
						if (columnLinePatternMatcher.find()){
							if (columnLinePattern.equals(likelihoodLinePattern)){
								// The likelihood line should be seen before the omega line.
								// Originally, the code expected the omega line at some point after the model line, but it is now known
								// that PAML does not produce a model line if only one model is used.
								acceptOmegaLine = true;
							}
							if (columnLinePattern.equals(omegaLinePattern)){
								if (! acceptOmegaLine){
									continue LINE;  // The omega value is given on multiple lines; add it only once.
								}
							}
							if (dataForModel == null){
								dataForModel = new Vector<Object>(9);
								dataForModel.add(datasetName);
								dataForModel.add(n);
								dataForModel.add(model0);  // Model 0 is assumed when PAML doesn't indicate any other model was used.
							}
							if (columnLinePattern.equals(likelihoodLinePattern)){
								LikelihoodData lrtResultsForModel =
									lrtResults.get(new SiteSubstitutionModel((String) dataForModel.get((Integer)
																																										 rv.COLUMN_INDICES().get("Model"))));
								dataForModel.add((lrtResultsForModel == null)
																 ? new LikelihoodData(columnLinePatternMatcher.group(1))
																 : lrtResultsForModel);
							}
							else{
								dataForModel.add(columnLinePatternMatcher.group(1));  // tree length, kappa or omega
							}
							if (columnLinePattern.equals(omegaLinePattern)){
								acceptOmegaLine = false;
							}
							continue LINE;  // since at most one column pattern will be matched
						}
					}
				}
	    }
	    catch (EOFException eofe){
				if (dataForModel != null){  // Add the data for the last model.
					dataForDataset.add(dataForModel);
					modelsForDataset++;
				}
	    }
	    mlcFileParser.closeFile();
	    if ((progress != null) && (rv.data.size() == 0)){  // We've just finished parsing the first file.
				// For the purpose of reporting progress, assume all datasets have the same number of models as this one.
				// Switch the progress bar to determinate mode.
				// At this point, we know we are creating a StandardSummaryTableModel.
				// For each file in mlcFiles, a single double-length task will be executed, and in addition,
				// one single-length task will be executed for each table cell.  [A cell is a (model, column) pair.]
				progress.callSafeMethod("setMaximum",
																(2 + StandardSummaryTableModel.COLUMN_NAMES.length * modelsForDataset)
																* mlcFiles.length);
				progress.callSafeMethod("setValue", 0);
				progress.callSafeMethod("setIndeterminate", false);
	    }
	    rv.data.add(dataForDataset);
	    if (progress != null){
				progress.callSafeMethod("setValue", ((Integer) progress.callSafeMethod("getValue")) + 2);
	    }
		}
		int malformattedFiles = malformattedLRTFiles.size();
		int missingFiles = missingLRTFiles.size();
		if (malformattedFiles + missingFiles > 0){
	    rv.lrtExceptions = new LinkedList<IOException>();
	    if (malformattedFiles > 0){
				StringBuffer malformattedExplanation = new StringBuffer("Malformatted LRT Results:The following file");
				if (malformattedFiles > 1){
					malformattedExplanation.append("s are malformatted:\n\n");
				}
				else{
					malformattedExplanation.append(" is malformatted:\n\n");
				}
				for (String malformattedLRTFile : malformattedLRTFiles){
					malformattedExplanation.append(malformattedLRTFile).append("\n");
				}
				malformattedExplanation.append("\nMalformatted results may be wholly or partially excluded from the display.");
				rv.lrtExceptions.add(new IOException(malformattedExplanation.toString()));
	    }
	    if (missingFiles > 0){
				StringBuffer missingExplanation = new StringBuffer("Missing LRT Results:");
				if (missingFiles == treeDatasets){  // no LRT results at all
					missingExplanation.append("No LRT results are available.  This may mean step 4 (idea-D-parse-output.pl) has not yet finished or was unsuccessful, or these results may have been generated using an older version of IDEA (LRT results were introduced in IDEA 2.4).");
				}
				else{
					missingExplanation.append("No LRT results were available for the following dataset");
					if (missingFiles > 1){
						missingExplanation.append("s");
					}
					missingExplanation.append(":\n\n");
					for (String missingLRTFile : missingLRTFiles){
						missingExplanation.append(missingLRTFile);
					}
				}
				rv.lrtExceptions.add(new IOException(missingExplanation.toString()));
	    }
		}
		int maxColumns = 0;
		DATASET_DATA: for (LinkedList<Vector<Object>> datasetData : rv.data){
	    for (Vector<Object> modelData : datasetData){
				maxColumns = Math.max(maxColumns, modelData.size());
				if (maxColumns > 3){
					break DATASET_DATA;
				}
	    }
		}
		if (maxColumns <= 3){
	    throw new IOException("There are no valid IDEA output files in "
														+ dirName
														+ ".\nPossible reasons for this include:\n1. The analysis was aborted.\n2. PAML produced no output or produced invalid output.\n3. "
														+ dirName
														+ " is not an IDEA output directory.\n4. The analysis is still running, and no dataset has yet been completely analyzed.\n5. You lack execute permission on "
														+ dirName
														+ ".");
		}
		return rv;
	}


	// OTHER ABSTRACT INSTANCE METHODS

	/**
	 * The <code>buttons</code> method returns a linked list containing the string names of the buttons
	 * that should be displayed in the specified column's header.  The column names are associated with
	 * the names of methods to be activated when the buttons are clicked.  See class <code>SummaryTableButton</code>.
	 *
	 * @param column the index of the column whose button names are to be listed
	 *
	 * @return a linked list of the names of the buttons that should be displayed in the specified column's header
	 */
	public abstract LinkedList<String> buttons(int column);

	/**
	 * The <code>displayTreeButtonInColumn</code> method returns true if a Tree button should be shown in the given column.
	 *
	 * @param column the index of the column whose tree-button status is to be determined
	 *
	 * @return true if a Tree button should be displayed in the column; false otherwise
	 */
	public abstract boolean displayTreeButtonInColumn(int column);

	/**
	 * The <code>highlightRowBasedOnLikelihood</code> method returns true
	 * if values in the specified row should be printed in bold because
	 * of the likelihood value in that row.
	 *
	 * @param row the index of the row whose likelihood-based highlighting status is to be determined
	 *
	 * @return true if the row should be shown in bold because of its likelihood; false otherwise
	 */
	public abstract boolean highlightRowBasedOnLikelihood(int row);

	/**
	 * Row selection is indicated on the table by displaying an outline around all or some of the
	 * row's cells.  The <code>leftMostColumnToDisplayAsSelected</code> method returns the index of the
	 * leftmost column to be included in the outlined area when a row is selected.
	 *
	 * @return the leftmost column to be included in the outlined area when a row is selected
	 */
	public abstract int leftmostColumnToDisplayAsSelected();

	/**
	 * The <code>numericalValuesInColumn</code> method returns a linked list
	 * of the values in the requested column (which should be a numeric column).
	 * This is useful for making histograms.  Only values in rows matching the
	 * given model are included in the returned list.
	 *
	 * @param column the requested column
	 * @param model the model to restrict results to, or StandardSummaryTableModel.LIKELIEST

	 * @return a linked list of values for the requested model in the requested column
	 */
	public abstract LinkedList<Double> numericalValuesInColumn(int column, String model);

	/**
	 * The <code>showHistogram</code> method is called when the user requests a histogram.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param column the column for which the histogram is requested
	 * @param ownerWindow the owner for any windows to be displayed in response
	 *
	 */
	public abstract void showHistogram(int column, JFrame ownerWindow);


	// NON-ABSTRACT INSTANCE METHODS

	/**
	 * The <code>getColumnCount</code> method returns the number of columns in the model.
	 * <code>SummaryTableModel</code> implements this <code>TableModel</code> method
	 * to reflect its data structure.
	 *
	 * @return the number of columns in the model
	 */
	public int getColumnCount(){
		if (data.size() == 0){
	    return 0;
		}
		for (LinkedList<Vector<Object>> datasetData : data){
	    if (datasetData.size() > 0){
				return datasetData.get(0).size();
	    }
		}
		return 0;
	}
	
	/**
	 * The <code>getRowCount</code> method returns the number of rows in the model.
	 * <code>SummaryTableModel</code> implements this <code>TableModel</code> method
	 * to reflect its data structure.
	 *
	 * @return the number of rows in the model
	 */
	public int getRowCount(){
		int rowCount = 0;
		for (LinkedList<Vector<Object>> datasetData : data){
	    rowCount+= datasetData.size();
		}
		return rowCount;
	}
	
	/**
	 * The <code>getValueAt</code> method returns the value at the specified position.
	 * <code>SummaryTableModel</code> implements this <code>TableModel</code> method
	 * to reflect its data structure.
	 *
	 * @param row the queried row
	 * @param col the queried column
	 * @return the value at the queried (row, column) position
	 */
	public Object getValueAt(int row, int col){
		Vector<Object> rowOfData = null;
		int rowsExamined = 0;
		for (LinkedList<Vector<Object>> datasetData : data){
	    if (row < rowsExamined + datasetData.size()){
				rowOfData = datasetData.get(row - rowsExamined);
				break;
	    }
	    else{
				rowsExamined+= datasetData.size();
	    }
		}
		return ((rowOfData == null) || (rowOfData.size() <= col)) ? "" : rowOfData.get(col);
	}

	/**
	 * The <code>isStarterRow</code> method returns true iff the row with the specified
	 * number is the first row displayed for its dataset.  This is dependent on the
	 * current sort.
	 *
	 * @param row the queried row
	 * @return true iff the specified row is the first row displayed for its dataset
	 */
	public boolean isStarterRow(int row){
		int rowsExamined = 0;
		for (LinkedList<Vector<Object>> datasetData : data){
	    if (row == rowsExamined){
				return true;
	    }
	    else{
				rowsExamined+= datasetData.size();
	    }
		}
		return false;
	}

	/** The <code>getDatasetNames</code> method returns a set of all the dataset names.
	 *
	 * @return a <code>LinkedHashSet</code> containing all the dataset names.
	 */
	public LinkedHashSet<String> getDatasetNames(){
		LinkedHashSet<String> rv = new LinkedHashSet<String>();
		for (int i = 0; i < getRowCount(); i++){
	    rv.add((String) getValueAt(i, COLUMN_INDICES().get("Dataset")));  // Duplicates are not added.
		}
		return rv;
	}

	/**
	 * The <code>getColumnName</code> method returns the name of the specified column.
	 * <code>SummaryTableModel</code> overrides this <code>AbstractTableModel</code>
	 * method to provide descriptive column headers.
	 *
	 * @return the name of the column with the specified index
	 */
	public String getColumnName(int columnIndex){
		return COLUMN_NAMES()[columnIndex];
	}

	/**
	 * This method returns the maximum number of characters in this column for any row,
	 * including the header row.  This is useful for text printing.
	 *
	 * @param columnIndex the index of the columnOfIndex
	 *
	 * @return the maximum width in characters of any value in the column or the column header
	 */
	public int columnWidthInCharacters(int columnIndex){
		int maxColumnWidth = TEXT_COLUMN_NAMES()[columnIndex].length();
		for (int i = 0; i < getRowCount(); i++){
	    maxColumnWidth = Math.max(maxColumnWidth, getValueAt(i, columnIndex).toString().length());
		}
		return maxColumnWidth;
	}

	/**
	 * The <code>nextBackgroundColor</code> method is used to alternate the background colors for datasets in the table.
	 * It returns whichever of the two row background colors was not returned the last time it was called.
	 *
	 * @return the row background color that was not returned the last time <code>nextBackgroundColor</code was called
	 */
	protected Color nextBackgroundColor(){
		backgroundColor = backgroundColor.equals(Color.WHITE) ? IDEAColors.PALE_LAVENDER : Color.WHITE;
		return backgroundColor;
	}

	/**
	 * The <code>assignColorTo</code> method associates the next background color with the specified dataset unless that
	 * dataset is already associated with a background color.  The association lasts until the table is re-sorted.
	 *
	 * @param datasetIdentifier the dataset (probably a String) with which to associate the next background color
	 */
	void assignColorTo(Object datasetIdentifier){
		if (! datasetColors.containsKey(datasetIdentifier)){
	    datasetColors.put(datasetIdentifier, nextBackgroundColor());
		}
	}

	/**
	 * The <code>getColor</code> method returns the cell background color currently associated with the specified dataset.
	 *
	 * @param datasetIdentifier the dataset whose background color is being queried (probably a String)
	 *
	 * @return the cell background color currently associated with <code>datasetIdentifier</code>
	 */
	Color getColor(Object datasetIdentifier){
		return datasetColors.get(datasetIdentifier);
	}
	
	/**
	 * The protected method <code>resetColors</code> is called by <code>sortAscendingGlobal</code> and
	 * <code>sortDescendingGlobal</code>.  It removes all dataset-color associations so the sorted table
	 * will still have alternating colors, one for each dataset.
	 */
	protected void resetColors(){
		datasetColors.clear();
	}
	

	// BUTTON METHODS

	/** 
	 * The <code>sortAscendingGlobal</code> method sorts across
	 * datasets in ascending order of the specified parameter.
	 * Numeric values appear in ascending order above string values, which appear
	 * in ascending order above non-numeric, non-string values.
	 * This method modifies the underlying data structure.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param columnIndex the index of the column on which to sort
	 * @param ownerWindow ignored; may be null  (This parameter exists for all button methods since some may require it.)
	 */
	public void sortAscendingGlobal(final int columnIndex, JFrame ownerWindow){
		resetColors();
		Collections.sort(data, new Comparator<LinkedList<Vector<Object>>>(){
											 public int compare(LinkedList<Vector<Object>> row1, LinkedList<Vector<Object>> row2){
												 Double value1 = null;
												 Double value2 = null;
												 String stringValue1 = null;
												 String stringValue2 = null;
												 Object object1 = null;
												 Object object2 = null;
												 try{
													 object1 = row1.getFirst().get(columnIndex);
												 }
												 catch (NoSuchElementException nsee){
													 return 1;
												 }
												 try{
													 object2 = row2.getFirst().get(columnIndex);
												 }
												 catch (NoSuchElementException nsee){
													 return -1;
												 }
												 try{
													 stringValue1 = (String) object1;
												 }
												 catch (ClassCastException cce){
													 try{
														 stringValue2 = (String) object2;
														 return 1;  // Since object2 is a string but object1 isn't, object2 comes before object1.
													 }
													 catch (ClassCastException cce2){
														 return object1.toString().compareTo(object2.toString());
													 }
												 }
												 try{
													 stringValue2 = (String) object2;
												 }
												 catch (ClassCastException cce){
													 return -1;  // Since object1 is a string but object2 isn't, object1 comes before object2.
												 }
												 try{
													 value1 = new Double(stringValue1);
												 }
												 catch (NumberFormatException nfe){
													 try{
														 value2 = new Double(stringValue2);
														 return 1;  // Since stringValue2 is numeric but stringValue1 isn't, object2 comes before object1.
													 }
													 catch (NumberFormatException nfe2){
														 return stringValue1.compareTo(stringValue2);  // Sort strings in alphanumeric (string) order.
													 }
												 }
												 try{
													 value2 = new Double(stringValue2);
												 }
												 catch (NumberFormatException nfe){
													 return -1;  // Since stringValue1 is numeric but stringValue2 isn't, object1 comes before object2.
												 }
												 return value1.compareTo(value2);
											 }
										 });
	}

	/** 
	 * The <code>sortDescendingGlobal</code> method sorts across
	 * datasets in descending order of the specified parameter.
	 * Numeric values appear in descending order above string values, which appear
	 * in descending order above non-numeric, non-string values.
	 * This method modifies the underlying data structure.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param columnIndex the index of the column on which to sort
	 * @param ownerWindow ignored; may be null  (This parameter exists for all button methods since some may require it.)
	 */
	public void sortDescendingGlobal(final int columnIndex, JFrame ownerWindow){
		resetColors();
		Collections.sort(data, new Comparator<LinkedList<Vector<Object>>>(){
											 public int compare(LinkedList<Vector<Object>> row1, LinkedList<Vector<Object>> row2){
												 Double value1 = null;
												 Double value2 = null;
												 String stringValue1 = null;
												 String stringValue2 = null;
												 Object object1 = null;
												 Object object2 = null;
												 try{
													 object1 = row1.getFirst().get(columnIndex);
												 }
												 catch (NoSuchElementException nsee){
													 return 1;
												 }
												 try{
													 object2 = row2.getFirst().get(columnIndex);
												 }
												 catch (NoSuchElementException nsee){
													 return -1;
												 }
												 try{
													 stringValue1 = (String) object1;
												 }
												 catch (ClassCastException cce){
													 try{
														 stringValue2 = (String) object2;
														 return 1;  // Since object2 is a string but object1 isn't, object2 comes before object1.
													 }
													 catch (ClassCastException cce2){
														 return object2.toString().compareTo(object1.toString());
													 }
												 }
												 try{
													 stringValue2 = (String) object2;
												 }
												 catch (ClassCastException cce){
													 return -1;  // Since object1 is a string but object2 isn't, object1 comes before object2.
												 }
												 try{
													 value1 = new Double(stringValue1);
												 }
												 catch (NumberFormatException nfe){
													 try{
														 value2 = new Double(stringValue2);
														 return 1;  // Since stringValue2 is numeric but stringValue1 isn't, object2 comes before object1.
													 }
													 catch (NumberFormatException nfe2){
														 return stringValue2.compareTo(stringValue1);  // Sort strings in alphanumeric (string) order.
													 }
												 }
												 try{
													 value2 = new Double(stringValue2);
												 }
												 catch (NumberFormatException nfe){
													 return -1;  // Since stringValue1 is numeric but stringValue2 isn't, object1 comes before object2.
												 }
												 return value2.compareTo(value1);
											 }
										 });
	}

}