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

package boofcv.alg.geo.selfcalib;

import boofcv.alg.geo.PerspectiveOps;
import boofcv.misc.BoofMiscOps;
import boofcv.misc.ConfigConverge;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.geo.AssociatedPair;
import georegression.geometry.ConvertRotation3D_F64;
import georegression.geometry.GeometryMath_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.so.Rodrigues_F64;
import lombok.Getter;
import lombok.Setter;
import org.ddogleg.optimization.FactoryOptimization;
import org.ddogleg.optimization.UnconstrainedLeastSquares;
import org.ddogleg.optimization.derivative.NumericalJacobianForward_DDRM;
import org.ddogleg.optimization.functions.FunctionNtoM;
import org.ddogleg.struct.DogArray_F64;
import org.ddogleg.struct.VerbosePrint;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

/**
 * Non-linear refinement of intrinsics and rotation while under pure rotation given two views and associated features.
 * The two views can have coupled or independent intrinsic parameters, i.e. they were taken using the same camera or
 * not.
 *
 * @author Peter Abeles
 */
public class RefineTwoViewPinholeRotation implements VerbosePrint {
	/** Convergence criteria */
	public @Getter final ConfigConverge converge = new ConfigConverge(1e-12, 1e-8, 20);

	/** Optimization algorithm */
	public @Getter @Setter
	UnconstrainedLeastSquares<DMatrixRMaj> minimizer = FactoryOptimization.levenbergMarquardt(null, false);

	/** If true then the intrinsic parameters are assumed to be the same for both views */
	@Getter @Setter boolean sameIntrinsics = false;

	/** If true then skew is assumed to be zero */
	@Getter @Setter boolean zeroSkew = true;

	/** If true then the aspect ratio is assumed to be 1.0. I.e. fx == fy */
	@Getter @Setter boolean assumeUnityAspect = true;

	/** Initial error before optimization */
	@Getter double errorBefore;
	/** Final error after optimizing */
	@Getter double errorAfter;

	//-------------- Internal workspace
	ResidualFunction function = new ResidualFunction();

	// Storage for encoded parameters
	DogArray_F64 initialParameters = new DogArray_F64();

	// Reference to input set of associated pixels
	List<AssociatedPair> associatedPixels;

	PrintStream verbose = null;

	/**
	 * Refines the provided parameters. Inputs are only modified if it returns true. If two views are specified
	 * but there's a single view assumption then only the first view is used.
	 *
	 * @param associatedPixels (Input) Matches image features between the two views. Pixels.
	 * @param rotation (Input/Output) Initial estimate of rotation. Results are written to it.
	 * @param intrinsic1 (Input/Output) Initial estimate of intrinsic1. Results are written to it.
	 * @param intrinsic2 (Input/Output) Initial estimate of intrinsic2. Results are written to it.
	 * @return true if it thinks it has a valid solution. False if it knows it failed.
	 */
	public boolean refine( List<AssociatedPair> associatedPixels, DMatrixRMaj rotation,
						   CameraPinhole intrinsic1, CameraPinhole intrinsic2 ) {

		this.associatedPixels = associatedPixels;

		// Copy inputs over
		ConvertRotation3D_F64.matrixToRodrigues(rotation, function.state.rotation);
		function.state.intrinsic1.setTo(intrinsic1);
		function.state.intrinsic2.setTo(intrinsic2);

		initialParameters.resize(function.getNumOfInputsN());
		function.state.encode(initialParameters.data);

		// Configure the minimization
		minimizer.setFunction(function, new NumericalJacobianForward_DDRM(new ResidualFunction()));
		minimizer.initialize(initialParameters.data, converge.ftol, converge.gtol);

		double errorBefore = minimizer.getFunctionValue();

		// Iterate until a final condition has been met
		int iterations;
		for (iterations = 0; iterations < converge.maxIterations; iterations++) {
			if (minimizer.iterate())
				break;
		}

		if (verbose != null)
			verbose.printf("before=%.2e after=%.2e iterations=%d converged=%s\n",
					errorBefore, minimizer.getFunctionValue(), iterations, minimizer.isConverged());

		// Get the refined values
		function.state.decode(minimizer.getParameters());

		// Sanity checks
		if (function.state.intrinsic1.fx <= 0.0 || function.state.intrinsic1.fy <= 0.0) {
			if (verbose != null) verbose.println("Negative focal length view-1");
			return false;
		}

		if (function.state.intrinsic2.fx <= 0.0 || function.state.intrinsic2.fy <= 0.0) {
			if (verbose != null) verbose.println("Negative focal length view-2");
			return false;
		}

		// Copy results
		intrinsic1.setTo(function.state.intrinsic1);
		intrinsic2.setTo(function.state.intrinsic2);
		ConvertRotation3D_F64.rodriguesToMatrix(function.state.rotation, rotation);

		return true;
	}

	@Override public void setVerbose( @Nullable PrintStream out, @Nullable Set<String> configuration ) {
		this.verbose = BoofMiscOps.addPrefix(this, out);
	}

	/**
	 * State of the system being optimized
	 */
	class State {
		public final Rodrigues_F64 rotation = new Rodrigues_F64();
		public final CameraPinhole intrinsic1 = new CameraPinhole();
		public final CameraPinhole intrinsic2 = new CameraPinhole();

		public void encode( double[] parameters ) {
			int index = 0;
			index = encodeIntrinsics(intrinsic1, index, parameters);
			if (!sameIntrinsics)
				index = encodeIntrinsics(intrinsic2, index, parameters);

			parameters[index++] = rotation.unitAxisRotation.x;
			parameters[index++] = rotation.unitAxisRotation.y;
			parameters[index++] = rotation.unitAxisRotation.z;
			parameters[index] = rotation.theta;
		}

		public void decode( double[] parameters ) {
			int index = 0;
			index = decodeIntrinsics(parameters, index, intrinsic1);
			if (!sameIntrinsics)
				index = decodeIntrinsics(parameters, index, intrinsic2);
			else
				intrinsic2.setTo(intrinsic1);

			rotation.unitAxisRotation.x = parameters[index++];
			rotation.unitAxisRotation.y = parameters[index++];
			rotation.unitAxisRotation.z = parameters[index++];
			rotation.theta = parameters[index];

			// ensure it's norm is 1
			rotation.unitAxisRotation.normalize();
		}

		private int encodeIntrinsics( CameraPinhole intrinsic, int index, double[] parameters ) {
			parameters[index++] = intrinsic.fx;
			parameters[index++] = intrinsic.cx;
			parameters[index++] = intrinsic.cy;
			if (!zeroSkew)
				parameters[index++] = intrinsic.skew;
			if (!assumeUnityAspect)
				parameters[index++] = intrinsic.fy;
			return index;
		}

		private int decodeIntrinsics( double[] parameters, int index, CameraPinhole intrinsic ) {
			intrinsic.fx = parameters[index++];
			intrinsic.cx = parameters[index++];
			intrinsic.cy = parameters[index++];
			if (!zeroSkew)
				intrinsic.skew = parameters[index++];
			else
				intrinsic.skew = 0.0;

			if (!assumeUnityAspect)
				intrinsic.fy = parameters[index++];
			else
				intrinsic.fy = intrinsic.fx;
			return index;
		}
	}

	/**
	 * Function which is being minimized.
	 */
	class ResidualFunction implements FunctionNtoM {
		// Storage for decoded state
		State state = new State();

		// Intrinsic parameters in matrix form
		DMatrixRMaj K1_inv = new DMatrixRMaj(3,3);
		DMatrixRMaj K2 = new DMatrixRMaj(3,3);
		// Rotation matrix
		DMatrixRMaj R = new DMatrixRMaj(3,3);
		// Homography for pixels
		DMatrixRMaj H = new DMatrixRMaj(3,3);

		// Predicted observation in view-2
		Point2D_F64 predicted2 = new Point2D_F64();

		// Storage for K2*R
		DMatrixRMaj K2R = new DMatrixRMaj(3,3);

		@Override public void process( double[] input, double[] output ) {
			state.decode(input);

			// H = K2*R*inv(K1)
			ConvertRotation3D_F64.rodriguesToMatrix(state.rotation, R);
			PerspectiveOps.pinholeToMatrix(state.intrinsic1, K1_inv);
			PerspectiveOps.pinholeToMatrix(state.intrinsic2, K2);
			PerspectiveOps.invertCalibrationMatrix(K1_inv, K1_inv);

			CommonOps_DDRM.mult(K2,R,K2R);
			CommonOps_DDRM.mult(K2R,K1_inv, H);

			// x2 = H*x1
			int errorIndex = 0;
			for (int i = 0; i < associatedPixels.size(); i++) {
				AssociatedPair pair = associatedPixels.get(i);
				GeometryMath_F64.mult(H,pair.p1, predicted2);
				output[errorIndex++] = pair.p2.x - predicted2.x;
				output[errorIndex++] = pair.p2.y - predicted2.y;

				// NOTE: Consider doing a symmetric error in the future
				//       E.g. p1.x - predicted1
			}
		}

		@Override public int getNumOfInputsN() {
			int intrinsicUnknown = 3; // focal length fx, cx, cy
			if (!zeroSkew)
				intrinsicUnknown += 1; // skew

			if (!assumeUnityAspect)
				intrinsicUnknown += 1; // focal length fy

			if (sameIntrinsics)
				intrinsicUnknown *= 2; // two cameras

			// 4 = over parameterized Rodriguez for rotation
			return 4 + intrinsicUnknown;
		}

		@Override public int getNumOfOutputsM() {
			return associatedPixels.size()*2;
		}
	}
}
