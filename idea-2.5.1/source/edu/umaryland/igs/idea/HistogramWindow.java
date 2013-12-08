package edu.umaryland.igs.idea;

import java.awt.Component;
import java.awt.Dimension;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.metal.MetalIconFactory;
import javax.swing.plaf.metal.MetalSliderUI;

import edu.umaryland.igs.aegan.utils.Histogram;
import edu.umaryland.igs.aegan.utils.WrapperPane;

/**
 * <code>HistogramWindow</code> displays a <code>Histogram</code> in a titled window
 * with a save button, radio buttons for changing the selected model and a slider
 * for changing the number of bins.
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
public class HistogramWindow extends JDialog{
	static final int HISTOGRAM_WIDTH = 400;  // the width of the internal panel on which the histogram is drawn
	static final int HISTOGRAM_HEIGHT = 400;  // the height of the internal bin on which the histogram is drawn
	static final int MIN_BINS = 2;  // the minimum number of bins the user may select
	static final int MAX_BINS = 50;  // the maximum number of bins the user may select
	static final int INITIAL_BINS = 5;    // the default number of bins

	protected SummaryTableModel resultSet;  // the result set from which to derive the histogram
	protected Histogram histogram;  // the histogram (includes drawing methods)
	protected JPanel contents;  // the panel holding the display components, including the histogram
	protected int selectedColumn;  // the column for which to display a histogram
	protected String selectedModel;  // the model on which to base the histogram
	protected String quantityName; // name of the quantity to be plotted, based on the column and model names
	protected JPanel histogramPanelPlaceholder = new JPanel();  // a placeholder used to improve the histogram's appearance
	protected JSlider numberOfBinsSlider;  // a slider which allows the user to select the number of bins
	protected int numberOfBins = INITIAL_BINS;  // the number of bins currently selected
	protected JPanel lastImageDrawn;  // the last histogram drawn; stored in case the user wants to save it to a file

	/**
	 * This is the only  constructor for <code>HistogramWindow</code>.
	 *
	 * @param owner the frame to which the window is attached
	 * @param rs the result set from which to derive the histogram
	 * @param column the column for which to display a histogram
	 * @param model the model on which to base the histogram
	 */
	public HistogramWindow(JFrame owner, SummaryTableModel rs, int column, String model){
		super(owner);
		setTitle(updateQuantityName(rs, column, model));
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		contents = new JPanel();
		contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
		contents.add(modelSelectionAndSavePanel());
		histogramPanelPlaceholder.add(histogramPanel());
		histogramPanelPlaceholder.setMaximumSize(histogramPanelPlaceholder.getPreferredSize());
		contents.add(histogramPanelPlaceholder);
		initializeSlider();
		contents.add(Box.createVerticalStrut(contents.getFont().getSize() * 2));
		contents.add(sliderPanel());
		add(contents);
		pack();
	}

	/**
	 * The <code>updateQuantityName</code> method updates the result set, column index and model name to the
	 * supplied values, updates the quantity name (which is to be plotted on the histogram) to
	 * one derived from those values and finally returns that derived quantity name.
	 *
	 * @param rs the result set from which to derive the histogram
	 * @param column the index of the column for which to display a histogram
	 * @param model the name of a model on which to base the histogram
	 *
	 * @return a label for the histogram's X axis, e.g., "Tree Length, Model 7-beta"
	 */
	private String updateQuantityName(SummaryTableModel rs, int column, String model){
		resultSet = rs;
		selectedColumn = column;
		selectedModel = model;
		quantityName = rs.getColumnName(column) + ((model == null) ? "" : (", Model " + model));
		return quantityName;
	}

	/**
	 * The <code>initializeSlider</code> method creates the number of bins slider, customizes its
	 * appearance and adds a listener that redraws the histogram when the user changes the number
	 * of bins.
	 */
	@SuppressWarnings("unchecked")
	protected void initializeSlider(){
		numberOfBinsSlider = new JSlider(JSlider.HORIZONTAL, MIN_BINS, MAX_BINS, INITIAL_BINS);
		numberOfBinsSlider.addChangeListener(new ChangeListener(){
				public void stateChanged(ChangeEvent e) {
					JSlider source = (JSlider)e.getSource();

					// Results are better if the event is responded to regardless of getValueIsAdjusting().
					//if (!source.getValueIsAdjusting()) {
					numberOfBins = (int) source.getValue();
					refreshHistogramPanel();
						//}
				}
			});
		numberOfBinsSlider.setMajorTickSpacing(5);
		numberOfBinsSlider.setMinorTickSpacing(1);
		numberOfBinsSlider.setPaintTicks(true);
		Hashtable labels = numberOfBinsSlider.createStandardLabels(5, 5);
		labels.put(new Integer(2), new JLabel("2"));
		numberOfBinsSlider.setLabelTable(labels);
		numberOfBinsSlider.setPaintLabels(true);
		numberOfBinsSlider.setUI(new HorizontalOffsetMetalSliderUI());
		numberOfBinsSlider.setSnapToTicks(true);
		numberOfBinsSlider.setMaximumSize(new Dimension(histogramPanelPlaceholder.getMaximumSize().width,
																											numberOfBinsSlider.getPreferredSize().height));
	}

	/**
	 * The <code>sliderPanel</code> method returns a panel containing the number of bins slider and a label.
	 * Currently, it does not contain a disclaimer about filtering since filtering is not supported.
	 *
	 * @return a panel containing the number of bins slider and a label
	 */
	protected JPanel sliderPanel(){
		JPanel rv = new JPanel();
		rv.setLayout(new BoxLayout(rv, BoxLayout.Y_AXIS));
		rv.add(new JLabel("Number of bins:"));
		numberOfBinsSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
		rv.add(numberOfBinsSlider);
		rv.add(Box.createVerticalStrut(contents.getFont().getSize() / 2));

		// For now, filtering is not supported, so don't display a disclaimer.

// 		JLabel disclaimer = new JLabel("<html>Note:  This histogram is based on all values of the selected parameter for the selected model<br>and is not affected by any filtering applied to the table.</html>");
// 		Font originalDisclaimerFont = disclaimer.getFont();
// 		disclaimer.setFont(new Font(originalDisclaimerFont.getName(), Font.PLAIN, 9));
// 		rv.add(disclaimer);

		rv.setAlignmentX(Component.CENTER_ALIGNMENT);
		return rv;
	}

	/**
	 * The <code>modelSelectionAndSavePanel</code> method returns a panel containing a drop-down box
	 * allowing the user to select a model and a Save button.  The Save button is currently disabled.
	 * Users may save the histogram image by extracting it from a captured screenshot.
	 *
	 * @return a panel containing a drop-down box allowing the user to select a model and a Save button
	 */
	protected JPanel modelSelectionAndSavePanel(){
		JPanel rv = new JPanel();
		if (resultSet instanceof StandardSummaryTableModel){
			rv.add(new JLabel("Model:"));
			JComboBox choices = new JComboBox();
			int i = 0;
			for (final String modelName : ((StandardSummaryTableModel) resultSet).getModelNames()){
				choices.addItem(modelName);
				if (modelName.equals(selectedModel)){
					choices.setSelectedIndex(i);
				}
				i++;
			}
			choices.addActionListener(new ActionListener(){
					public void actionPerformed(ActionEvent e){
						JComboBox cb = (JComboBox) e.getSource();
						selectedModel = (String) cb.getSelectedItem();
						setTitle(resultSet.getColumnName(selectedColumn) + ", " + selectedModel);
						refreshHistogramPanel();
					}
				});
			rv.add(choices);
		}
		JButton saveButton = new JButton("Save");
		saveButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					JFileChooser histogramFileChooser
						= new JFileChooser(resultSet.directoryContainingOutputFiles){
							public void setSelectedFile(File file){
								super.setSelectedFile(file);
								if (file == null){
									StackTraceElement[] stack = Thread.currentThread().getStackTrace();
									for (int i = 0; i < stack.length - 1; i++){
										if (stack[i].getClassName().startsWith("edu.umaryland.igs.idea")
												&& stack[i].getMethodName().equals("setSelectedFile")
												&& stack[i + 1].getClassName().endsWith("proveSelectionAction")
												&& stack[i + 1].getMethodName().equals("actionPerformed")){
											JOptionPane.showMessageDialog(HistogramWindow.this,
																										"You must select a file, not a directory.",
																										"Select Files Only",
																										JOptionPane.ERROR_MESSAGE);
											break;
										}
									}
								}
							}
						};
					histogramFileChooser.setAcceptAllFileFilterUsed(false);
					histogramFileChooser.setFileFilter(new FileFilter(){
							public String getDescription(){
								return "JPEG Files";
							}
							public boolean accept(File f){
								if (f.isDirectory()){
									return true;
								}
								String ucName = f.getName().toUpperCase();
								return ucName.endsWith(".JPG") || ucName.endsWith(".JPEG");
							}
						});
					histogramFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					histogramFileChooser.setDialogTitle("Save Histogram");
					int outcome = histogramFileChooser.showDialog(HistogramWindow.this, "Save");
					if (outcome == JFileChooser.APPROVE_OPTION){
						File selectedFile = histogramFileChooser.getSelectedFile();
						if (! histogramFileChooser.accept(selectedFile)){
							String selectedFilename = selectedFile.getName();
							if (! selectedFilename.contains(".")){
								selectedFile = new File(selectedFile.getAbsolutePath() + ".jpg");
							}
						}
						if (! histogramFileChooser.accept(selectedFile)){
							WrapperPane.showMessageDialog(HistogramWindow.this,
																						"The filename "
																						+ selectedFile.getName()
																						+ " is invalid.  All histograms are saved as JPEG files with extension .jpg or .jpeg.",
																						"Invalid Filename (Only JPEG Files Allowed)",
																						JOptionPane.ERROR_MESSAGE);
						}
						else{
							// Save the image here.
							Image histogramImage = lastImageDrawn.createImage(lastImageDrawn.getWidth(), lastImageDrawn.getHeight());
							lastImageDrawn.paint(histogramImage.getGraphics());
							try{
								ImageIO.write((RenderedImage) histogramImage, "JPEG", selectedFile);
							}
							catch (IOException ioe){
								JOptionPane.showMessageDialog(HistogramWindow.this,
																							"An I/O error occurred.\nThe file may not have been saved.",
																							"I/O Error",
																							JOptionPane.ERROR_MESSAGE);
							}
						}
					}
				}
			});
		rv.add(saveButton);
		return rv;
	}
	
	/**
	 * The <code>HistogramPanel</code> method creates a new histogram based on the selected model and
	 * returns the drawn histogram.
	 *
	 * @return a JPanel with the histogram drawn on it
	 */
	protected JPanel histogramPanel(){
		histogram = new Histogram(resultSet.numericalValuesInColumn(selectedColumn, selectedModel));
		lastImageDrawn = histogram.draw(numberOfBins, HISTOGRAM_WIDTH,
																		HISTOGRAM_HEIGHT, updateQuantityName(resultSet, selectedColumn, selectedModel));
		return lastImageDrawn;
	}
		
	/**
	 * The <code>refreshHistogramPanel</code> method recreates and redisplays the histogram.
	 */
	private void refreshHistogramPanel(){
		histogramPanelPlaceholder.removeAll();
		histogramPanelPlaceholder.add(histogramPanel());
		histogramPanelPlaceholder.invalidate();
		histogramPanelPlaceholder.revalidate();
	}

}

/**
 * <code>HorizontalOffsetMetalSliderUI</code> is a custom UI for the "Number of bins" slider
 * that paints major tick marks at the locations of its custom labels.
 */
class HorizontalOffsetMetalSliderUI extends MetalSliderUI{
	
	/**
	 * The <code>paintTicks</code> method is based on the method in <code>BasicSliderUI</code>.
	 * 
	 * @param g the graphics object on which ticks are drawn
	 */
	public void paintTicks(Graphics g){        
		Rectangle tickBounds = tickRect;
		int maj = slider.getMajorTickSpacing();
		int min = slider.getMinorTickSpacing();

		g.translate(0, tickBounds.y);

		int value = slider.getMinimum();
		int xPos = 0;

		if (min > 0){
			while (value <= slider.getMaximum()){
				xPos = xPositionForValue(value);
				paintMinorTickForHorizSlider(g, tickBounds, xPos);
				value += min;
			}
		}

		if (maj > 0){
			value = Math.min(0, slider.getMinimum());  // This is designed for sliders with minimum >= 0.
			while (value <= slider.getMaximum()){
				if (value >= slider.getMinimum()){
					xPos = xPositionForValue(value);
					paintMajorTickForHorizSlider(g, tickBounds, xPos);
				}
				value += maj;
			}
				
			// Paint major ticks at the minimum and maximum even if they aren't integer multiples of the major tick spacing.
			xPos = xPositionForValue(slider.getMinimum());
			paintMajorTickForHorizSlider(g, tickBounds, xPos);
			xPos = xPositionForValue(slider.getMaximum());
			paintMajorTickForHorizSlider(g, tickBounds, xPos);
		}
		g.translate(0, -tickBounds.y);
	}

	/**
	 * The <code>installUI</code> method is based on the method in <code>MetalSliderUI</code>.
	 * 
	 * @param c the component that is to use this UI
	 */
	public void installUI(JComponent c){
		UIManager.installLookAndFeel("metal", "javax.swing.plaf.metal.MetalLookAndFeel");
		if (UIManager.get("Slider.trackWidth") == null){
			UIManager.put("Slider.trackWidth", new Integer(7));
		}
		if (UIManager.get("Slider.majorTickLength") == null){
			UIManager.put("Slider.majorTickLength", new Integer(6));
		}
		UIManager.put("Slider.horizontalThumbIcon", MetalIconFactory.getHorizontalSliderThumbIcon());
		UIManager.put("Slider.verticalThumbIcon", MetalIconFactory.getVerticalSliderThumbIcon());
		horizThumbIcon = UIManager.getIcon("Slider.horizontalThumbIcon");
		vertThumbIcon = UIManager.getIcon("Slider.verticalThumbIcon");
		super.installUI(c);
	}

}
