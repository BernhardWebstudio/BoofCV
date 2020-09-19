/*
 * Copyright (c) 2011-2020, Peter Abeles. All Rights Reserved.
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

package boofcv.examples.stereo;

import boofcv.alg.geo.PerspectiveOps;
import boofcv.alg.geo.rectify.RectifyCalibrated;
import boofcv.gui.image.ShowImages;
import boofcv.gui.image.VisualizeImageData;
import boofcv.io.UtilIO;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.calib.StereoParameters;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.visualize.PointCloudViewer;
import boofcv.visualize.VisualizeData;
import georegression.geometry.ConvertRotation3D_F64;
import georegression.geometry.GeometryMath_F64;
import georegression.struct.EulerType;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import org.ejml.data.DMatrixRMaj;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Expanding upon ExampleStereoDisparity, this example demonstrates how to rescale an image for stereo processing and
 * then compute its 3D point cloud.  Images are often rescaled to improve speed and some times quality.  Creating
 * 3D point clouds from disparity images is easy and well documented in the literature, but there are some nuances
 * to it.
 *
 * @author Peter Abeles
 */
public class ExampleStereoDisparity3D {

	// Specifies what disparity values are considered
	public static final int minDisparity = 10;
	public static final int rangeDisparity = 60;

	public static void main( String[] args ) {
		// ------------- Compute Stereo Correspondence

		// Load camera images and stereo camera parameters
		String calibDir = UtilIO.pathExample("calibration/stereo/Bumblebee2_Chess/");
		String imageDir = UtilIO.pathExample("stereo/");

		StereoParameters param = CalibrationIO.load(new File(calibDir , "stereo.yaml"));

		// load and convert images into a BoofCV format
		BufferedImage origLeft = UtilImageIO.loadImage(imageDir , "chair01_left.jpg");
		BufferedImage origRight = UtilImageIO.loadImage(imageDir , "chair01_right.jpg");

		GrayU8 distLeft = ConvertBufferedImage.convertFrom(origLeft, (GrayU8) null);
		GrayU8 distRight = ConvertBufferedImage.convertFrom(origRight,(GrayU8)null);

		// rectify images and compute disparity
		GrayU8 rectLeft = distLeft.createSameShape();
		GrayU8 rectRight = distRight.createSameShape();

		RectifyCalibrated rectAlg = ExampleStereoDisparity.rectify(distLeft,distRight,param,rectLeft,rectRight);

//		GrayU8 disparity = ExampleStereoDisparity.denseDisparity(rectLeft, rectRight, 3,minDisparity, rangeDisparity);
		GrayF32 disparity = ExampleStereoDisparity.denseDisparitySubpixel(
				rectLeft, rectRight, 5, minDisparity, rangeDisparity);

		// ------------- Convert disparity image into a 3D point cloud

		// The point cloud will be in the left cameras reference frame
		DMatrixRMaj rectK = rectAlg.getCalibrationMatrix();
		DMatrixRMaj rectR = rectAlg.getRectifiedRotation();

		// extract intrinsic parameters from rectified camera
		double baseline = param.getBaseline()*0.1;
		double fx = rectK.get(0,0);
		double fy = rectK.get(1,1);
		double cx = rectK.get(0,2);
		double cy = rectK.get(1,2);

		double maxZ = baseline*100;

		// Iterate through each pixel in disparity image and compute its 3D coordinate
		PointCloudViewer pcv = VisualizeData.createPointCloudViewer();
		pcv.setTranslationStep(1.5);

		Point3D_F64 pointRect = new Point3D_F64();
		Point3D_F64 pointLeft = new Point3D_F64();
		for( int y = 0; y < disparity.height; y++ ) {
			for( int x = 0; x < disparity.width; x++ ) {
				double d = disparity.unsafe_get(x,y) + minDisparity;

				// skip over pixels were no correspondence was found
				if( d >= rangeDisparity || d <= 0 )
					continue;

				// Coordinate in rectified camera frame
				pointRect.z = baseline*fx/d;
				pointRect.x = pointRect.z*(x - cx)/fx;
				pointRect.y = pointRect.z*(y - cy)/fy;

				// prune points which are likely to be noise
				if( pointRect.z >= maxZ )
					continue;

				// rotate into the original left camera frame
				GeometryMath_F64.multTran(rectR, pointRect, pointLeft);

				// add pixel to the view for display purposes and sets its gray scale value
				int v = rectLeft.unsafe_get(x, y);
				pcv.addPoint(pointLeft.x,pointLeft.y,pointLeft.z,v << 16 | v << 8 | v);
//				temp.add( pointLeft.copy() );
			}
		}

		// move it back a bit to make the 3D structure more apparent
		Se3_F64 cameraToWorld = new Se3_F64();
		cameraToWorld.T.z = -baseline*5;
		cameraToWorld.T.x = baseline*12;
		ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0.1,-0.4,0,cameraToWorld.R);

		// Configure the display
//		pcv.setFog(true);
//		pcv.setClipDistance(baseline*45);
//		PeriodicColorizer colorizer = new TwoAxisRgbPlane.Z_XY(4.0);
//		colorizer.setPeriod(baseline*5);
//		pcv.setColorizer(colorizer); // sometimes pseudo color can be easier to view
		pcv.setDotSize(1);
		pcv.setCameraHFov(PerspectiveOps.computeHFov(param.left));
		pcv.setCameraToWorld(cameraToWorld);
		JComponent viewer = pcv.getComponent();
		viewer.setPreferredSize(new Dimension(600,600*param.left.height/param.left.width));

		// display the results.  Click and drag to change point cloud camera
		BufferedImage visualized = VisualizeImageData.disparity(disparity, null,rangeDisparity,0);
		ShowImages.showWindow(visualized,"Disparity", true);
		ShowImages.showWindow(viewer,"Point Cloud", true);
	}
}
