/*
 * Copyright (c) 2022, Peter Abeles. All Rights Reserved.
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

package boofcv.demonstrations.fiducial;

import boofcv.demonstrations.shapes.DetectBlackPolygonControlPanel;
import boofcv.demonstrations.shapes.DetectBlackShapePanel;
import boofcv.demonstrations.shapes.ThresholdControlPanel;
import boofcv.factory.fiducial.ConfigAztecCode;
import boofcv.gui.controls.JCheckBoxValue;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;

import static boofcv.gui.BoofSwingUtil.MAX_ZOOM;
import static boofcv.gui.BoofSwingUtil.MIN_ZOOM;

/**
 * Controls for {@link DetectAztecCodeApp}.
 *
 * @author Peter Abeles
 */
public class DetectAztecCodeControlPanel extends DetectBlackShapePanel implements ActionListener, ChangeListener {
	ConfigAztecCode config = new ConfigAztecCode();
	DetectAztecCodeApp<?> owner;

	DetectAztecCodeMessagePanel messagePanel;

	// selects which image to view
	JComboBox<String> imageView = new JComboBox<>();

	JButton bRunAgain = new JButton("(ms)");

	boolean bShowMarkers = true;
	boolean bShowBits = false;
	boolean bShowSquares = false;
	boolean bShowPyramids = true;
	boolean bShowContour = false;
	boolean bShowFailures = true;

	JCheckBox showMarkers = checkbox("Markers", bShowMarkers);
	JCheckBox showFailures = checkbox("Failures", bShowFailures);
	JCheckBox showBits = checkbox("Bits", bShowBits);
	JCheckBox showSquares = checkbox("Squares", bShowSquares);
	JCheckBox showPyramids = checkbox("Pyramids", bShowPyramids);
	JCheckBox showContour = checkbox("Contour", bShowContour);
	JCheckBoxValue checkTransposed = checkboxWrap("Transposed", config.considerTransposed).tt("Consider markers with bits transposed");

	DetectBlackPolygonControlPanel polygonPanel;

	public DetectAztecCodeControlPanel( DetectAztecCodeApp<?> owner ) {
		this.owner = owner;

		messagePanel = new DetectAztecCodeMessagePanel(owner);

		polygonPanel = new DetectBlackPolygonControlPanel(owner, config.polygon, config.threshold);
		polygonPanel.thresholdPanel.addHistogramGraph();

		bRunAgain.addActionListener(actionEvent -> DetectAztecCodeControlPanel.this.owner.reprocessImageOnly());
		bRunAgain.setMaximumSize(bRunAgain.getPreferredSize());
		bRunAgain.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

		imageView.addItem("Input");
		imageView.addItem("Binary");
		imageView.addItem("Black");
		imageView.addActionListener(this);
		imageView.setMaximumSize(imageView.getPreferredSize());

		selectZoom = spinner(1, MIN_ZOOM, MAX_ZOOM, 1.0);

		var togglePanel = new JPanel(new GridLayout(0, 2));
		togglePanel.add(showMarkers);
		togglePanel.add(showFailures);
		togglePanel.add(showSquares);
		togglePanel.add(showPyramids);
		togglePanel.add(showBits);
		togglePanel.add(showContour);
		togglePanel.setMaximumSize(togglePanel.getPreferredSize());

		var tabbedPanel = new JTabbedPane();
		tabbedPanel.addTab("Message", messagePanel);
		tabbedPanel.addTab("Controls", polygonPanel);

		var timePanel = new JPanel();
		timePanel.setLayout(new BoxLayout(timePanel, BoxLayout.X_AXIS));
		timePanel.add(processingTimeLabel);
		timePanel.add(bRunAgain);

		addLabeled(timePanel, "Time");
		addLabeled(imageSizeLabel, "Size");
		addLabeled(imageView, "View: ");
		addLabeled(selectZoom, "Zoom");
		add(togglePanel);
		addAlignLeft(checkTransposed.check);
		add(tabbedPanel);
		addVerticalGlue();
		setPreferredSize(new Dimension(250, 200));
	}

	@Override public void controlChanged( final Object source ) {
		if (source == showMarkers) {
			bShowMarkers = showMarkers.isSelected();
			owner.viewUpdated();
		} else if (source == showFailures) {
			bShowFailures = showFailures.isSelected();
			owner.viewUpdated();
		} else if (source == showBits) {
			bShowBits = showBits.isSelected();
			owner.viewUpdated();
		} else if (source == imageView) {
			selectedView = imageView.getSelectedIndex();
			owner.viewUpdated();
		} else if (source == showSquares) {
			bShowSquares = showSquares.isSelected();
			owner.viewUpdated();
		} else if (source == showPyramids) {
			bShowPyramids = showPyramids.isSelected();
			owner.viewUpdated();
		} else if (source == showContour) {
			bShowContour = showContour.isSelected();
			owner.viewUpdated();
		} else {
			if (source == selectZoom) {
				zoom = ((Number)selectZoom.getValue()).doubleValue();
				owner.viewUpdated();
				return;
			}
			owner.configUpdate();
		}
	}

	public ThresholdControlPanel getThreshold() {
		return polygonPanel.thresholdPanel;
	}

	public ConfigAztecCode getConfigQr() {
		config.polygon = polygonPanel.getConfigPolygon();
		return config;
	}
}
