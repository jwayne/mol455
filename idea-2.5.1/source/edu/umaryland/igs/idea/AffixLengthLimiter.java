package edu.umaryland.igs.idea;

/**
 * <code>AffixLengthLimiter</code> is an extension of <code>StateDependentInputVerifier</code>
 * for text fields which are converted to filename affixes (prefixes, suffixes or infixes),
 * such as omega.  These affixes may affect the compliance of filename parameters with length
 * limits.
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

public class AffixLengthLimiter extends StateDependentInputVerifier implements Cloneable{

	protected String parameterName;  // the name of the parameter to which this verifier's parameter is to be affixed
	protected String affixName;  // the name of the affix parameter verified by this verifier
	protected Parameter filenameParameter; //the parameter to which this verifier's parameter is to be affixed; starts null

	/**
	 * This constructs a <code>AffixLengthLimiter</code> from the name of a filename parameter, the
	 * name of a parameter to be used as an affix for that parameter and the IDEA configuration
	 * reflecting the state of all parameters at the time of this <code>AffixLengthLimiter</code>'s
	 * creation.  Changes to parameters after this <code>AffixLengthLimiter</code>'s creation
	 * will be reflected in this class's <code>currentState</code> member.
	 *
	 * @param parameterName the name of a parameter to which another parameter will be affixed
	 * @param affixParameterName the name of a parameter to validate as an affix to another (typically filename) parameter
	 * @param config the state of all parameters
	 */
	public AffixLengthLimiter(String parameterName, String affixParameterName, IDEAConfiguration config){
		super(config);
		this.parameterName = parameterName;
		affixName = affixParameterName;
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
	  AffixLengthLimiter rv = new AffixLengthLimiter(parameterName, affixName, currentState);
		rv.errorMessage = (errorMessage == null) ? null : errorMessage.substring(0);
		// Don't copy currentState.
		rv.parameterName = (rv.parameterName == null) ? null : rv.parameterName.substring(0);
		rv.affixName = (rv.affixName == null) ? null : rv.affixName.substring(0);
		return rv;
	}

	/**
	 * The <code>verifyText</code> method for <code>AffixLengthLimiter</code> performs the validation
	 * required by the filename parameter based on a tentative update of the affix parameter to the value
	 * the user has entered.  If the user's entry is found to be invalid, the previous value for the affix
	 * parameter is restored.
	 *
	 * @param text the user's entry for the affix parameter
	 *
	 * @return whether the filename parameter is valid if the affix parameter is updated to the value the user has entered
	 */
	public boolean verifyText(String text){
		Object oldValue = currentState.getParameter(affixName).value;
		currentState.getParameter(affixName).updateValue(text);
		filenameParameter = currentState.getParameter(parameterName);
		boolean rv = (! filenameParameter.isEnabled()) || filenameParameter.valueIsValidOrEmpty();
		errorMessage = filenameParameter.getInputVerifier().errorMessage;
		if (! rv){
			currentState.getParameter(affixName).updateValue(oldValue);
		}
		return rv;
	}

	/**
	 * The <code>errorMessage</code> method for <code>AffixLengthLimiter</code> augments the error message returned by
	 * the filename parameter's validator with a message explaining the interaction between the two parameters.
	 *
	 * @param text the user's entry for the affix parameter
	 *
	 * @return the error message to be given when the user's entry is invalid
	 */
	String errorMessage(String text){
		String paramName = parameterName.toLowerCase();
		if (filenameParameter == null){
			return "The required parameter " + paramName + " is missing.";
		}
		Parameter affix = currentState.getParameter(affixName);
		Object oldValue = affix.value;
		affix.updateValue(text);
		String errorMessage = filenameParameter.getInputVerifier().errorMessage(filenameParameter.valueString());
		affix.updateValue(oldValue);
		return "Because this parameter (" + affixName + ") is used as an affix to the parameter "
			+ (parameterName.contains(" ") ? parameterName.toLowerCase() : parameterName)
			+ ", your entry (" + text + ") caused the following error:  " + errorMessage;
	}

}
