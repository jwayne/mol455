package edu.umaryland.igs.idea;

import java.io.File;

/**
 * Class <code>DatasetListVerifier</code> is a <code>FileFormatVerifier</code> for the
 * dataset list used in multi-dataset mode.  A dataset list should have one sequence
 * filename per line.  It may optionally list tree filenames in a second column,
 * separated from the sequence filenames by a tab.  Filenames may be either absolute
 * or relative paths, and relative paths may be to files in subdirectories.
 * Individual filenames are validated for length.
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

public class DatasetListVerifier extends FileFormatVerifier{

	DatasetVerifier sequenceVerifier, treeVerifier;  // length-limiting verifiers for listed sequence and tree filenames

	/**
	 * This is the only constructor for <code>DatasetListVerifier</code>.
	 *
	 * @param config the state on which to base validation
	 */
	public DatasetListVerifier(IDEAConfiguration config){
		super(config);
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
		DatasetListVerifier rv = new DatasetListVerifier(currentState);
 		rv.sequenceVerifier = (DatasetVerifier) rv.sequenceVerifier.clone();
 		rv.treeVerifier = (DatasetVerifier) rv.treeVerifier.clone();
		return rv;
	}

	/**
	 * The <code>createDatasetVerifiers</code> method creates the sequence and tree verifiers, which are instances of
	 * anonymous subclasses of class <code>DatasetVerifier</code> (defined below) instantiated with the specified
	 * dataset list filename.  They impose length limits on the sequence and tree filenames, respectively, based on both
	 * the current omega values and either the current input directory, the current dataset list filename's location or
	 * the directry from which IDEA was invoked.
	 *
	 * @param datasetListName the name of the dataset list, whose parent directory may be prepended to filenames
	 */
	private void createDatasetVerifiers(String datasetListName){
		sequenceVerifier = new DatasetVerifier(datasetListName, currentState){
				public int maxAbsolutePathLength(){
					return Math.max(103 - maxCharsForOmegaValue(), 0);
				}
				public int maxRelativePathLength(){
					return Math.max(100 - maxCharsForOmegaValue(), 0);
				}
			};
		treeVerifier = new DatasetVerifier(datasetListName, currentState){
				public int maxAbsolutePathLength(){
					return 114;
				}
				public int maxRelativePathLength(){
					return 114;
				}
			};
	}

	public boolean verifyText(String text){
		// 1. Make sure the dataset list file exists.
		if (! (new FileAvailabilityVerifier(currentState).verifyText(text))){
			return false;
		}

		// 2. Create verifiers for the individual files based on the now-confirmed-valid dataset list filename.
		createDatasetVerifiers(text);

		// 3. Call the superclass (FileFormatVerifier) method to check the files content's.
		// That method calls the abstract method verify(String, int), which in this class is defined
		// to make use of the individual-file verifiers that were initialized in step 2.
		return super.verifyText(text);
	}

	/**
	 * The<code>verify</code> method for <code>DatasetListVerifier<code>
	 * returns whether a line in the file is in the expected dataset-list format.
	 *
	 * @param line the line (string) to verify
	 * @param lineNumber the number of the line in the file; unused in this subclass
	 *
	 * @return whether this line contains a valid sequence file name and a valid tree file name separated by whitespace
	 */
	public boolean verify(String line, int lineNumber){
		String[] lineSubparts = line.split("\\s");
		if ((lineSubparts.length != 1) && (lineSubparts.length != 2)){
			errorMessage = "Improperly formatted line:  " + line;
			return false;
		}
		if (! sequenceVerifier.verifyText(lineSubparts[0])){
			errorMessage = sequenceVerifier.errorMessage(lineSubparts[0]);
			return false;
		}
		if ((lineSubparts.length == 2) && (! treeVerifier.verifyText(lineSubparts[1]))){
			errorMessage = treeVerifier.errorMessage(lineSubparts[1]);
			return false;
		}
		return true;
	}

	/**
	 * The <code>errorMessage</code> method is declared by <code>TextFieldInputVerifier</code>.
	 * The message returned by <code>DatasetListVerifier</code>'s implementation may indicate that a line is improperly
	 * formatted or that a sequence or tree filename listed in the file is too long.
	 *
	 * @param text the name of a dataset list file which was found to be invalid
	 *
	 * @return an error message explaining which line was the first to be invalid and why
	 */
	String errorMessage(String text){
		if ((sequenceVerifier != null) && (sequenceVerifier.errorMessage != null)){
			return sequenceVerifier.errorMessage;
		}
		else if ((treeVerifier != null) && (treeVerifier.errorMessage != null)){
			return treeVerifier.errorMessage;
		}
		else if (errorMessage != null){
			return errorMessage;
		}
		else{
			return fullFilename(text) + " is not available.";
		}
	}

}

/**
 * A dataset list's constituent sequence and tree filenames are validated by instances of anonymous subclasses of class
 * <code>DatasetVerifier</code>, which extends <code>OmegaBasedFilenameLengthLimiter</code>.  The anonymous subclasses
 * define <code>maxAbsolutePathLength</code> and <code>maxRelativePathLength</code> according to their individual needs
 * but share <code>DatasetVerifier</code>'s implementation of <code>relativeTo</code>.  The dataset list filename is
 * required as a parameter to the constructor because its parent directory is used as the assumed parent for sequence
 * and tree filenames given as relative paths if no input directory is currently set.
 */
abstract class DatasetVerifier extends OmegaBasedFilenameLengthLimiter{

	protected String datasetListName;  // the name of a dataset list file listing filenames on which this verifier operates

	/**
	 * In <code>DatasetVerifier</code>, a two-arg constructor replaces the one-arg constructor of
	 * <code>StateDependentInputVerifier</code> because the most recently chosen dataset list filename is required and,
	 * since it has not yet been validated, it is not available via <code>currentState</code>.
	 *
	 * @param datasetListName the name of a dataset list file listing filenames on which this verifier operates
	 * @param config the state on which to base validation
	 */
	public DatasetVerifier(String datasetListName, IDEAConfiguration config){
		super(config);
		this.datasetListName = datasetListName;
	}

	/**
	 * The <code>relativeTo</code> method is declared by <code>FileVerifier</code>.
	 * <code>DatasetVerifier</code>'s implementation returns the current value of the "Input directory" parameter, if it is
	 * not the empty string, or otherwise the directory containing or assumed to contain the dataset list file, if the
	 * value of the "Dataset name list" parameter is not the empty string, or otherwise the directory from which IDEA was
	 * invoked.
	 *
	 * @return in order of preference, the input directory, the dataset list's parent directory or the invocation directory
	 */
	public String relativeTo(){
		String inputDirectory = currentState.getParameter("Input directory").valueString();
		if (inputDirectory.equals("")){
			if (datasetListName.equals("")){
				return IDEAInputGUI.invocationDir;
			}
			return new File(datasetListName).getParent();
		}
		else{
			return inputDirectory;
		}
	}

}
