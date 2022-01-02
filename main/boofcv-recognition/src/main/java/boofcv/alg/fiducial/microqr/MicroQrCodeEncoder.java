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

import boofcv.alg.fiducial.qrcode.PackedBits8;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.alg.fiducial.qrcode.QrCodeCodecBitsUtils;
import boofcv.alg.fiducial.qrcode.ReidSolomonCodes;
import lombok.Getter;
import lombok.Setter;
import org.ddogleg.struct.DogArray_I8;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static boofcv.alg.fiducial.qrcode.QrCodeCodecBitsUtils.MessageSegment;
import static boofcv.alg.fiducial.qrcode.QrCodeCodecBitsUtils.flipBits8;

/**
 * Provides an easy to use interface for specifying QR-Code parameters and generating the raw data sequence. After
 * the QR Code has been created using this class it can then be rendered.
 *
 * By default it will select the qr code version based on the number of
 * bits and the error correction level based on the version and number of bits. If the error correction isn't specified
 * and the version isn't specified then error correction level M is used by default.
 *
 * @author Peter Abeles
 */
public class MicroQrCodeEncoder {
	// used to compute error correction
	private final ReidSolomonCodes rscodes = new ReidSolomonCodes(8, 0b100011101);

	// output qr code
	private final MicroQrCode qr = new MicroQrCode();

	// If true it will automatically select the amount of error correction depending on the length of the data
	private boolean autoErrorCorrection;

	private boolean autoMask;

	/** Encoding for the byte character set. UTF-8 isn't standard compliant but is the most widely used */
	private @Getter @Setter Charset byteCharacterSet = StandardCharsets.UTF_8;

	// workspace variables
	PackedBits8 packed = new PackedBits8();
	// storage for the data message
	private final DogArray_I8 message = new DogArray_I8();
	// storage fot the message's ecc
	private final DogArray_I8 ecc = new DogArray_I8();

	// Since QR Code version might not be known initially and the size of the length byte depends on the
	// version, store the segments here until fixate is called.
	private final List<MessageSegment> segments = new ArrayList<>();

	public MicroQrCodeEncoder() {
		reset();
	}

	public void reset() {
		qr.reset();
		qr.version = -1;
		packed.size = 0;
		autoMask = true;
		autoErrorCorrection = true;
		segments.clear();
	}

	public MicroQrCodeEncoder setVersion( int version ) {
		qr.version = version;
		return this;
	}

	public MicroQrCodeEncoder setError( @Nullable MicroQrCode.ErrorLevel level ) {
		autoErrorCorrection = level == null;
		if (level != null)
			qr.error = level;
		return this;
	}

	public MicroQrCodeEncoder setMask( MicroQrCodeMaskPattern pattern ) {
		autoMask = false;
		qr.mask = pattern;
		return this;
	}

	/**
	 * Select the encoding based on the letters in the message. A very simple algorithm is used internally.
	 */
	public MicroQrCodeEncoder addAutomatic( String message ) {
		QrCodeCodecBitsUtils.addAutomatic(byteCharacterSet, message, segments);
		return this;
	}

	/** Encodes into packed the mode. Number of bits vary depending on the version */
	private void encodeMode( QrCode.Mode mode ) {
		int bits = MicroQrCode.modeIndicatorBitCount(qr.version);
		if (bits == 0)
			return;
		packed.append(mode.ordinal(), bits, false);
	}

	/**
	 * Creates a QR-Code which encodes a number sequence
	 *
	 * @param message String that specifies numbers and no other types. Each number has to be from 0 to 9 inclusive.
	 * @return The QR-Code
	 */
	public MicroQrCodeEncoder addNumeric( String message ) {
		segments.add(QrCodeCodecBitsUtils.createSegmentNumeric(message));
		return this;
	}

	/**
	 * Creates a QR-Code which encodes a number sequence
	 *
	 * @param numbers Array of numbers. Each number has to be from 0 to 9 inclusive.
	 * @return The QR-Code
	 */
	public MicroQrCodeEncoder addNumeric( byte[] numbers ) {
		segments.add(QrCodeCodecBitsUtils.createSegmentNumeric(numbers));
		return this;
	}

	private void encodeNumeric( byte[] numbers, int length ) {
		// specify the mode
		qr.mode = QrCode.Mode.NUMERIC;
		encodeMode(qr.mode);

		// Save the message
		int lengthBits = getLengthBitsNumeric(qr.version);
		QrCodeCodecBitsUtils.encodeNumeric(numbers, length, lengthBits, packed);
	}

	/**
	 * Creates a QR-Code which encodes data in the alphanumeric format
	 *
	 * @param alphaNumeric String containing only alphanumeric values.
	 * @return The QR-Code
	 */
	public MicroQrCodeEncoder addAlphanumeric( String alphaNumeric ) {
		segments.add(QrCodeCodecBitsUtils.createSegmentAlphanumeric(alphaNumeric));
		return this;
	}

	private void encodeAlphanumeric( byte[] numbers, int length ) {
		if (qr.version < 2)
			throw new RuntimeException("Alphanumeric requires version >= 2");

		// specify the mode
		qr.mode = QrCode.Mode.ALPHANUMERIC;
		encodeMode(qr.mode);

		// Save the message
		int lengthBits = getLengthBitsAlphanumeric(qr.version);
		QrCodeCodecBitsUtils.encodeAlphanumeric(numbers, length, lengthBits, packed);
	}

	public MicroQrCodeEncoder addBytes( String message ) {
		return addBytes(message.getBytes(byteCharacterSet));
	}

	/**
	 * Creates a QR-Code which encodes data in the byte format.
	 *
	 * @param data Data to be encoded
	 * @return The QR-Code
	 */
	public MicroQrCodeEncoder addBytes( byte[] data ) {
		segments.add(QrCodeCodecBitsUtils.createSegmentBytes(data));
		return this;
	}

	private void encodeBytes( byte[] data, int length ) {
		if (qr.version < 3)
			throw new RuntimeException("Bytes requires version >= 3");

		// specify the mode
		qr.mode = QrCode.Mode.BYTE;
		encodeMode(qr.mode);

		// Save the message
		int lengthBits = getLengthBitsBytes(qr.version);
		QrCodeCodecBitsUtils.encodeBytes(data, length, lengthBits, packed);
	}

	/**
	 * Creates a QR-Code which encodes Kanji characters
	 *
	 * @param message Data to be encoded
	 * @return The QR-Code
	 */
	public MicroQrCodeEncoder addKanji( String message ) {
		segments.add(QrCodeCodecBitsUtils.createSegmentKanji(message));
		return this;
	}

	private void encodeKanji( byte[] bytes, int length ) {
		if (qr.version < 3)
			throw new IllegalArgumentException("Kanji requires version >= 3");

		// specify the mode
		qr.mode = QrCode.Mode.KANJI;
		encodeMode(qr.mode);

		// Save the message
		int lengthBits = getLengthBitsKanji(qr.version);
		QrCodeCodecBitsUtils.encodeKanji(bytes, length, lengthBits, packed);
	}

	public static int getLengthBitsNumeric( int version ) {
		return 2 + version;
	}

	public static int getLengthBitsAlphanumeric( int version ) {
		return 1 + version;
	}

	public static int getLengthBitsBytes( int version ) {
		return 1 + version;
	}

	public static int getLengthBitsKanji( int version ) {
		return version;
	}

	/**
	 * Call this function after you are done adding to the QR code
	 *
	 * @return The generated QR Code
	 */
	public MicroQrCode fixate() {
		autoSelectVersionAndError();

		// sanity check of code
		int expectedBitSize = bitsAtVersion(qr.version);

		qr.message = "";
		for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
			MessageSegment m = segments.get(segIdx);
			qr.message += m.message;
			switch (m.mode) {
				case NUMERIC -> encodeNumeric(m.data, m.length);
				case ALPHANUMERIC -> encodeAlphanumeric(m.data, m.length);
				case BYTE -> encodeBytes(m.data, m.length);
				case KANJI -> encodeKanji(m.data, m.length);
				default -> throw new RuntimeException("Unsupported: " + m.mode);
			}
		}

		if (packed.size != expectedBitSize)
			throw new RuntimeException("Bad size code. " + packed.size + " vs " + expectedBitSize);

		int maxBits = MicroQrCode.maxDataBits(qr.version, qr.error);

		if (packed.size > maxBits) {
			throw new IllegalArgumentException("The message is longer than the max possible size");
		}

		// add the terminator bits to the bit stream
		int terminatorBits = qr.terminatorBits();
		if (packed.size < maxBits) {
			packed.append(0b0000, Math.min(terminatorBits, maxBits - packed.size), false);
		}

		bitsToMessage(packed);

		// TODO implement properly later
		if (autoMask) {
			qr.mask = MicroQrCodeMaskPattern.M00;
		}

		return qr;
	}

	/**
	 * Checks to see if a request has been made to select version and/or error correction. If so it will pick something
	 */
	private void autoSelectVersionAndError() {
		if (qr.version == -1) {
			// Make sure the version is high enough for specific modes
			int minimumVersion = ensureMinimumVersionForMode();

			int maxEncodedSize = 0;

			escape:
			// select the smallest version which can store all the data
			for (int version = minimumVersion; version <= 4; version++) {
				MicroQrCode.ErrorLevel[] errorsToTry;
				if (autoErrorCorrection) {
					errorsToTry = MicroQrCode.allowedErrorCorrection(version);
				} else {
					errorsToTry = new MicroQrCode.ErrorLevel[]{qr.error};
				}

				for (MicroQrCode.ErrorLevel error : errorsToTry) {
					qr.error = error;

					int maxDataBits = MicroQrCode.maxDataBits(version, error);
					int encodedDataBits = bitsAtVersion(version);
					maxEncodedSize = Math.max(maxEncodedSize, encodedDataBits);

					// See if there's enough storage for this message
					if (encodedDataBits > maxDataBits)
						continue;
					qr.version = version;
					break escape;
				}
			}
			if (qr.version == -1) {
				throw new IllegalArgumentException("Packet too big to be encoded. size=" + maxEncodedSize + " (bits)");
			}
		} else {
			// the version is set but the error correction level isn't. Pick the one with
			// the most error correction that can can store all the data
			if (qr.version == 1) {
				qr.error = MicroQrCode.ErrorLevel.DETECT;
			} else if (autoErrorCorrection) {
				@Nullable MicroQrCode.ErrorLevel selected = null;
				int encodedDataBits = bitsAtVersion(qr.version);

				// ErrorLevel is ordered from most to least correction
				for (MicroQrCode.ErrorLevel error : MicroQrCode.allowedErrorCorrection(qr.version)) {
					int maxDataBits = MicroQrCode.maxDataBits(qr.version, error);

					if (encodedDataBits <= maxDataBits) {
						selected = error;
						break;
					}
				}

				if (selected == null) {
					throw new IllegalArgumentException("You need to use a high version number to store the data. Tried " +
							"all error correction levels at version " + qr.version + ". Total Data " + (packed.size/8));
				}
				qr.error = selected;
			}
		}

		// Sanity check
		int dataBits = bitsAtVersion(qr.version);
		int maxDataBits = MicroQrCode.maxDataBits(qr.version, qr.error);
		if (dataBits > maxDataBits) {
			int encodedBits = totalEncodedBitsNoOverHead();
			throw new IllegalArgumentException("Version and error level can't encode all the data. " +
					"Version=" + qr.version + ", Error=" + qr.error +
					" , Encoded_Bits=" + encodedBits + ", Overhead_Bits=" + (dataBits - encodedBits) +
					" , Data_bits=" + dataBits + ", Limit_bits=" + maxDataBits);
		}
	}

	/**
	 * Make sure the version is at least the minimum required for the encoding mode.
	 */
	private int ensureMinimumVersionForMode() {
		int minimum = 0;
		for (MessageSegment s : segments) {
			int minVersion = switch (s.mode) {
				case NUMERIC -> 1;
				case ALPHANUMERIC -> 2;
				case BYTE, KANJI -> 3;
				default -> throw new RuntimeException("Unexpected mode " + s.mode);
			};
			minimum = Math.max(minVersion, minimum);
		}
		return minimum;
	}

	/**
	 * Computes how many bits it takes to encode the message at this version number
	 */
	private int bitsAtVersion( int version ) {
		int total = 0;
		for (int i = 0; i < segments.size(); i++) {
			total += sizeInBits(segments.get(i), version);
		}
		return total;
	}

	/**
	 * Returns the number of encoded bits without overhead
	 */
	private int totalEncodedBitsNoOverHead() {
		int total = 0;
		for (int i = 0; i < segments.size(); i++) {
			total += segments.get(i).encodedSizeBits;
		}
		return total;
	}

	protected void bitsToMessage( PackedBits8 stream ) {
		// add padding to make it align to 8
		stream.append(0, (8 - (stream.size%8))%8, false);

//		System.out.println("encoded message");
//		stream.print();System.out.println();

		MicroQrCode.VersionInfo version = MicroQrCode.VERSION_INFO[qr.version];
		MicroQrCode.DataInfo dataInfo = version.levels.get(qr.error);
		if (dataInfo == null)
			throw new IllegalArgumentException("Invalid error correction level selected for level");

		qr.rawbits = new byte[version.codewords];
		int wordsEcc = version.codewords - dataInfo.dataCodewords;

		message.resize(dataInfo.dataCodewords);

		rscodes.generator(wordsEcc);
		ecc.resize(wordsEcc);

		System.arraycopy(stream.data, 0, message.data, 0, stream.arrayLength());
		addPadding(message, stream.arrayLength(), 0b00110111, 0b10001000);
		flipBits8(message);

		// compute the ecc
		rscodes.computeECC(message, ecc);
		flipBits8(message);
		flipBits8(ecc);

		// write it into the output array
		System.arraycopy(message.data, 0, qr.rawbits, 0, message.size);
		System.arraycopy(ecc.data, 0, qr.rawbits, message.size, ecc.size);
	}

	private void addPadding( DogArray_I8 queue, int dataBytes, int padding0, int padding1 ) {
		boolean a = true;
		for (int i = dataBytes; i < queue.size; i++, a = !a) {
			if (a)
				queue.data[i] = (byte)padding0;
			else
				queue.data[i] = (byte)padding1;
		}
	}

	public int sizeInBits( MessageSegment segment, int version ) {
		int lengthBits = switch (segment.mode) {
			case NUMERIC -> getLengthBitsNumeric(version);
			case ALPHANUMERIC -> getLengthBitsAlphanumeric(version);
			case BYTE -> getLengthBitsBytes(version);
			case KANJI -> getLengthBitsKanji(version);
			default -> throw new RuntimeException("Egads");
		};
		return segment.encodedSizeBits + lengthBits + MicroQrCode.modeIndicatorBitCount(version);
	}
}
