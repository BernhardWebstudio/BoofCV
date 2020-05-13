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

package boofcv.factory.geo;

import boofcv.struct.Configuration;

/**
 * Configuration parameters for estimating a fundamental matrix robustly.
 *
 * @author Peter Abeles
 */
public class ConfigFundamental implements Configuration {

	/**
	 * Which algorithm should it use.
	 */
	public EnumFundamental which = EnumFundamental.LINEAR_7;

	/**
	 * How many points should be used to resolve ambiguity in the solutions?
	 */
	public int numResolve = 2;

	/**
	 * If computed robustly this specifies the error model that's used to prune outliers
	 */
	public ErrorModel errorModel = ErrorModel.GEOMETRIC;

	public ConfigFundamental() {
	}

	public void setTo( ConfigFundamental src ) {
		this.which = src.which;
		this.numResolve = src.numResolve;
		this.errorModel = src.errorModel;
	}

	@Override
	public void checkValidity() {
	}

	public enum ErrorModel {
		/**
		 * Second order approximation of the Euclidean error function.
		 */
		SAMPSON,
		/**
		 * Uses a Euclidean error model. The 3D location of triangulated points are found and the pixel error resulting
		 * from that.
		 */
		GEOMETRIC
	}
}
