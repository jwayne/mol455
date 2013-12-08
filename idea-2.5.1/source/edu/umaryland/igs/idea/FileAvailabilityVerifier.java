package edu.umaryland.igs.idea;

/**
 * <code>FileAvailabilityVerifier</code>, the degenerate <code>FileFormatVerifier</code>,
 * simply requires that a file exist and be non-empty and readable.  There are no restrictions
 * on the files contents, but it may not be a directory.
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

public class FileAvailabilityVerifier extends FileFormatVerifier implements Cloneable{

	/**
	 * This is the only constructor for <code>FileAvailabilityVerifier</code>.
	 *
	 * @param config the state on which to base validation
	 */
	public FileAvailabilityVerifier(IDEAConfiguration config){
		super(config);
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
		FileAvailabilityVerifier rv = new FileAvailabilityVerifier(currentState);
		rv.errorMessage = (errorMessage == null) ? null : errorMessage.substring(0);
		// Don't copy currentState.
		return rv;
	}

	/**
	 * The<code>verify</code> method for <code>FileAvailabilityVerifier<code>
	 * simply returns true.
	 *
	 * @param line the line (string) to verify; unused in this subclass
	 * @param lineNumber the number of the line in the file; unused in this subclasss
	 *
	 * @return always true because this subclass imposes no format requirements
	 */
	public boolean verify(String line, int lineNumber){
		return true;
	}

}
