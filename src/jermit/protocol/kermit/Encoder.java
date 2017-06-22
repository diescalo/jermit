/*
 * Jermit
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2017 Kevin Lamonte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * @author Kevin Lamonte [kevin.lamonte@gmail.com]
 * @version 1
 */
package jermit.protocol.kermit;

// import java.io.ByteArrayOutputStream;

/**
 * Encoder contains routines to translate bytes from in-memory form to
 * transmittable across the wire.
 */
public final class Encoder {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * Carriage return constant.
     */
    public static final byte C_CR = 0x0d;

    /**
     * Line feed constant.
     */
    public static final byte C_LF = 0x0a;

    /**
     * Prevent instantiation.
     */
    private Encoder() {
    }

    /**
     * Converts a control character or integer to a printable ASCII
     * character.
     *
     * @param ch non-printable character
     * @return printable ASCII representation
     */
    public static byte toChar(final byte ch) {
        return (byte) (ch + 32);
    }

    /**
     * Converts a printable ASCII character to a control character or
     * integer.
     *
     * @param ch printable ASCII representation
     * @return non-printable character
     */
    public static byte unChar(final byte ch) {
        return (byte) (ch - 32);
    }

    /**
     * Converts a control character to a printable ASCII character, and vice
     * versa.
     *
     * @param ch control character or printable ASCII character
     * @return printable ASCII character or control character
     */
    public static byte ctl(final byte ch) {
        return (byte) (ch ^ 0x40);
    }

    /**
     * Encodes several raw bytes to a stream of bytes appropriate for
     * transmitting on the wire.
     *
     * @param transferParameters low-level transfer handshake details
     * @param data bytes to encode
     * @param repeatCount number of repetitions to encode
     * @return encoded bytes
     */
    public static byte [] encode(final TransferParameters transferParameters,
        final byte [] data, final int repeatCount) {

        // TODO
        return null;
    }

    /**
     * Decode several wire-encoded bytes into raw bytes.
     *
     * @param transferParameters low-level transfer handshake details
     * @return decoded bytes
     */
    public static byte [] decode(final TransferParameters transferParameters) {

        // TODO
        return null;
    }

}
