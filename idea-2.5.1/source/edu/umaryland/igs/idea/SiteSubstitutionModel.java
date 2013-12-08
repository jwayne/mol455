package edu.umaryland.igs.idea;

import java.util.Arrays;

/**
 * The <code>SiteSubstitutionModel</code> class represents one of codeml's site models.  The class's primary purpose is
 * to provide methods for converting between representations of the models.
 *
 * <p>Written:
 *
 * <p>Copyright (C) 2007, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 */

public class SiteSubstitutionModel{

	// a list of known site model names
	static final String[] SHORT_MODEL_NAMES = {"one-ratio", "NearlyNeutral", "PositiveSelection", "discrete", "freqs",
																						 "gamma", "2gamma", "beta", "beta&w>1", "beta&gamma",
																						 "beta&gamma+1", "beta&normal>1", "0&2normal>0", "3normal>0"};
	private int modelNumber;  // the number of this model

	/**
	 * This constructs an object representing the site model with the different number.
	 * It throws an <code>IllegalArgumentException</code> if the supplied number is invalid.
	 *
	 * @param modelNumber the number of the model to create
	 */
	public SiteSubstitutionModel(int modelNumber){
		setModelNumber(modelNumber);
	}

	/**
	 * This sets the <code>modelNumber</code> field to the specified number or throws
	 * an <code>IllegalArgumentException</code> if the supplied number is invalid.
	 *
	 * @param modelNumber the model number to attempt to set for this <code>SiteSubstitutionModel</code>
	 */
	private void setModelNumber(int modelNumber){
		if ((modelNumber >= SHORT_MODEL_NAMES.length) || (modelNumber < 0)){
			throw new IllegalArgumentException("Unknown Model:  " + modelNumber);
		}
		this.modelNumber = modelNumber;
	}

	/**
	 * This constructor creates a <code>SiteSubstitutionModel</code> from any of various string representations of a model:
	 * a. the number itself as a string (e.g., "0")
	 * b. the model abbreviation (e.g., "M0")
	 * c. the short model name (e.g., "one-ratio")
	 * d. the full model name (e.g., "0-one-ratio")
	 *
	 * @param modelNameOrAbbreviation a <code>String</code> in any of the formats listed above
	 */
	public SiteSubstitutionModel(String modelNameOrAbbreviation){
		try{
			setModelNumber(Integer.parseInt(modelNameOrAbbreviation));
			return;
		}
		catch (NumberFormatException nfe){
			// The string contains nonnumeric characters; try to process it as a model abbreviation or model name.
		}
		if ((modelNameOrAbbreviation.length() == 2) && modelNameOrAbbreviation.startsWith("M")){
			try{
				setModelNumber(Integer.parseInt(modelNameOrAbbreviation.substring(1)));
				return;
			}
			catch (NumberFormatException nfe){
				// Let this on through so we can try to process it as a short model name.
			}
		}
		int arrayIndex = Arrays.asList(SHORT_MODEL_NAMES).indexOf(modelNameOrAbbreviation);
		if (arrayIndex == -1){
			// It's not a known short model name; see if it's a full model name.
			String[] fullModelNameSubparts = modelNameOrAbbreviation.split("\\-", 2);
			try{
				setModelNumber(Integer.parseInt(fullModelNameSubparts[0]));
				if (! SHORT_MODEL_NAMES[modelNumber].equals(fullModelNameSubparts[1])){
					throw new IllegalArgumentException("Unknown Model:  " + modelNameOrAbbreviation);
				}
			}
			catch (NumberFormatException nfe){
				throw new IllegalArgumentException("Unknown Model:  " + modelNameOrAbbreviation);
			}
		}
		else{
			// It's a short model name.
			setModelNumber(arrayIndex);
		}
	}

	/**
	 * The <code>modelNumber</code> method returns the number for this model.
	 *
	 * @return the number for this model
	 */
	public int modelNumber(){
		return modelNumber;
	}

	/**
	 * The <code>modelNumberString</code> method returns the number for this model as a <code>String</code>.
	 *
	 * @return the number for this model as a <code>String</code>
	 */
	public String modelNumberString(){
		return Integer.toString(modelNumber);
	}

	/**
	 * For a given model number i, the <code>formattedLikelihoodIdentifier</code> method returns an HTML string
	 * which will appear in an HTML document as l_i (with l a cursive lowercase L and the underscore denoting a subscript).
	 *
	 * @return an HTML string which will be displayed as "cursive little-L sub-i"
	 */
	public String formattedLikelihoodIdentifier(){
		return "\u2113<sub><style font.size=\"80%\">" + modelNumber + "</style></sub>";
	}

	/**
	 * The <code>modelAbbreviation</code> method returns a <code>String</code> consisting of the model number prepended
	 * by "M".
	 *
	 * @return "Mi" for a given model number i
	 */
	public String modelAbbreviation(){
		return "M" + modelNumber;
	}

	/**
	 * The <code>shortModelName</code> method returns the unnumbered model name associated with the given model number.
	 *
	 * @return the unnumbered model name for this number
	 */
	public String shortModelName(){
		return SHORT_MODEL_NAMES[modelNumber];
	}

	/**
	 * The <code>fullModelName</code> method returns a <code>String</code> of the form "Mi-shortname".
	 * 
	 * @return "Mi-shortname" for a given model number i with the unnumbered name "shortname"
	 */
	public String fullModelName(){
		return modelNumber + "-" + SHORT_MODEL_NAMES[modelNumber];
	}

	/**
	 * The <code>equlas</code> method of class <code>SiteSubstitutionModel</code> returns true if the other object
	 * is a <code>SiteSubstitutionModel</code> with the same model number.
	 *
	 * @param o the other object with which to test this <code>SiteSubstitutionModel</code> for equality
	 *
	 * @return true <b><i>iff</i></b> o is another <code>SiteSubstitutionModel</code> with the same model number
	 */
	public boolean equals(Object o){
		if (o instanceof SiteSubstitutionModel){
			SiteSubstitutionModel other = (SiteSubstitutionModel) o;
			return modelNumber == other.modelNumber;
		}
		return false;
	}

	/**
	 * The <code>hashCode</code> method for class <code>SiteSubstitutionModel</code> simply returns the model number.
	 *
	 * @return the model number
	 */
	public int hashCode(){
		return modelNumber;
	}

	/**
	 * <code>SiteSubstitutionModel</code>'s implementation of <code>toString</code> returns a String of the form "Model i"
	 * for a given model number i.
	 * 
	 * @return "Model i" for a given model number i.
	 */
	public String toString(){
		return "Model " + modelNumber;
	}

}
