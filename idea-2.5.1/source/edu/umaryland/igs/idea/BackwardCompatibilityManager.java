package edu.umaryland.igs.idea;

import java.io.EOFException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The XStream library is used to perform XML serialization of configurations (saving in IDEA format).  It works via
// the same mechanisms that Java's object serialization works, but objects aren't required to implement Serializable.
// XStream allows private fields and anonymous classes to be converted to and from XML.
import com.thoughtworks.xstream.XStream;

import edu.umaryland.igs.aegan.utils.FileParser;

/**
 * <code>BackwardCompatibilityManager</code> contains static methods useful for reading configurations
 * saved using old versions of IDEA and initializing them to be compliant with the current code.
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

public class BackwardCompatibilityManager{

	/**
	 * The static method <code>backwardCompatibleStreamAndVersion</code> returns an array of two objects:  an
	 * XStream prepared to process the format of the version in the supplied filename (the first array element)
	 * and the version used to save that file (obtained by parsing the file) as a string (the second array element).
	 *
	 * @param filename the name of configuration file saved with this version of IDEA or a previous version
	 *
	 * @return an array of two objects:  {XStream for the version the file was saved with, String name of that version}
	 */
	public static Object[] backwardCompatibleStreamAndVersion(String filename){
		Object[] rv = new Object[2];
		XStream stream = new XStream();
		stream.registerConverter(new FaultTolerantFontConverter());
		String ideaVersion = IDEAConfiguration.IDEA_VERSION;
		try{
			FileParser fp = new FileParser(filename);
			Pattern ideaVersionLinePattern = Pattern.compile("\\<ideaVersion\\>([\\d\\.]+)\\<\\/ideaVersion\\>");
			try{
				while (true){
					String line = fp.nextLine();
					Matcher ideaVersionLinePatternMatcher = ideaVersionLinePattern.matcher(line);
					if (ideaVersionLinePatternMatcher.find()){
						ideaVersion = ideaVersionLinePatternMatcher.group(1);
						break;
					}
				}
			}
			catch (EOFException eofe){
				rv[0] = stream;
				rv[1] = "unknown";
				return rv;
			}
		}
		catch (IOException ioe){ // includes FileNotFoundException
			rv[0] = stream;
			rv[1] = "unknown";
			return rv;
		}
		if (ideaVersion.equals("2.1") || ideaVersion.equals("2.2") || ideaVersion.equals("2.2.1")){
			stream.alias("edu.umaryland.igs.idea.StandardFileParameter$1", StandardFileParameter$2.class);
			stream.alias("edu.umaryland.igs.idea.IDEAConfiguration$2", IDEAConfiguration$3.class);
			stream.alias("edu.umaryland.igs.idea.IDEAConfiguration$3", IDEAConfiguration$4.class);
			stream.alias("edu.umaryland.igs.idea.IDEAConfiguration$4", IDEAConfiguration$5.class);
			stream.alias("javax.swing.JRadioButton", LocalRadioButton.class);
		}
		rv[0] = stream;
		rv[1] = ideaVersion;
		return rv;
	}

	/**
	 * The static method <code>deriveFromOldVersion</code> initializes the supplied configuration
	 * as necessary to be compliant with the current version of IDEA, based on the
	 * assumption that it was loaded from a file saved with the supplied version of IDEA.
	 * 
	 * @param state the loaded configuration
	 * @param oldVersion the IDEA version that was used to save that configuration
	 */
	public static void deriveFromOldVersion(IDEAConfiguration state, String oldVersion){
		if (oldVersion.equals("2.1") || oldVersion.equals("2.2") || oldVersion.equals("2.2.1")){
			state.additionalParameters.renameParameter("Create treehorn", "Create tree (single-dataset mode)");
			state.getParameter("Email address (optional)").setMinTextAreaSize(19);
		}
	}

}
