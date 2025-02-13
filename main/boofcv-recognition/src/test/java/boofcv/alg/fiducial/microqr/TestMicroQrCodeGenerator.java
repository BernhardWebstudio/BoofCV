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

package boofcv.alg.fiducial.microqr;

import boofcv.alg.fiducial.qrcode.PackedBits32;
import boofcv.testing.BoofStandardJUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Abeles
 */
public class TestMicroQrCodeGenerator extends BoofStandardJUnit {
	/** Compare to reference from specification documentation*/
	@Test void formatInformationBits() {
		var qr = new MicroQrCode();
		qr.version = 2;
		qr.mask = MicroQrCodeMaskPattern.M01;
		qr.error = MicroQrCode.ErrorLevel.L;

		PackedBits32 found = MicroQrCodeGenerator.formatInformationBits(qr);
		assertEquals(0b101_0000_1001_1001, found.data[0]);
	}
}
