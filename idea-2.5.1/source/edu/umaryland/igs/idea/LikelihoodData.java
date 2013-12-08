package edu.umaryland.igs.idea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The class <code>LikelihoodData</code> encapsulates the results of a likelihood ratio test and the data used to arrive
 * at those results.  A <code>LikelihoodData</code> object may also represent the likelihood score of a model which did
 * not serve as an alternative model in an LRT, in which case the <code>tested</code> field should be false.
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

public class LikelihoodData implements CustomRenderable{

	double alternativeModelLikelihoodScore;  // the likelihood score on which these LRT results are based
	boolean tested;  // whether an LRT was conducted
	double nullModelLikelihoodScore;  // the score of the null model compared to the alternative model in the LRT
	double testStatistic;  // the test statistic compared to chi^2 in the LRT
	double pValue;  // the P-value of the test-statistic
	boolean significant;  // true <b><i>iff</i></b> the P-value is significant at 5%
	SiteSubstitutionModel alternativeModel;  // the alternative model in the LRT
	SiteSubstitutionModel nullModel;  // the null model in the LRT

	/**
	 * This constructs a <code>LikelihoodData</code> representing the likelihood score of a model which did not serve as
	 * the alternative model in an LRT.
	 *
	 * @param alternativeModelLikelihoodScore the likelihood score to represent as a degenerate <code>LikelihoodData</code>
	 */
	public LikelihoodData(double alternativeModelLikelihoodScore){
		this(alternativeModelLikelihoodScore, false, 0.0, 0.0, 0.0, false, null, null);
	}

	/**
	 * This convenience constructor converts its <code>String> argument to a <code>double</code>.
	 *
	 * @param amlsString a String representation of a likelihood score meant to be a degenerate <code>LikelihoodData</code>
	 */
	public LikelihoodData(String amlsString){
		this(Double.parseDouble(amlsString));
	}

	/**
	 * This constructs a full-fledged <code>LikelihoodData</code> object representing an actual LRT.
	 * The <code>tested</code> field is automatically set to true since this is implied when the seven-arg constructor
	 * is called.
	 *
	 * @param amls the likelihood score on which these LRT results are based
	 * @param nmls the score of the null model compared to the alternative model in the LRT
	 * @param ts the test statistic compared to chi^2 in the LRT
	 * @param pv the P-value of the test-statistic
	 * @param s whether the P-value is significant at 5%
	 * @param am the alternative model in the LRT
	 * @param nm the null model in the LRT
	 */
	public LikelihoodData(double amls, double nmls, double ts, double pv,
												boolean s, SiteSubstitutionModel am, SiteSubstitutionModel nm){
		this(amls, true, nmls, ts, pv, s, am, nm);
	}

	/**
	 * The private eight-arg constructor is called by the seven-arg constructor.
	 *
	 * @param amls the likelihood score on which these LRT results are based
	 * @param t  whether an LRT was conducted
	 * @param nmls the score of the null model compared to the alternative model in the LRT
	 * @param ts the test statistic compared to chi^2 in the LRT
	 * @param pv the P-value of the test-statistic
	 * @param s whether the P-value is significant at 5%
	 * @param am the alternative model in the LRT
	 * @param nm the null model in the LRT
	 */
	private LikelihoodData(double amls, boolean t, double nmls, double ts,
												 double pv, boolean s, SiteSubstitutionModel am, SiteSubstitutionModel nm){
		alternativeModelLikelihoodScore = amls;
		tested = t;
		nullModelLikelihoodScore = nmls;
		testStatistic = ts;
		pValue = pv;
		significant = s;
		alternativeModel = am;
		nullModel = nm;
	}

	/**
	 * The <code>likelihoodScore</code> method returns the likelihood score of the alternative model in the LRT or,
	 * in the case of a degenerate <code>LikelihoodData</code> object, the likelihood score the object represents.
	 *
	 * @return the alternative model's lnL or the lnL of a model which did not serve as an alternative model
	 */
	public double likelihoodScore(){
		return alternativeModelLikelihoodScore;
	}

	/**
	 * The <code>toString</code> method of a <code>customRenderable</code> class should return a string representation
	 * of an object suitable for printing in a table cell.  <code>LikelihoodData</code>'s implementation simply returns
	 * the lnL score of the alternative model (or the lnL score represented by a degenerate <code>LikelihoodData</code>
	 * object) as a <code>String</code>.
	 *
	 * @return the alternative model's lnL (or the lnL represented by a degenerate object) as a <code>String</code>
	 */
	public String toString(){
		return Double.toString(alternativeModelLikelihoodScore);
	}

	/**
	 * The static method <code>htmlFormat</code> converts a given <code>String</code> into one which, when inserted into
	 * HTML, will be displayed as that string.
	 *
	 * @param s a <code>String</code> to be converted to an HTML-friendly format
	 *
	 * @return an HTML-friendly version of the supplied string
	 */
	static String htmlFormat(String s){
		return s.replace("&", "&#38;").replace(">", "&#62;").replace("<", "&#60;").replace(" ", "&#160;");
	}

	/**
	 * The <code>details</code> method returns an HTML document, to be displayed as a tool tip for this object's
	 * custom rendering, as a <code>String</code>.
	 *
	 * @return a <code>String</code> containing an HTML document to be added as a tool tip to this object's rendering
	 */
	public String details(){
		String nmScoreString = Double.toString(nullModelLikelihoodScore);
		String l_A = alternativeModel.formattedLikelihoodIdentifier();
		String l_N = nullModel.formattedLikelihoodIdentifier();
		String[][] columns = {{"Hypothesis", "Alternative", "Null"},
													{"Model", htmlFormat(alternativeModel.fullModelName()), htmlFormat(nullModel.fullModelName())},
													{"lnL", l_A + " = " + toString(), l_N + " = " + nmScoreString}};
		StringWriter detailsWriter = new StringWriter();
		PrintWriter detailsPrinter = new PrintWriter(detailsWriter);
		detailsPrinter.print("<html><head><style>body {color: #006050} body {background-color: #90E8E0}</style></head><body><table width=\"100%\">");
		for (int r = 0; r < columns[0].length; r++){
	    detailsPrinter.print("<tr" + ((r == 0) ? " bgColor=\"#30C0A4\"" : "") + ">");
	    for (int c = 0; c < columns.length; c++){
				String styleTag = (r == 0) ? "</b>" : "";
				String codeLetter = (r == 0) ? "h" : "d";
				detailsPrinter.println("<t" + codeLetter + ((r == 0) ? " align=\"left\"" : "") + ">"
															 + ((c > 0) ? "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" : "") + columns[c][r]
															 + "</t" + codeLetter + ">");
	    }
	    detailsPrinter.println("</tr>");
		}
		detailsPrinter.print("</table><br>");
		String colorString = significant ? " bgColor=\"#A8E8A8\"" : "";
		detailsPrinter.print("<table width=\"100%\"><tr" + colorString
												 + " height=\"34%\"><td><b>Test Statistic:</b>&nbsp;&nbsp;2\u0394\u2113 = 2(" + l_A + " \u2212 "
												 + l_N
												 + ") \u2243 <font face=\"Symbol\"><i>\u03c7</i></font><sup><style font.size=\"80%\">2</style></sup> with 2 d.f.");
		detailsPrinter.printf("%s%s%s%s%s%2.6f",
													"</td></tr><tr" + colorString + "  height=\"33%\"><td>2(",
													toString(),
													" \u2212 ",
													nmScoreString,
													") = ",
													testStatistic);
		String pTag = significant ? "</b></font>" : "";
		detailsPrinter.printf("%s%s%1.9f%s",
													"</td></tr><tr" + colorString + " height=\"33%\"><td><font face=\"serif\"><i>p</i></font> = ",
													pTag.replace("/", "").replace("font", "font color=\"#00D0A0\""),
													pValue,
													pTag);
		detailsPrinter.println("</p></td></tr></table></body></html>");
		return detailsWriter.toString();
	}

	/**
	 * <code>LikelihoodData</code>'s implementation of <code>customRendering</code> returns a a label with a check or X
	 * icon.  Mousing over this icon activates a tool tip containing the HTML document returned by <code>details</code>.
	 *
	 * @return an icon conveying the results of the LRT with a tool tip containing the details of the LRT computation
	 */
	public Component customRendering(){
		JLabel lrtLabel =
	    new JLabel(new ImageIcon(ClassLoader.getSystemResource((tested && significant) ? "check.png" : "x.png")));
		lrtLabel.setToolTipText("");
		return tested ? lrtLabel : Box.createRigidArea(lrtLabel.getPreferredSize());
	}

}
