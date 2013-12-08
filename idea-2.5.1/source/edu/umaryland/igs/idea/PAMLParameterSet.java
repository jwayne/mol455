package edu.umaryland.igs.idea;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import edu.umaryland.igs.aegan.utils.FileParser;
import org.tigr.antware.shared.exceptions.InvalidFileFormatException;

/**
 * <code>PAMLParameterSet</code> represents a grouping of PAML parameters.
 * There is only one <code>PAMLParameterSet</code> in an <code>IDEAConfiguration</code>,
 * and it contains only those parameters read from and written to PAML's .ctl file.
 * If the user wishes to save values for IDEA-specific parameters, the alternate .idea
 * format (which also incorporates the PAML parameters) must be used.
 * <code>PAMLParameterSet</code> overrides <code>ParameterSet</code> to provide methods
 * for parsing and writing .ctl files.
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

public class PAMLParameterSet extends ParameterSet{

	static final long serialVersionUID = 5542794971724313068L;

	/**
	 * The constructor for <code>PAMLParameterSet</code> parses the
	 * parameters from a .ctl file.
	 *
	 * @param codemlFilename the name of the .ctl file from which to read this parameter set
	 *
	 * @throws FileNotFoundException if the file is not found or a gunzip-related error occurs
	 * @throws InvalidFileFormatException if the file is not in the expected .ctl format
	 * @throws IOException if an I/O error occurs
	 */
	public PAMLParameterSet(String codemlFilename)
		throws FileNotFoundException, IOException, InvalidFileFormatException{
		super("PAML Parameters");  // The superclass constructor sets the name of the parameter set.
		FileParser parametersReader = new FileParser(codemlFilename);
		Parameter lastParameter = null;
		boolean readingGeneticCodes = false;
		try{
			while (true){
				String currentLine = parametersReader.nextLine();  // Get the next line from the file.
				String[] subparts = currentLine.split("\\s+");  // Split the line on whitespace.
				List<String> subpartsList = new LinkedList<String>(Arrays.asList(subparts));
				while (subpartsList.remove("")); // This loop removes all empty strings from the list.
				// end of short while loop
				if (subpartsList.size() == 0){ // Ignore empty lines.
					continue;
				}
				if (subpartsList.get(0).equals("*")){
					if (currentLine.contains(" = ")){
						continue;  // Ignore commented-out parameters while still paying attention to true comments.
					}
					String comment = getComment(currentLine);
					if (comment.startsWith("Genetic codes")){
						readingGeneticCodes = true;
					}
					if (readingGeneticCodes){
						// The explanation for the "icode" parameter is continued at the bottom of the file, so it must be
						// handled specially.
						lastParameter = parameters.get("icode");
					}
					if (lastParameter != null){
						if ((lastParameter.explanation == null)
							 || (lastParameter.name.equals("icode")
									 && (! lastParameter.explanation.textRepresentation().contains("Genetic codes")))){
							// Create a new parameter explanation based on the comment.
							lastParameter.explanation = new ParameterExplanation(comment);
						}
						else{
							// Add additional comment lines to the existing parameter explanation.
							lastParameter.explanation.addLine(comment);
						}
					}
// 					else{
// 					 (there is an unused comment)
// 					}
					continue;
				}
				if (subpartsList.size() < 3){
					throw new InvalidFileFormatException("Invalid line in " + codemlFilename + ":  [" + currentLine + "]");
				}

				// Parse the parameter name and value from the line.
				String currentParameterName = subpartsList.get(0);
				String currentComment = getComment(currentLine);
				List<String> valueSubparts;
				int firstIgnoredIndex = subpartsList.indexOf("*");
				if (firstIgnoredIndex == -1){
					firstIgnoredIndex = subpartsList.size();
				}
				valueSubparts = subpartsList.subList(2, firstIgnoredIndex);
				StringBuffer valueBuffer = new StringBuffer();
				for (String valueSubpart : valueSubparts){
					valueBuffer.append(valueSubpart).append(" ");
				}
				String valueSeparatedBySpaces = valueBuffer.toString();
				valueSeparatedBySpaces = valueSeparatedBySpaces.substring(0, valueSeparatedBySpaces.length() - 1);
				
				// Create a new parameter based on the name and value and using any comment as the explanation.
				Parameter currentParameter = null;
				if (currentParameterName.endsWith("file")){
					if (currentParameterName.equals("aaRatefile")){
						Vector<String> standardRateFiles = new Vector<String>();
						standardRateFiles.add("dat/dayhoff.dat");
						standardRateFiles.add("dat/jones.dat");
						standardRateFiles.add("dat/mtmam.dat");
						standardRateFiles.add("dat/wag.dat");
						currentParameter =
							new StandardFileParameter(currentParameterName,
																				valueSeparatedBySpaces,
																				(currentComment == null) ? null : new ParameterExplanation(currentComment),
																				standardRateFiles){
								// The valueString method is extended in this inner class to supply an absolute path.
								public String valueString(){
									String asSelected = super.valueString();
									if (! new File(asSelected).isAbsolute()){
										if (asSelected.startsWith("dat/")){
											asSelected = asSelected.substring(3);
										}
										StringBuffer retVal = new StringBuffer(IDEAInputGUI.PAML_BIN_DIR.toString());
										asSelected = retVal.append(asSelected).toString();
									}
									return asSelected;
								}
							};
					}
					else{
						currentParameter =
							new FileParameter(currentParameterName,
																valueSeparatedBySpaces,
																(currentComment == null) ? null : new ParameterExplanation(currentComment));
					}
				}
				else{
					currentParameter = new Parameter(currentParameterName,
																					 valueSeparatedBySpaces,
																					 (currentComment == null) ? null : new ParameterExplanation(currentComment));
				}
				parameters.put(currentParameterName, currentParameter);
				parameterList.add(currentParameterName);
				lastParameter = currentParameter;
			}
		}
		catch (EOFException eofe){
			// This exception is expected; no action is taken.
		}
	}

	/**
	 * The <code>save</code> method writes the <code>PAMLParameterSet</code>
	 * to the specified file.  The file is written in .ctl format.
	 * This method makes use of the one-arg <code>toString</code> method in <code>Parameter</code>.
	 *
	 * @param codemlFilename the name of the .ctl file to write
	 * @throws FileNotFoundException if the file is a directory, cannot be created or cannot be opened
	 */
	void save(String codemlFilename) throws FileNotFoundException{
		PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(codemlFilename)));
		for (Parameter parameter : getParameterList()){
			ps.println(parameter.toString(maxCombinedNameAndValueLength() + 4));
		}
		ps.close();
	}

	/**
	 * 
	 * The static method <code>getComment</code> returns the part of a .ctl-file line that is
	 * a comment (not including the *).  If the line contains no comment or a comment with only
	 * spaces, it returns null.
	 * 
	 * @param line the string from which to extract the comment
	 * 
	 * @return a <code>String</code> representing the part of the line that is a comment
	 */
	public static String getComment(String line) {
		String[] halves = line.split("\\*");
		if (halves.length > 1){
			String secondHalf = halves[1];
			if (secondHalf != null){
				while (secondHalf.startsWith(" ")){
					secondHalf = secondHalf.substring(1);
				}
				while (secondHalf.endsWith(" ")){
					secondHalf = secondHalf.substring(0, secondHalf.length() - 1);
				}
				if (! secondHalf.equals("")){
					return secondHalf;
				}
			}
		}
		return null;
	}

}
