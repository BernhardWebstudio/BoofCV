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

package boofcv.abst.feature.detect.intensity;

import boofcv.alg.feature.detect.intensity.GradientCornerIntensity;
import boofcv.struct.ListIntPoint2D;
import boofcv.struct.image.ImageGray;

/**
 * Wrapper around children of {@link boofcv.alg.feature.detect.intensity.GradientCornerIntensity}.
 * 
 * @author Peter Abeles
 */
public class WrapperGradientCornerIntensity<I extends ImageGray<I>,D extends ImageGray<D>>
		extends BaseGeneralFeatureIntensity<I,D>
{
	GradientCornerIntensity<D> alg;

	public WrapperGradientCornerIntensity(GradientCornerIntensity<D> alg) {
		super(null,alg.getInputType());
		this.alg = alg;
	}

	@Override
	public void process(I image , D derivX, D derivY, D derivXX, D derivYY, D derivXY ) {
		init(image.width,image.height);
		alg.process(derivX,derivY,intensity);
	}

	@Override
	public ListIntPoint2D getCandidatesMin() {
		return null;
	}

	@Override
	public ListIntPoint2D getCandidatesMax() {
		return null;
	}

	@Override
	public boolean getRequiresGradient() {
		return true;
	}

	@Override
	public boolean getRequiresHessian() {
		return false;
	}

	@Override
	public boolean hasCandidates() {
		return false;
	}

	@Override
	public int getIgnoreBorder() {
		return alg.getIgnoreBorder();
	}

	@Override
	public boolean localMaximums() {
		return true;
	}

	@Override
	public Class<I> getImageType() {
		return null;
	}

	@Override
	public Class<D> getDerivType() {
		return alg.getInputType();
	}

	@Override
	public boolean localMinimums() {
		return false;
	}
}
