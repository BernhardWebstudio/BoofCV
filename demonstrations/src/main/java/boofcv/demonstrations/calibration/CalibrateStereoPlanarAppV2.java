/*
 * Copyright (c) 2021, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.demonstrations.calibration;

import boofcv.abst.geo.calibration.CalibrateStereoPlanar;
import boofcv.abst.geo.calibration.DetectSingleFiducialCalibration;
import boofcv.abst.geo.calibration.ImageResults;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.alg.geo.RectifyImageOps;
import boofcv.alg.geo.calibration.CalibrationObservation;
import boofcv.alg.geo.rectify.RectifyCalibrated;
import boofcv.gui.BoofSwingUtil;
import boofcv.gui.StandardAlgConfigPanel;
import boofcv.gui.calibration.UtilCalibrationGui;
import boofcv.gui.controls.CalibrationTargetPanel;
import boofcv.gui.controls.ControlPanelPinhole;
import boofcv.gui.controls.JCheckBoxValue;
import boofcv.gui.controls.JSpinnerNumber;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ScaleOptions;
import boofcv.gui.image.ShowImages;
import boofcv.gui.settings.GlobalSettingsControls;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.misc.BoofMiscOps;
import boofcv.misc.VariableLockSet;
import boofcv.struct.calib.StereoParameters;
import boofcv.struct.image.GrayF32;
import georegression.struct.se.Se3_F64;
import lombok.Getter;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I32;
import org.ejml.data.DMatrixRMaj;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static boofcv.gui.BoofSwingUtil.MAX_ZOOM;
import static boofcv.gui.BoofSwingUtil.MIN_ZOOM;

/**
 * Application that lets you change calibration and target settings and recalibrate stereo image sets.
 *
 * @author Peter Abeles
 */
public class CalibrateStereoPlanarAppV2 extends JPanel {

	protected @Nullable StereoImageSet inputImages;
	protected final Object lockInput = new Object();

	AlgorithmsLocked algorithms = new AlgorithmsLocked();
	ResultsLocked resultsLeft = new ResultsLocked();
	ResultsLocked resultsRight = new ResultsLocked();

	//--------------------- GUI owned thread
	public JMenuBar menuBar;
	protected JMenu menuRecent;
	public JFrame window;
	StereoCalibrationPanel stereoPanel = new StereoCalibrationPanel();
	protected @Getter ConfigureInfoPanel configurePanel = new ConfigureInfoPanel();
	protected CalibrationListPanel imageListPanel = createImageListPanel();
	//--------------------------------------------------------------------

	// True if a thread is running for calibration
	protected boolean runningCalibration = false;

	// Specifies if the user changed calibration settings
	boolean calibratorChanged = true;
	boolean targetChanged = true;
	// if true the landmarks have been modified and it should not display results
	boolean resultsInvalid;

	{
		BoofSwingUtil.initializeSwing();
	}

	public CalibrateStereoPlanarAppV2() {
		setLayout(new BorderLayout());

		stereoPanel.panelLeft.setScale = ( scale ) -> configurePanel.setZoom(scale);
		stereoPanel.panelRight.setScale = ( scale ) -> configurePanel.setZoom(scale);

		stereoPanel.setPreferredSize(new Dimension(1000, 720));

		createAlgorithms();
		add(imageListPanel, BorderLayout.EAST);
		add(configurePanel, BorderLayout.WEST);
		add(stereoPanel, BorderLayout.CENTER);

		createMenuBar();
	}

	protected void createMenuBar() {
		menuBar = new JMenuBar();

		JMenu menuFile = new JMenu("File");
		menuFile.setMnemonic(KeyEvent.VK_F);
		menuBar.add(menuFile);

		var menuItemFile = new JMenuItem("Open Images");
		BoofSwingUtil.setMenuItemKeys(menuItemFile, KeyEvent.VK_O, KeyEvent.VK_O);
		menuItemFile.addActionListener(( e ) -> openDialog());
		menuFile.add(menuItemFile);

		menuRecent = new JMenu("Open Recent");
		menuFile.add(menuRecent);
		updateRecentItems();

//		var menuItemSaveCalibration = new JMenuItem("Save Intrinsics");
//		BoofSwingUtil.setMenuItemKeys(menuItemSaveCalibration, KeyEvent.VK_S, KeyEvent.VK_S);
//		menuItemSaveCalibration.addActionListener(( e ) -> saveIntrinsics());
//		menuFile.add(menuItemSaveCalibration);
//
//		var menuItemSaveLandmarks = new JMenuItem("Save Landmarks");
//		menuItemSaveLandmarks.addActionListener(( e ) -> saveLandmarks());
//		menuFile.add(menuItemSaveLandmarks);
//
//		var menuItemSaveTarget = new JMenuItem("Save Target");
//		menuItemSaveTarget.addActionListener(( e ) -> saveCalibrationTarget());
//		menuFile.add(menuItemSaveTarget);

		JMenuItem menuSettings = new JMenuItem("Settings");
		menuSettings.addActionListener(e -> new GlobalSettingsControls().showDialog(window, this));

		var menuItemQuit = new JMenuItem("Quit", KeyEvent.VK_Q);
		menuItemQuit.addActionListener((e -> System.exit(0)));
		BoofSwingUtil.setMenuItemKeys(menuItemQuit, KeyEvent.VK_Q, KeyEvent.VK_Q);

		menuFile.addSeparator();
		menuFile.add(menuSettings);
		menuFile.add(menuItemQuit);
	}

	protected void updateRecentItems() {
//		BoofSwingUtil.updateRecentItems(this, menuRecent, (info)->processDirectory(new File(info.files.get(0))));
	}

	public void openDialog() {

	}

	/**
	 * Change camera model
	 */
	protected void createAlgorithms() {
		algorithms.safe(() -> {
			if (targetChanged)
				algorithms.detector = configurePanel.targetPanel.createSingleTargetDetector();

			if (targetChanged || calibratorChanged) {
				algorithms.calibrator = new CalibrateStereoPlanar(algorithms.detector.getLayout());
				ControlPanelPinhole controls = configurePanel.pinhole;
				algorithms.calibrator.configure(controls.skew.value, controls.numRadial.vint(), controls.tangential.value);
			}
		});

		targetChanged = false;
		calibratorChanged = false;
		resultsInvalid = true;
	}

	/**
	 * Process two sets of images for left and right cameras
	 */
	public void process( List<String> listLeft, List<String> listRight ) {
		BoofMiscOps.checkEq(listLeft.size(), listRight.size());

		Collections.sort(listLeft);
		Collections.sort(listRight);

		synchronized (lockInput) {
			inputImages = new StereoImageSet() {
				int selected;

				@Override public void setSelected( int index ) {this.selected = index;}

				@Override public int size() {return listLeft.size();}

				@Override public String getLeftName() {return new File(listLeft.get(selected)).getName();}

				@Override public String getRightName() {return new File(listRight.get(selected)).getName();}

				@Override public BufferedImage loadLeft() {
					BufferedImage image = UtilImageIO.loadImage(listLeft.get(selected));
					BoofMiscOps.checkTrue(image != null);
					return image;
				}

				@Override public BufferedImage loadRight() {
					BufferedImage image = UtilImageIO.loadImage(listRight.get(selected));
					BoofMiscOps.checkTrue(image != null);
					return image;
				}
			};
		}

		targetChanged = true;
		handleProcessCalled();
	}

	/**
	 * Process a single set of images that will be automatically split
	 *
	 * @param listFused List of input images
	 * @param horizontal true then left and right images are side by side and split in the middle
	 */
	public void process( List<String> listFused, boolean horizontal ) {

	}

	protected void handleProcessCalled() {
		BoofSwingUtil.checkNotGuiThread();
		if (inputImages == null)
			return;

		// TODO disable and re-enable menu bar when done
		// Prevent the user from trying to open up new images or change anything while this is dynamic
//		SwingUtilities.invokeLater(() -> setMenuBarEnabled(false));
		SwingUtilities.invokeLater(() -> configurePanel.bCompute.setEnabled(false));

		// Update algorithm based on the latest user requests
		boolean detectTargets = targetChanged;
		createAlgorithms();

		if (detectTargets)
			detectLandmarksInImages();

		// Perform calibration
		try {
			StereoParameters param = algorithms.select(() -> algorithms.calibrator.process());
			// Visualize the results
			setRectification(param);
			algorithms.calibrationSuccess = true;
		} catch (RuntimeException e) {
			e.printStackTrace();
			SwingUtilities.invokeLater(() -> BoofSwingUtil.warningDialog(this, e));
			algorithms.calibrationSuccess = false;
		}
		// Tell it to select the last image since that's what's being previewed already
		SwingUtilities.invokeLater(() -> changeSelectedGUI(inputImages.size() - 1));
	}

	/**
	 * Computes stereo rectification and then passes the distortion along to the gui.
	 */
	private void setRectification( final StereoParameters param ) {
		// calibration matrix for left and right camera
		DMatrixRMaj K1 = PerspectiveOps.pinholeToMatrix(param.getLeft(), (DMatrixRMaj)null);
		DMatrixRMaj K2 = PerspectiveOps.pinholeToMatrix(param.getRight(), (DMatrixRMaj)null);

		RectifyCalibrated rectify = RectifyImageOps.createCalibrated();
		rectify.process(K1, new Se3_F64(), K2, param.getRightToLeft().invert(null));

		final DMatrixRMaj rect1 = rectify.getUndistToRectPixels1();
		final DMatrixRMaj rect2 = rectify.getUndistToRectPixels2();

		SwingUtilities.invokeLater(() -> stereoPanel.setRectification(param.getLeft(), rect1, param.getRight(), rect2));
	}

	private void detectLandmarksInImages() {
		// Remove the previously displayed images
		SwingUtilities.invokeLater(() -> imageListPanel.clearImages());

		int numStereoPairs;
		synchronized (lockInput) {
			if (inputImages == null)
				return;
			numStereoPairs = inputImages.size();
		}

		algorithms.calibrationSuccess = false;
		resultsLeft.reset();
		resultsRight.reset();

		int numUsed = 0;
		GrayF32 image = new GrayF32(1, 1);
		for (int imageIdx = 0; imageIdx < numStereoPairs; imageIdx++) {
			CalibrationObservation calibLeft, calibRight;
			BufferedImage buffLeft, buffRight;
			String leftName;

			// Load the image
			synchronized (lockInput) {
				inputImages.setSelected(imageIdx);
				buffLeft = inputImages.loadLeft();
				buffRight = inputImages.loadRight();
				leftName = inputImages.getLeftName();
			}
			// Detect calibration landmarks
			ConvertBufferedImage.convertFrom(buffLeft, image);
			calibLeft = algorithms.select(() -> {
				algorithms.detector.process(image);
				return algorithms.detector.getDetectedPoints();
			});
			// see if at least one view was able to use this target
			boolean used = resultsLeft.add(imageIdx, calibLeft);

			ConvertBufferedImage.convertFrom(buffRight, image);
			calibRight = algorithms.select(() -> {
				algorithms.detector.process(image);
				return algorithms.detector.getDetectedPoints();
			});
			used |= resultsRight.add(imageIdx, calibRight);

			// Pass in the results to the calibrator for future use
			if (used) {
				algorithms.safe(() -> algorithms.calibrator.addPair(calibLeft, calibRight));
				numUsed++;
			}

			resultsLeft.lock();
			resultsLeft.inputToUsed.add(used ? numUsed - 1 : -1);
			resultsLeft.unlock();
			resultsRight.lock();
			resultsRight.inputToUsed.add(used ? numUsed - 1 : -1);
			resultsRight.unlock();

			// Update the GUI by showing the latest images
			boolean _used = used;
			SwingUtilities.invokeLater(() -> {
				// Show images as they are being loaded
				stereoPanel.panelLeft.setImage(buffLeft);
				stereoPanel.panelRight.setImage(buffRight);

				imageListPanel.addImage(leftName, _used);
			});
		}
	}

	protected void settingsChanged( boolean target, boolean calibrator ) {
		BoofSwingUtil.checkGuiThread();
		targetChanged |= target;
		calibratorChanged |= calibrator;
		SwingUtilities.invokeLater(() -> configurePanel.bCompute.setEnabled(true));
	}

	protected void updateVisualizationSettings() {
		stereoPanel.setShowPoints(configurePanel.checkPoints.value);
		stereoPanel.setShowErrors(configurePanel.checkErrors.value);
		stereoPanel.setRectify(configurePanel.checkRectified.value);
		stereoPanel.setShowAll(configurePanel.checkAll.value);
		stereoPanel.setShowNumbers(configurePanel.checkNumbers.value);
		stereoPanel.setShowOrder(configurePanel.checkOrder.value);
		stereoPanel.setErrorScale(configurePanel.selectErrorScale.value.doubleValue());
		stereoPanel.setShowResiduals(configurePanel.checkResidual.value);
	}

	/**
	 * Change which image is being displayed. Request from GUI
	 */
	private void changeSelectedGUI( int index ) {
		BoofSwingUtil.checkGuiThread();
		if (inputImages == null || index < 0 || index >= inputImages.size())
			return;

		// Change the item selected in the list
		imageListPanel.setSelected(index);

		BufferedImage buffLeft;
		BufferedImage buffRight;
		synchronized (lockInput) {
			inputImages.setSelected(index);
			buffLeft = inputImages.loadLeft();
			buffRight = inputImages.loadRight();
		}
		configurePanel.setImageSize(buffLeft.getWidth(), buffLeft.getHeight());
		stereoPanel.panelLeft.setBufferedImageNoChange(buffLeft);
		stereoPanel.panelRight.setBufferedImageNoChange(buffRight);

		updateResultsVisuals(index);
	}

	private void updateResultsVisuals( int inputIndex ) {
		BoofSwingUtil.checkGuiThread();

		resultsLeft.safe(() -> {
			List<CalibrationObservation> all = algorithms.select(() ->
					algorithms.calibrator.getCalibLeft().getObservations());
			CalibrationObservation o = resultsLeft.observations.get(inputIndex);
			int errorIndex = resultsLeft.inputToUsed.get(inputIndex);
			List<ImageResults> errors = algorithms.calibrator.getCalibLeft().getErrors();
			ImageResults results = errorIndex == -1 || errors == null ? null : errors.get(errorIndex);
			stereoPanel.panelLeft.setResults(o, results, all);
		});
		resultsLeft.safe(() -> {
			List<CalibrationObservation> all = algorithms.select(() ->
					algorithms.calibrator.getCalibRight().getObservations());
			CalibrationObservation o = resultsRight.observations.get(inputIndex);
			int errorIndex = resultsRight.inputToUsed.get(inputIndex);
			List<ImageResults> errors = algorithms.calibrator.getCalibRight().getErrors();
			ImageResults results = errorIndex == -1 || errors == null ? null : errors.get(errorIndex);
			stereoPanel.panelRight.setResults(o, results, all);
		});

		stereoPanel.repaint();
	}

	/**
	 * Creates and configures a panel for displaying images names and control buttons for removing points/images
	 */
	protected CalibrationListPanel createImageListPanel() {
		var panel = new CalibrationListPanel();
		// TODO implement these
//		panel.bRemovePoint.addActionListener(( e ) -> removePoint());
//		panel.bRemoveImage.addActionListener(( e ) -> removeImage());
//		panel.bReset.addActionListener(( e ) -> undoAllRemove());
		panel.selectionChanged = this::changeSelectedGUI;
		return panel;
	}

	/**
	 * Provides controls to configure detection and calibration while also listing all the files
	 */
	public class ConfigureInfoPanel extends StandardAlgConfigPanel {
		protected JSpinnerNumber zoom = spinnerWrap(1.0, MIN_ZOOM, MAX_ZOOM, 1.0);
		protected JLabel imageSizeLabel = new JLabel();

		JButton bCompute = button("Compute", false);

		JCheckBoxValue checkPoints = checkboxWrap("Points", true).tt("Show calibration landmarks");
		JCheckBoxValue checkResidual = checkboxWrap("Residual", false).tt("Line showing residual exactly");
		JCheckBoxValue checkErrors = checkboxWrap("Errors", true).tt("Exaggerated residual errors");
		JCheckBoxValue checkRectified = checkboxWrap("Rectify", false).tt("Visualize rectified images");
		JCheckBoxValue checkAll = checkboxWrap("All", false).tt("Show location of all landmarks in all images");
		JCheckBoxValue checkNumbers = checkboxWrap("Numbers", false).tt("Draw feature numbers");
		JCheckBoxValue checkOrder = checkboxWrap("Order", true).tt("Visualize landmark order");
		JSpinnerNumber selectErrorScale = spinnerWrap(10.0, 0.1, 1000.0, 2.0);

		@Getter ControlPanelPinhole pinhole = new ControlPanelPinhole(() -> settingsChanged(false, true));
		@Getter CalibrationTargetPanel targetPanel = new CalibrationTargetPanel(( a, b ) -> handleUpdatedTarget());
		// Displays a preview of the calibration target
		ImagePanel targetPreviewPanel = new ImagePanel();
		// Displays calibration information
		JTextArea textAreaCalib = new JTextArea();
		JTextArea textAreaStats = new JTextArea();

		public ConfigureInfoPanel() {
			configureTextArea(textAreaCalib);
			configureTextArea(textAreaStats);

			targetPreviewPanel.setScaling(ScaleOptions.DOWN);
			targetPreviewPanel.setCentering(true);
			targetPreviewPanel.setPreferredSize(new Dimension(200, 400));
			var targetVerticalPanel = new JPanel(new BorderLayout());
			targetVerticalPanel.add(targetPanel, BorderLayout.NORTH);
			targetVerticalPanel.add(targetPreviewPanel, BorderLayout.CENTER);
			handleUpdatedTarget();

			JTabbedPane tabbedPane = new JTabbedPane();
			tabbedPane.addTab("Model", pinhole);
			tabbedPane.addTab("Target", targetVerticalPanel);
			tabbedPane.addTab("Calib", new JScrollPane(textAreaCalib));
			tabbedPane.addTab("Stats", new JScrollPane(textAreaStats));

			addLabeled(imageSizeLabel, "Image Size", "Size of image being viewed");
			addLabeled(zoom.spinner, "Zoom", "Zoom of image being viewed");
			addAlignCenter(bCompute, "Press to compute calibration with current settings.");
			add(createVisualFlagPanel());
			addLabeled(selectErrorScale.spinner, "Error Scale", "Increases the error visualization");
			add(tabbedPane);
		}

		private void configureTextArea( JTextArea textAreaCalib ) {
			textAreaCalib.setEditable(false);
			textAreaCalib.setWrapStyleWord(true);
			textAreaCalib.setLineWrap(true);
			textAreaCalib.setFont(new Font("monospaced", Font.PLAIN, 12));
		}

		private void handleUpdatedTarget() {
			BufferedImage preview = UtilCalibrationGui.renderTargetBuffered(
					targetPanel.selected, targetPanel.getActiveConfig(), 40);
			targetPreviewPanel.setImageUI(preview);
			settingsChanged(true, false);
		}

		private JPanel createVisualFlagPanel() {
			var panel = new JPanel(new GridLayout(0, 3));
			panel.setBorder(BorderFactory.createTitledBorder("Visual Flags"));

			panel.add(checkPoints.check);
			panel.add(checkErrors.check);
			panel.add(checkRectified.check);
			panel.add(checkResidual.check);
			panel.add(checkAll.check);
			panel.add(checkNumbers.check);
			panel.add(checkOrder.check);

			panel.setMaximumSize(panel.getPreferredSize());

			return panel;
		}

		public void setZoom( double _zoom ) {
			_zoom = Math.max(MIN_ZOOM, _zoom);
			_zoom = Math.min(MAX_ZOOM, _zoom);
			if (_zoom == zoom.value.doubleValue())
				return;
			zoom.value = _zoom;

			BoofSwingUtil.invokeNowOrLater(() -> zoom.spinner.setValue(zoom.value));
		}

		public void setImageSize( final int width, final int height ) {
			BoofSwingUtil.invokeNowOrLater(() -> imageSizeLabel.setText(width + " x " + height));
		}

		@Override public void controlChanged( final Object source ) {
			if (source == bCompute) {
				if (!runningCalibration) {
					new Thread(() -> handleProcessCalled(), "bCompute").start();
				}
			} else if (source == zoom.spinner) {
				stereoPanel.setScale(zoom.vdouble());
			} else {
				updateVisualizationSettings();
			}
		}
	}

	private static class AlgorithmsLocked extends VariableLockSet {
		protected DetectSingleFiducialCalibration detector;
		protected CalibrateStereoPlanar calibrator;
		protected boolean calibrationSuccess;
	}

	private static class ResultsLocked extends VariableLockSet {
		// Index of images used when calibrating
		protected final DogArray_I32 usedImages = new DogArray_I32();
		protected final DogArray_I32 inputToUsed = new DogArray_I32();
		// Active list of observations
		protected final List<CalibrationObservation> observations = new ArrayList<>();
		// Copy of original observation before any edits
		protected final DogArray<CalibrationObservation> original = new DogArray<>(CalibrationObservation::new);

		public boolean add( int imageIndex, CalibrationObservation o ) {
			boolean used = o.points.size() >= 4;
			safe(() -> {
				if (used)
					usedImages.add(imageIndex);
				observations.add(o);
				original.grow().setTo(o);
			});
			return used;
		}

		public void reset() {
			safe(() -> {
				usedImages.reset();
				observations.clear();
				original.reset();
				inputToUsed.reset();
			});
		}
	}

	public static void main( String[] args ) {
		String directory = UtilIO.pathExample("calibration/stereo/Bumblebee2_Chess");

		List<String> leftImages = UtilIO.listByPrefix(directory, "left", null);
		List<String> rightImages = UtilIO.listByPrefix(directory, "right", null);

		SwingUtilities.invokeLater(() -> {
			var app = new CalibrateStereoPlanarAppV2();

			app.window = ShowImages.showWindow(app, "Planar Stereo Calibration", true);
			app.window.setJMenuBar(app.menuBar);

			new Thread(() -> app.process(leftImages, rightImages)).start();
		});
	}
}
