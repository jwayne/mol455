package edu.umaryland.igs.idea;

/**
 * <code>OmegaBasedFilenameLengthLimiter</code> is an extension of <code>FilenameLengthLimiter</code>
 * common to length-limited filename fields whose contents will be converted to
 * filenames parameterized by user-specified omega values.
 *
 * <p>Written:
 *
 * <p>Copyright (C) 2007, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 *
 */

public abstract class OmegaBasedFilenameLengthLimiter extends FilenameLengthLimiter{

	/**
	 * This is the only constructor for <code>OmegaBasedFilenameLengthLimiter</code>.
	 *
	 * @param config the state on which to base validation
	 */
	public OmegaBasedFilenameLengthLimiter(IDEAConfiguration config){
		super(config);
	}

	/**
	 * The <code>maxCharsForOmegaValue</code> method returns the maximum number of characters required to represent any
	 * omega value in the current state (including the value for the parameter "omega" and all vaues for the parameter
	 * "Extra omega values") in a filename.
	 *
	 * @return the maximum characters necessary to represent any omega value in the current state in a filename
	 */
	protected int maxCharsForOmegaValue(){
		Parameter omega = currentState.pamlParameters.getParameter("omega");
		Parameter extraOmegaValues = currentState.additionalParameters.getParameter("Extra omega values");
		if ((omega == null) && (extraOmegaValues == null)){
			return 3;
		}
		if (extraOmegaValues == null){
			return charsForOmegaValue(omega.valueString());
		}
		int maxCharsForExtraOmegaValue = 0;
		for (String extraOmegaString : extraOmegaValues.valueString().split("\\s")){
			maxCharsForExtraOmegaValue = Math.max(maxCharsForExtraOmegaValue, charsForOmegaValue(extraOmegaString));
		}
		return Math.max(maxCharsForExtraOmegaValue, (omega == null) ? 3 : charsForOmegaValue(omega.valueString()));
	}

	/**
	 * The <code>charsForOmegaValue</code> method returns the number of characters the omega value represented in the
	 * specified string will take up in a filename, which may be one greater than the length of the string if a leading
	 * zero must be added.
	 *
	 * @param omegaValue a string representation of an omega value, as entered by the user
	 *
	 * @return the number of characters in a filename that must be devoted to representing that value
	 */
	private int charsForOmegaValue(String omegaValue){
		return omegaValue.length() + (omegaValue.startsWith(".") ? 1 : 0);
	}

}
