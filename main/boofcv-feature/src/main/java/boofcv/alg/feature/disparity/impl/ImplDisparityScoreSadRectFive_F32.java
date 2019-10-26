/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.feature.disparity.impl;

import boofcv.alg.InputSanityCheck;
import boofcv.alg.feature.disparity.DisparityScoreWindowFive;
import boofcv.alg.feature.disparity.DisparitySelect;
import boofcv.concurrency.BoofConcurrency;
import boofcv.concurrency.IntRangeObjectConsumer;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import org.ddogleg.struct.FastQueue;

import javax.annotation.Generated;

/**
 * <p>
 * Implementation of {@link boofcv.alg.feature.disparity.DisparityScoreWindowFive} for processing
 * images of type {@link GrayF32}.
 * </p>
 *
 * <p>
 * DO NOT MODIFY. This code was automatically generated by GenerateDisparityScoreSadRectFive.
 * <p>
 *
 * @author Peter Abeles
 */
@Generated("boofcv.alg.feature.disparity.impl.GenerateDisparityScoreSadRectFive")
public class ImplDisparityScoreSadRectFive_F32<DI extends ImageGray<DI>>
		extends DisparityScoreWindowFive<GrayF32,DI>
{
	// Computes disparity from scores
	DisparitySelect<float[], DI> disparitySelect0;

	// reference to input images;
	GrayF32 left, right;
	DI disparity;

	FastQueue workspace = new FastQueue<>(WorkSpace.class, WorkSpace::new);
	ComputeBlock computeBlock = new ComputeBlock();

	public ImplDisparityScoreSadRectFive_F32(int minDisparity, int maxDisparity,
											int regionRadiusX, int regionRadiusY,
											DisparitySelect<float[], DI> computeDisparity) {
		super(minDisparity,maxDisparity,regionRadiusX,regionRadiusY);
		this.disparitySelect0 = computeDisparity;
		workspace.grow();
	}

	@Override
	public void _process(GrayF32 left , GrayF32 right , DI disparity ) {
		InputSanityCheck.checkSameShape(left,right);
		disparity.reshape(left.width,left.height);
		this.left = left;
		this.right = right;
		this.disparity = disparity;

		if( BoofConcurrency.USE_CONCURRENT ) {
			BoofConcurrency.loopBlocks(0,left.height,regionHeight,workspace,computeBlock);
		} else {
			computeBlock.accept((WorkSpace)workspace.get(0),0,left.height);
		}
	}

	class WorkSpace {
		// stores the local scores for the width of the region
		float[] elementScore;
		// scores along horizontal axis for current block
		float[][] horizontalScore;
		// summed scores along vertical axis
		// Save the last regionHeight scores in a rolling window
		float[][] verticalScore;
		// In the rolling verticalScore window, which one is the active one
		int activeVerticalScore;
		// Where the final score it stored that has been computed from five regions
		float[] fiveScore;

		DisparitySelect<float[], DI> computeDisparity;

		public void checkSize() {
			if( horizontalScore == null || verticalScore.length < lengthHorizontal ) {
				horizontalScore = new float[regionHeight][lengthHorizontal];
				verticalScore = new float[regionHeight][lengthHorizontal];
				elementScore = new float[ left.width ];
				fiveScore = new float[ lengthHorizontal ];
			}
			if( computeDisparity == null ) {
				computeDisparity = disparitySelect0.concurrentCopy();
			}
			computeDisparity.configure(disparity,minDisparity,maxDisparity,radiusX*2);
		}
	}

	private class ComputeBlock implements IntRangeObjectConsumer<WorkSpace> {
		@Override
		public void accept(WorkSpace workspace, int minInclusive, int maxExclusive)
		{
			workspace.checkSize();

			// The image border will be skipped, so it needs to back track some
			int row0 = Math.max(0,minInclusive-2*radiusY);
			int row1 = Math.min(left.height,maxExclusive+2*radiusY);

			// initialize computation
			computeFirstRow(row0, workspace);

			// efficiently compute rest of the rows using previous results to avoid repeat computations
			computeRemainingRows(row0,row1, workspace);
		}
	}

	/**
	 * Initializes disparity calculation by finding the scores for the initial block of horizontal
	 * rows.
	 */
	private void computeFirstRow( final int row0 , final WorkSpace workSpace ) {
		final float firstRow[] = workSpace.verticalScore[0];
		workSpace.activeVerticalScore = 1;

		// compute horizontal scores for first row block
		for( int row = 0; row < regionHeight; row++ ) {

			float scores[] = workSpace.horizontalScore[row];

			UtilDisparityScore.computeScoreRow(left, right, row0+row, scores,
					minDisparity, maxDisparity, regionWidth, workSpace.elementScore);
		}

		// compute score for the top possible row
		for( int i = 0; i < lengthHorizontal; i++ ) {
			float sum = 0;
			for( int row = 0; row < regionHeight; row++ ) {
				sum += workSpace.horizontalScore[row][i];
			}
			firstRow[i] = sum;
		}
	}

	/**
	 * Using previously computed results it efficiently finds the disparity in the remaining rows.
	 * When a new block is processes the last row/column is subtracted and the new row/column is
	 * added.
	 */
	private void computeRemainingRows(final int row0 , final int row1, final WorkSpace workSpace )
	{
		for( int row = row0+regionHeight; row < row1; row++ , workSpace.activeVerticalScore++) {
			int oldRow = (row-row0)%regionHeight;
			float previous[] = workSpace.verticalScore[ (workSpace.activeVerticalScore -1) % regionHeight ];
			float active[] = workSpace.verticalScore[ workSpace.activeVerticalScore % regionHeight ];

			// subtract first row from vertical score
			float scores[] = workSpace.horizontalScore[oldRow];
			for( int i = 0; i < lengthHorizontal; i++ ) {
				active[i] = previous[i] - scores[i];
			}

			UtilDisparityScore.computeScoreRow(left, right, row, scores,
					minDisparity,maxDisparity,regionWidth,workSpace.elementScore);

			// add the new score
			for( int i = 0; i < lengthHorizontal; i++ ) {
				active[i] += scores[i];
			}

			if( workSpace.activeVerticalScore >= regionHeight-1 ) {
				float top[] = workSpace.verticalScore[ (workSpace.activeVerticalScore -2*radiusY) % regionHeight ];
				float middle[] = workSpace.verticalScore[ (workSpace.activeVerticalScore -radiusY) % regionHeight ];
				float bottom[] = workSpace.verticalScore[workSpace. activeVerticalScore % regionHeight ];

				computeScoreFive(top,middle,bottom,workSpace.fiveScore,left.width);
				workSpace.computeDisparity.process(row - (1 + 4*radiusY) + 2*radiusY+1, workSpace.fiveScore );
			}
		}
	}

	/**
	 * Compute the final score by sampling the 5 regions.  Four regions are sampled around the center
	 * region.  Out of those four only the two with the smallest score are used.
	 */
	protected void computeScoreFive( float top[] , float middle[] , float bottom[] , float score[] , int width ) {

		// disparity as the outer loop to maximize common elements in inner loops, reducing redundant calculations
		for( int d = minDisparity; d < maxDisparity; d++ ) {

			// take in account the different in image border between the sub-regions and the effective region
			int indexSrc = (d-minDisparity)*width + (d-minDisparity) + radiusX;
			int indexDst = (d-minDisparity)*width + (d-minDisparity);
			int end = indexSrc + (width-d-4*radiusX);
			while( indexSrc < end ) {
				int s = 0;

				// sample four outer regions at the corners around the center region
				float val0 = top[indexSrc-radiusX];
				float val1 = top[indexSrc+radiusX];
				float val2 = bottom[indexSrc-radiusX];
				float val3 = bottom[indexSrc+radiusX];

				// select the two best scores from outer for regions
				if( val1 < val0 ) {
					float temp = val0;
					val0 = val1;
					val1 = temp;
				}

				if( val3 < val2 ) {
					float temp = val2;
					val2 = val3;
					val3 = temp;
				}

				if( val3 < val0 ) {
					s += val2;
					s += val3;
				} else if( val2 < val1 ) {
					s += val2;
					s += val0;
				} else {
					s += val0;
					s += val1;
				}

				score[indexDst++] = s + middle[indexSrc++];
			}
		}
	}

	@Override
	public Class<GrayF32> getInputType() {
		return GrayF32.class;
	}

	@Override
	public Class<DI> getDisparityType() {
		return disparitySelect0.getDisparityType();
	}

}
