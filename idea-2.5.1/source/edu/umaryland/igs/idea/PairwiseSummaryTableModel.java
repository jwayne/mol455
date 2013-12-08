package edu.umaryland.igs.idea;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JProgressBar;

import edu.umaryland.igs.aegan.utils.FileParser;
import edu.umaryland.igs.aegan.utils.SwingThreadSafeComponent;

/**
 * <code>PairwiseSummaryTableModel</code> is the subclass of <code>SummaryTableModel</code> used for
 * tables derived from pairwise IDEA analyses (those for which all datasets
 * have exactly two sequences).  These analyses differ from non-pairwise
 * analyses in that multiple models for site-class evolution are not used.
 * Therefore, there is only one set of results for each dataset.  For
 * simplicity, the same data structure is used for all summary table models;
 * for pairwise models, each vector of vectors of objects representing a
 * dataset contains only one vector of objects.  Pairwise data tables
 * contain two extra columns (dN and dS, the rates of nonsynonymous and
 * synonymous substitutions, respectively).  <code>PairwiseSummaryTableModel</code>
 * provides a constructor that loads data from a file in PAML's unique
 * pairwise output format.
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

public class PairwiseSummaryTableModel extends SummaryTableModel{
	
	// the names displayed in the column headers in the table
	static String[] COLUMN_NAMES = {"Dataset", "Likelihood Score", "Tree Length", KAPPA, OMEGA, "dN", "dS"};

	// the names used as column headers when the table is saved as tab-delimited text
	static String[] TEXT_COLUMN_NAMES = {"Dataset", "Likelihood Score", "Tree Length", "Kappa", "Omega", "dN", "dS"};

	// This hash makes it easy to map column names to numerical indices.
	static HashMap<String, Integer> COLUMN_INDICES = new HashMap<String, Integer>();

	// A static initializer is used to populate the column name -> column index map.
	static{
		for (int i = 0; i < COLUMN_NAMES.length; i++){
			COLUMN_INDICES.put(COLUMN_NAMES[i], new Integer(i));
		}
	}

	// COLUMN_NAMES, TEXT_COLUMN_NAMES and COLUMN_INDICES are conceptually constants which vary by subclass.
	// To achieve the correct inheritance behavior, they must be declared as methods rather than fields.
	// These methods are implemented identically for all subclasses, but they refer to each class's unique fields.
	String[] COLUMN_NAMES(){
		return COLUMN_NAMES;
	}

	String[] TEXT_COLUMN_NAMES(){
		return TEXT_COLUMN_NAMES;
	}

	HashMap<String, Integer> COLUMN_INDICES(){
		return COLUMN_INDICES;
	}

	/**
	 * This creates a <code>PairwiseSummaryTableModel</code> from data in files in the specified directory.
	 * It is called by the <code>load</code> method in <code>SummaryTableModel</code> when pairwise data is detected.
	 *
	 * @param dirName the directory containing the .mlc files to use
	 * @param mlcFiles the pre-sorted list of .mlc files in the directory
	 * @param progress a progress bar to update as loading progesses; may be null
	 *
	 * @throws IOException if an output file is deleted while data is being loaded or an I/O error occurs
	 */
	public PairwiseSummaryTableModel(String dirName, File[] mlcFiles, SwingThreadSafeComponent<JProgressBar> progress)
		throws IOException{
		super();  // Call the constructor for AbstractTableModel.
		directoryContainingOutputFiles = dirName;

		// Parse each .mlc file and insert the desired data into the table model.
		FileParser mlcFileParser;
		Pattern likelihoodLinePattern = Pattern.compile("lnL\\s*=\\s*(\\-?\\d+\\.\\d+)");
		Pattern pairwiseDataLinePattern = Pattern.compile("t=\\s*\\d+\\.\\d+\\s+S=\\s*\\d+\\.\\d+\\s+N=\\s*\\d+\\.\\d+\\s+dN\\/dS=\\s*\\d+\\.\\d+\\s+dN=\\s*(\\d+\\.\\d+)\\s+dS=\\s*(\\d+\\.\\d+)");
		for (int i = 0; i < mlcFiles.length; i++){
			String filename = mlcFiles[i].getName();
			String datasetName = filename.substring(0, filename.lastIndexOf("."));
			LinkedList<Vector<Object>> dataForDataset = new LinkedList<Vector<Object>>();
			mlcFileParser = new FileParser(mlcFiles[i].getAbsolutePath());
			String nextLine;
			boolean acceptTreeLengthLine = false;
			Vector<Object> dataForModel = null;
			try{
				LINE: while (true){
					nextLine = mlcFileParser.nextLine();
					if (acceptTreeLengthLine){
						// We expect the line with the tree length, kappa and omega to immediately follow the likelihood line.
						String[] unlabeledValues = nextLine.split("\\s+");
						dataForModel.add(unlabeledValues[1]);
						dataForModel.add(unlabeledValues[2]);
						dataForModel.add(unlabeledValues[3]);
						acceptTreeLengthLine = false;
						continue LINE;
					}
					Matcher likelihoodLinePatternMatcher = likelihoodLinePattern.matcher(nextLine);
					if (likelihoodLinePatternMatcher.find()){
						// The line with the lnL score should be the first of the required lines found, so create the vector here.
						dataForModel = new Vector<Object>(9);
						dataForModel.add(datasetName);
						dataForModel.add(likelihoodLinePatternMatcher.group(1));
						acceptTreeLengthLine = true;
						continue LINE;
					}
					Matcher pairwiseDataLinePatternMatcher = pairwiseDataLinePattern.matcher(nextLine);
					if (pairwiseDataLinePatternMatcher.lookingAt()){
						// The line with dN and dS should come somewhere after the tree length line.
						dataForModel.add(pairwiseDataLinePatternMatcher.group(1));
						dataForModel.add(pairwiseDataLinePatternMatcher.group(2));
						continue LINE;
					}
				}
			}
			catch (EOFException eofe){
				if (dataForModel != null){  // Add the sole `model' vector to this dataset's vector.
					dataForDataset.add(dataForModel);
				}
			}
			mlcFileParser.closeFile();
			data.add(dataForDataset);
		}
		int maxColumns = 0;
		DATASET: for (LinkedList<Vector<Object>> datasetData : data){
			for (Vector<Object> modelData : datasetData){
				maxColumns = Math.max(maxColumns, modelData.size());
				if (maxColumns > 3){
					break DATASET;
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
		if (progress != null){
			progress.callSafeMethod("setValue", ((Integer) progress.callSafeMethod("getValue")) + 2);
		}
	}

	/**
	 * The <code>numericalValuesInColumn</code> method returns a linked list
	 * of the values in the requested column (which should be a numeric column).
	 * This is useful for making histograms.
	 * In <code>PairwiseSummaryTableModel</code>'s implementation of this method,
	 * the <code>model</code> parameter is ignored.
	 *
	 * @param column the requested column
	 * @param model ignored in <code>PairwiseSummaryTableModel</code>'s implementation
	 * @return a linked list of values in the requested column
	 */
	public LinkedList<Double> numericalValuesInColumn(int column, String model){
		LinkedList<Double> rv = new LinkedList<Double>();
		for (LinkedList<Vector<Object>> datasetData : data){
			for (Vector<Object> modelData : datasetData){
				rv.add(new Double((String) modelData.get(column)));
			}
		}
		return rv;
	}

	/**
	 * <code>PairwiseSummaryTableModel</code>'s implementation of <code>highlightRowBasedOnLikelihood</code>
	 * always returns false.
	 *
	 * @param row the index of the row whose likelihood-based highlighting status is to be determined
	 *
	 * @return false
	 */
	public boolean highlightRowBasedOnLikelihood(int row){
		return false;
	}

	/**
	 * <code>PairwiseSummaryTableModel</code>'s implementation of <code>displayTreeButtonInColumn</code>
	 * always returns false.
	 *
	 * @param column the index of the column whose tree-button status is to be determined
	 *
	 * @return false
	 */
	public boolean displayTreeButtonInColumn(int column){
		return false;
	}

	/**
	 * Row selection is indicated on the table by displaying an outline around all or some of the
	 * row's cells.  The <code>leftMostColumnToDisplayAsSelected</code> method returns the index of the
	 * leftmost column to be included in the outlined area when a row is selected.
	 * <code>PairwiseSummaryTableModel</code>'s implementation returns 0 because in pairwise tables,
	 * no columns contain values that span multiple rows.
	 *
	 * @return the index of the Model column
	 */
	public int leftmostColumnToDisplayAsSelected(){
		return 0;
	}

	/**
	 * The <code>buttons</code> method returns a linked list containing the string names of the buttons
	 * that should be displayed in the specified column's header.  The column names are associated with
	 * the names of methods to be activated when the buttons are clicked.  See class <code>SummaryTableButton</code>.
	 *
	 * @param column the index of the column whose button names are to be listed
	 *
	 * @return a linked list of the names of the buttons that should be displayed in the specified column's header
	 */
	public LinkedList<String> buttons(int column){
		LinkedList<String> rv = new LinkedList<String>();
		rv.add("Sort Ascending Global");
		rv.add("Sort Descending Global");
		if (! getColumnName(column).equals("Dataset")){
			rv.add("Show Histogram");
		}
		return rv;
	}


	// BUTTON METHOD

	/**
	 * The <code>showHistogram</code> method is called when the user requests a histogram.
	 * <code>PairwiseSummaryTableModel</code>'s implementation brings up a <code>HistogramWindow</code>.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param column the column for which the histogram is requested
	 * @param ownerWindow the owner for the <code>HistogramWindow</code> to be displayed in response
	 *
	 */
	public void showHistogram(int column, JFrame ownerWindow){
		HistogramWindow hw = new HistogramWindow(ownerWindow, this, column, null);
		hw.setVisible(true);
	}

}
