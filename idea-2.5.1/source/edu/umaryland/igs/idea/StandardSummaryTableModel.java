package edu.umaryland.igs.idea;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Vector;

import javax.swing.JFrame;

/**
 * <code>StandardSummaryTableModel</code> is the subclass of <code>SummaryTableModel</code> used for
 * tables derived from non-pairwise IDEA analyses (those with at least one dataset
 * having three or more sequences).  These analyses differ from pairwise analyses
 * in that there are multiple models for each dataset.  <code>StandardSummaryTableModel</code>
 * provides methods for sorting the models within datasets, determining the
 * likeliest model for a dataset and obtaining a list of the values in a numerical
 * column using only a given model or using the likeliest model for each dataset.
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

public class StandardSummaryTableModel extends SummaryTableModel{
	
	// This string constant can be given as a special model argument to <code>numericalValuesInColumn</code>.
	static final String LIKELIEST = "Likeliest for each dataset";

	// the names displayed in the column headers in the table
	static String[] COLUMN_NAMES = {"Dataset", "n", "Model", "Likelihood Score", "Tree Length", KAPPA, OMEGA};

	// the names used as column headers when the table is saved as tab-delimited text
	static String[] TEXT_COLUMN_NAMES = {"Dataset", "n", "Model", "Likelihood Score", "Tree Length", "Kappa", "Omega"};

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

	LinkedList<IOException> lrtExceptions;  // any exception thrown while parsing LRT results

	/**
	 * This creates a <code>StandardSummaryTableModel</code> for a table with no data, only column headers.
	 */
	public StandardSummaryTableModel(){
	}

	/** The <code>getModelNames</code> method returns a set of all the model names.
	 *
	 * @return a <code>LinkedHashSet</code> containing all the model names.
	 */
	public LinkedHashSet<String> getModelNames(){
		LinkedHashSet<String> rv = new LinkedHashSet<String>();
		for (int i = 0; i < getRowCount(); i++){
			String model = (String) getValueAt(i, COLUMN_INDICES.get("Model"));
			if (! model.equals("")){
				rv.add(model);  // Duplicates are not added.
			}
		}
		return rv;
	}

	/**
	 * The private method <code>likeliestModelForDataset</code> returns the string name
	 * of the model with the highest likelihood score among those in the given
	 * set of dataset data.
	 *
	 * @param datasetData the set of dataset data to search for the likeliest model
	 * @return the <code>String</code> name of the likeliest model, as printed in the table
	 */
	private String likeliestModelForDataset(LinkedList<Vector<Object>> datasetData){
		Comparator<Vector<Object>> likeliest = new Comparator<Vector<Object>>(){
			public int compare(Vector<Object> model1, Vector<Object> model2){
				int lhIndex = COLUMN_INDICES.get("Likelihood Score").intValue();
				return new Double(model1.get(lhIndex).toString()).compareTo(new Double(model2.get(lhIndex).toString()));
			}
		};
		String rv = null;
		try{
			rv =  (String) Collections.max(datasetData, likeliest).get(COLUMN_INDICES.get("Model").intValue());
		}
		catch (ArrayIndexOutOfBoundsException aioobe){  // Results are not available for this dataset.
			// Return null.
		}
		return rv;
	}

	/**
	 * The public method <code>likeliestModelForDataset</code> is a convenience method that
	 * returns the string name of the model with the highest likelihood score
	 * among those in the dataset containing the given row.
	 *
	 * @param row a row belonging to the dataset to search for the likeliest model
	 * @return the <code>String</code> name of the likeliest model, as printed in the table
	 */
	public String likeliestModelForDataset(int row){
		int rowsExamined = 0;
		for (LinkedList<Vector<Object>> datasetData : data){
			rowsExamined+= datasetData.size();
			if (row < rowsExamined){
				return likeliestModelForDataset(datasetData);
			}
		}
		throw new IllegalArgumentException("Row " + row + " is out of bounds, so no dataset corresponds to it.");
	}
			
	/**
	 * The <code>numericalValuesInColumn</code> method returns a linked list
	 * of the values in the requested column (which should be a numeric column).
	 * This is useful for making histograms.  Only values in rows matching the
	 * given model are included in the returned list.  If LIKELIEST is given
	 * as the model, the values for the likeliest model for each dataset are used.
	 *
	 * @param column the requested column
	 * @param model the model to restrict results to, or LIKELIEST
	 * @return a linked list of values in the requested column
	 */
	public LinkedList<Double> numericalValuesInColumn(int column, String model){
		LinkedList<Double> rv = new LinkedList<Double>();
		String modelToMatch = model;
		for (LinkedList<Vector<Object>> datasetData : data){
			if (model.equals(LIKELIEST)){
				modelToMatch = likeliestModelForDataset(datasetData);
			}
			MODEL: for (Vector<Object> modelData : datasetData){
				int i = 0;
				for (Object dataPoint : modelData){
					// If row modelData meets the criterion,
					// add (modelData, column) to the list.
					if (COLUMN_NAMES[i].equals("Model") && dataPoint.equals(modelToMatch)){
						rv.add(new Double(modelData.get(column).toString()));
						break MODEL;
					}
					i++;
				}
			}
		}
		return rv;
	}

	/**
	 * <code>StandardSummaryTableModel</code>'s implementation of <code>highlightRowBasedOnLikelihood</code>
	 * returns true if the specified row corresponds to the likeliest model for its dataset.
	 *
	 * @param row the index of the row whose likelihood-based highlighting status is to be determined
	 *
	 * @return true if the row corresponds to the likeliest model for its dataset; false otherwise
	 */
	public boolean highlightRowBasedOnLikelihood(int row){
		return getValueAt(row, COLUMN_INDICES.get("Model")).equals(likeliestModelForDataset(row));
	}

	/**
	 * <code>StandardSummaryTableModel</code>'s implementation of <code>displayTreeButtonInColumn</code>
	 * returns true for the Tree Length column only.
	 *
	 * @param column the index of the column whose tree-button status is to be determined
	 *
	 * @return true if the column's name is "Tree Length"; false otherwise
	 */
	public boolean displayTreeButtonInColumn(int column){
		return getColumnName(column).equals("Tree Length");
	}
	
	/**
	 * Row selection is indicated on the table by displaying an outline around all or some of the
	 * row's cells.  The <code>leftMostColumnToDisplayAsSelected</code> method returns the index of the
	 * leftmost column to be included in the outlined area when a row is selected.
	 * <code>StandardSummaryTableModel</code>'s implementation returns the index of the Model column
	 * because the columns to the left of it contain values that span multiple rows.
	 *
	 * @return the index of the Model column
	 */
	public int leftmostColumnToDisplayAsSelected(){
		return COLUMN_INDICES().get("Model");
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
		if (getColumnName(column).equals("Dataset") || getColumnName(column).equals("n")){
			rv.add("Sort Ascending Global");
			rv.add("Sort Descending Global");
		}
		else{
			rv.add("Sort Ascending");
			rv.add("Sort Descending");
			if (! (getColumnName(column).equals("Model"))){
				rv.add("Show Histogram");
			}
		}
		return rv;
	}


	// BUTTON METHODS

	/**
	 * The <code>showHistogram</code> method is called when the user requests a histogram.
	 * <code>StandardSummaryTableModel</code>'s implementation brings up a <code>HistogramDialog</code>,
	 * which allows the user to select a model on which to base the histogram.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param column the column for which the histogram is requested
	 * @param ownerWindow the owner for the <code>HistogramDialog</code> to be displayed in response
	 *
	 */
	public void showHistogram(int column, JFrame ownerWindow){
		HistogramDialog hd = new HistogramDialog(ownerWindow, this, column);
		hd.setVisible(true);
	}

	/** 
	 * The <code>sortAscending</code> method sorts within each dataset (rather than
	 * across datasets) in ascending order of the specified parameter.
	 * Numeric values appear in ascending order above string values, which appear
	 * in ascending order above non-numeric, non-string values.
	 * This method modifies the underlying data structure.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param columnIndex the index of the column on which to sort
	 * @param ownerWindow ignored; may be null  (This parameter exists for all button methods since some may require it.)
	 */
	public void sortAscending(final int columnIndex, JFrame ownerWindow){
		for (LinkedList<Vector<Object>> datasetData : data){
			Collections.sort(datasetData, new Comparator<Vector<Object>>(){
				public int compare(Vector<Object> model1, Vector<Object> model2){
					Double value1 = null;
					Double value2 = null;
					String stringValue1 = null;
					String stringValue2 = null;
					Object object1 = model1.get(columnIndex);
					Object object2 = model2.get(columnIndex);
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
	}

	/** 
	 * The <code>sortDescending</code> method sorts within each dataset (rather than
	 * across datasets) in descending order of the specified parameter.
	 * Numeric values appear in descending order above string values, which appear
	 * in descending order above non-numeric, non-string values.
	 * This method modifies the underlying data structure.
	 * NOTE:  This is a button method.  See class <code>SummaryTableButton</code>.
	 *
	 * @param columnIndex the index of the column on which to sort
	 * @param ownerWindow ignored; may be null  (This parameter exists for all button methods since some may require it.)
	 */
	public void sortDescending(final int columnIndex, JFrame ownerWindow){
		for (LinkedList<Vector<Object>> datasetData : data){
			Collections.sort(datasetData, new Comparator<Vector<Object>>(){
				public int compare(Vector<Object> model1, Vector<Object> model2){
					Double value1 = null;
					Double value2 = null;
					String stringValue1 = null;
					String stringValue2 = null;
					Object object1 = model1.get(columnIndex);
					Object object2 = model2.get(columnIndex);
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
							return -1;  // Since stringValue2 is numeric but stringValue1 isn't, object2 comes before object1.
						}
						catch (NumberFormatException nfe2){
							return stringValue2.compareTo(stringValue1);  // Sort strings in reverse alphanumeric (string) order.
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

}
