package edu.umaryland.igs.idea;

import java.io.File;

/**
 * <code>FilenameLengthLimiter</code> is an extension of <code>FileVerifier</code>
 * common to text fields which restrict user input based on a maximum filename length
 * imposed by PAML.  A default parent directory is assumed if a relative path is
 * given; the length of this parent directory's name is counted toward the maximum.
 * The maximum length for a relative path (including the parent directory) may differ
 * from that for an absolute path.
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

public abstract class FilenameLengthLimiter extends FileVerifier{

	/**
	 * This is the only constructor for <code>FilenameLengthLimiter</code>.
	 *
	 * @param config the state on which to base validation
	 */
	public FilenameLengthLimiter(IDEAConfiguration config){
		super(config);
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
		return (FilenameLengthLimiter) super.clone();
	}

	/**
	 * The <code>maxAbsolutePathLength()</code> method should return the maximum allowable length for an absolute path.
	 * This maximum may depend on other properties of the state.
	 *
	 * @return the maximum allowable length for an absolute path
	 */
	abstract int maxAbsolutePathLength();

	/**
	 * The <code>maxRelativePathLength()</code> method should return the maximum allowable length for a relative path.
	 * This maximum may depend on other properties of the state.
	 *
	 * @return the maximum allowable length for an relative path
	 */
	abstract int maxRelativePathLength();
	
	/**
	 * The <code>maxLength</code> method returns this verifier's maximum length for the supplied filename, which may be
	 * either an absolute or a relative path.
	 *
	 * @param filename an absolute or relative filename
	 */
	protected int maxLength(String filename){
		return new File(filename).isAbsolute() ? maxAbsolutePathLength() : maxRelativePathLength();
	}

	/**
	 * The <code>verifyText</code> method is declared by <code>TextFieldInputVerifier</code>.
	 * <code>FilenameLengthLimiter</code>'s version returns whether the length of the filename being validated
	 * is less than or equal to a verifier's maximum for it.  If the supplied filename is a relative path, the verifier's
	 * default parent is assumed, and the characters (if any) in its name are counted toward the maximum.
	 *
	 * @param text the text to validate, which should be an absolute or relative path to a (possibly nonexistent) file
	 *
	 * @return whether the filename's length is less than or equal to this verifier's maximum for it
	 */
	public boolean verifyText(String text){
		return fullFilename(text).length() <= maxLength(text);
	}
				
	/**
	 * The <code>errorMessage</code> method is declared by <code>TextFieldInputVerifier</code>.
	 * <code>FilenameLengthLimiter</code>'s version returns a string including the maximum number of allowed characters
	 * and the exact string that that exceeded that maximum.
	 *
	 * @param text the filename which, alone or when appended to an assumed parent directory, was found to be too long
	 *
	 * @return a message including the maximum characters allowed and the exact string which exceeded the maximum
	 */
	String errorMessage(String text){
		errorMessage = "The filename " + fullFilename(text) + " exceeds the maximum length of " + maxLength(text) + ".";
		return errorMessage;
	}

}
