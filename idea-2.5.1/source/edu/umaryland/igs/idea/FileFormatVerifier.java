package edu.umaryland.igs.idea;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import edu.umaryland.igs.aegan.utils.FileParser;

/**
 * <code>FileFormatVerifier</code> is an extension of <code>FileVerifier</code>
 * common to text fields which expect the name of a file that must have a particular format.
 * The simplest <code>FileFormatVerifier</code> is <code>FileAvailabilityVerifier</code>, which
 * simply requires that a file exist and be non-empty and readable.  Extended classes
 * implement verifyLine to define their own required file formats.
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

public abstract class FileFormatVerifier extends FileVerifier{

	/**
	 * This is the only constructor for <code>FileFormatVerifier</code>.
	 *
	 * @param config the state on which to base validation
	 */
	public FileFormatVerifier(IDEAConfiguration config){
		super(config);
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
		return (FileFormatVerifier) super.clone();
	}

	/**
	 * The method <code>verify</code> should return whether or not the line at the
	 * given position in the file matches the format expected of it.
	 * Subclasses should normally set errorMessage within this method.
	 *
	 * @param line the line (string) to verify
	 * @param lineNumber the number of the line in the file, starting with ONE
	 *
	 * @return whether line matches the expected format for the given line number
	 */
	public abstract boolean verify(String line, int lineNumber);

	/**
	 * Although this method is defined here, it may be overridden in subclasses.
	 */
	public String relativeTo(){
		return "";
	}

	/**
	 * The <code>verifyText</code> method is declared by <code>TextFieldInputVerifier</code>.
	 * <code>FileFormatVerifier</code>'s implementation returns false if the entry does not correspond to an existing file
	 * if the corresponding file is a directory, unreadable or empty, or if an I/O error occurs while parsing the file.
	 * Otherwise, it returns true if every line in the file is found to be valid and false if any line is found to be
	 * invalid.  Individual lines are validated by calling <code>verify</code>.  Since a file may be invalid for a number
	 * of distinct reasons, the <code>errorMessage</code> field is set so it can be returned by a later call to
	 * <code>errorMessage</code>.  Subclasses implementing <code>verify</code> should set <code>errorMessage</code> within
	 * that method in the event of failure.
	 *
	 * @param text the name of a file whose format is to be validated
	 * 
	 * @return whether the file exists as a readable, non-empty, non-directory file in the required format and can be parsed without an <code>IOException</code>
	 */
	public boolean verifyText(String text){
		String filename = fullFilename(text);
		File file = new File(filename);
		if (! file.exists()){
			errorMessage = filename + " does not exist.";
			return false;
		}
		if (! file.canRead()){
			errorMessage = filename + " is not readable.";
			return false;
		}
		if (file.isDirectory()){
			errorMessage = filename + " is a directory.";
			return false;
		}
		if (file.length() == 0L){
			errorMessage = "The file " + filename + " is empty.";
			return false;
		}
		try{
			FileParser fp = new FileParser(filename);
			int linesRead = 0;
			try{
				while (true){
					String nextLine = fp.nextLine();
					linesRead++;
					if (! verify(nextLine, linesRead)){
						return false;
					}
				}
			}
			catch (EOFException eofe){
				// This exception is expected; no action is taken.
			}
			return true;
		}
		catch (FileNotFoundException fnfe){
			errorMessage = filename + " could not be opened.";
			return false;
		}
		catch (IOException ioe){
			errorMessage = "I/O error reading " + filename + ":  " + ioe.getMessage();
			return false;
		}
	}
				
	/**
	 * The <code>errorMessage</code> method is declared by <code>TextFieldInputVerifier</code>.
	 * <code>FileFormatVerifier</code>'s implementation returns a message saved during the validation process indicative
	 * of the particular error that occurred or a default error message if a subclass has failed to provide an error
	 * message.
	 *
	 * @param text the name of the file which was found not to be in the required format
	 *
	 * @return an error message explaining the particular formatting or I/O error
	 */
	String errorMessage(String text){
		if (errorMessage != null){
			return errorMessage;
		}
		else{
			return fullFilename(text) + " is not available.";
		}
	}

}
