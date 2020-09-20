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

package boofcv.alg.sfm.structure;

import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import georegression.struct.point.Point2D_F64;
import org.ddogleg.struct.FastQueue;
import org.ejml.data.DMatrixRMaj;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Graph describing the relationship between image features using matching features from epipolar geometry.
 *
 * @author Peter Abeles
 */
public class PairwiseImageGraph {

	public List<View> nodes = new ArrayList<>();
	public List<Motion> edges = new ArrayList<>();
	public Map<String, Camera> cameras = new HashMap<>();

	public void addCamera( Camera camera ) {
		cameras.put(camera.camera, camera);
	}

	static class Camera {
		public String camera;
		public Point2Transform2_F64 pixelToNorm;
		public CameraPinhole pinhole;

		public Camera( String camera, Point2Transform2_F64 pixelToNorm, CameraPinhole pinhole ) {
			this.camera = camera;
			this.pixelToNorm = pixelToNorm;
			this.pinhole = pinhole;
		}
	}

	/**
	 * Finds all motions which are observations of the specified camera entirely, src and dst
	 *
	 * @param target (Input) Camera being searched for
	 * @param storage (Output) Optional storage for found camera motions
	 */
	public List<Motion> findCameraMotions( Camera target, @Nullable List<Motion> storage ) {
		if (storage == null)
			storage = new ArrayList<>();

		for (int i = 0; i < edges.size(); i++) {
			Motion m = edges.get(i);
			if (m.viewSrc.camera == target && m.viewDst.camera == target) {
				storage.add(m);
			}
		}

		return storage;
	}

	static class View {
		Camera camera;
		public int index;

		public List<Motion> connections = new ArrayList<>();

		/** feature descriptor of all features in this image */
		public FastQueue<TupleDesc> descriptions;
		/** observed location of all features in pixels */
		public FastQueue<Point2D_F64> observationPixels = new FastQueue<>(Point2D_F64::new);
		public FastQueue<Point2D_F64> observationNorm = new FastQueue<>(Point2D_F64::new);

		public View( int index, FastQueue<TupleDesc> descriptions ) {
			this.index = index;
			this.descriptions = descriptions;
		}
	}

	static class Motion {
		/** 3x3 matrix describing epipolar geometry. Fundamental or Essential  */
		public DMatrixRMaj F = new DMatrixRMaj(3, 3);

		/** if this camera motion is known up to a metric transform. otherwise it will be projective */
		public boolean metric;

		/** Which features are associated with each other and in the inlier set */
		public List<AssociatedIndex> associated = new ArrayList<>();

		public View viewSrc;
		public View viewDst;

		public int index;

		public View destination( View src ) {
			if (src == viewSrc) {
				return viewDst;
			} else if (src == viewDst) {
				return viewSrc;
			} else {
				throw new RuntimeException("BUG!");
			}
		}
	}
}
