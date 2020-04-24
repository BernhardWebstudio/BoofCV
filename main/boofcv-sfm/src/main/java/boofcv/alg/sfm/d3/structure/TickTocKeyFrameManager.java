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

package boofcv.alg.sfm.d3.structure;

import boofcv.abst.tracker.PointTracker;
import org.ddogleg.struct.GrowQueue_I32;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.Set;

/**
 * This key frame manager performs its maintenance at a constant fixed rate independent of observations.
 *
 * @author Peter Abeles
 */
public class TickTocKeyFrameManager implements VisOdomKeyFrameManager {
	/**
	 * The period at which the current frame is turned into a new keyframe
	 */
	public int keyframePeriod = 1;

	// list of frames to discard
	private final GrowQueue_I32 keyframeIndexes = new GrowQueue_I32();

	/**
	 * No need to configure or initialize anything
	 */
	@Override
	public void initialize(int imageWidth, int imageHeight) {}

	@Override
	public GrowQueue_I32 selectFramesToDiscard(PointTracker<?> tracker, int maxKeyFrames, VisOdomBundleAdjustment<?> sba) {
		keyframeIndexes.reset();
		// Add key frames until it hits the max
		if( sba.frames.size <= maxKeyFrames)
			return keyframeIndexes;

		// See if the current keyframe should be removed from the list and prevent it from becoming a real keyframe
		boolean removeCurrent = tracker.getFrameID()%keyframePeriod != 0;
		maxKeyFrames += removeCurrent ? 1 : 0;

		// Remove older keyframes until it has the correct number of keyframes
		for (int i = 0; i < sba.frames.size - maxKeyFrames; i++) {
			keyframeIndexes.add(i);
		}

		// Now remove the current frame. This is done at the end to ensure the order of key frame indexes
		if( removeCurrent ) {
			keyframeIndexes.add( sba.frames.size-1 );
		}

		return keyframeIndexes;
	}

	/**
	 * Tracker information is ignored
	 */
	@Override
	public void handleSpawnedTracks(PointTracker<?> tracker) {}

	@Override
	public void setVerbose(@Nullable PrintStream out, @Nullable Set<String> configuration) {}
}
