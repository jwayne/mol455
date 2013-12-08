package edu.umaryland.igs.idea;

import java.io.File;

/**
 * <code>FileVerifier</code> is an abstract extension of <code>StateDependentInputVerifier</code>
 * common to filename text fields.  Subclasses may impose requirements on a file's name, availability and/or contents.
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

public abstract class FileVerifier extends StateDependentInputVerifier{

	/**
	 * This is the only constructor for <code>FileVerifier</code>.
	 *
	 * @param config the state on which to base validation
	 */
	public FileVerifier(IDEAConfiguration config){
		super(config);
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
		return (FileVerifier) super.clone();
	}

	/**
	 * The abstract method <code>relativeTo</code> should return a base path to which entries being validated are
	 * assumed to be relative.  The returned string may or may not end with a path separator.
	 *
	 * @return the base path to which entries being validated are assumed to be relative
	 */
	abstract String relativeTo();

	/**
	 * The <code>parent</code> method returns a <code>String</code> which may be prepended to the filename being validated
	 * to get the full path to the (possibly nonexistent) file as determined by the assumptions of the particular verifier.
	 * This string will be the empty string if non-absolute filenames validated by this verifier are assumed to be
	 * paths relative to the current execution directory.  Otherwise, it will always end with a path separator.
	 *
	 * @return a <code>String</code> which may be prepended to the filename being validated to get a full path to the file
	 */
	protected String parent(){
		String rv = relativeTo();
		if ((rv.length() > 0) && (! rv.endsWith(File.separator))){
			rv+= File.separator;
		}
		return rv;
	}

	/**
	 * The <code>fullFilename</code> method returns the full path corresponding to a filename entry.
	 *
	 * @param text an absolute or relative filename
	 *
	 * @return the absolute path to the file, based on the assumptions of the particular verifier
	 */
	protected String fullFilename(String text){
		return new File(text).isAbsolute() ? text : (parent() + text);
	}

}
