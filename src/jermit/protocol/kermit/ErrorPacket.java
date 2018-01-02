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

import java.io.UnsupportedEncodingException;

/**
 * ErrorPacket is used to signal an unrecoverable error state that will
 * result in terminating the Kermit session.
 */
class ErrorPacket extends Packet {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The error message string.
     */
    public String errorMessage = "";

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param errorMessage error message to send to remote side
     * @param checkType checksum type
     * @param seq sequence number of the packet
     */
    public ErrorPacket(final String errorMessage, final byte checkType,
        final int seq) {

        super(Type.ERROR, (byte) 'E', "Error", checkType, seq);
        this.errorMessage = errorMessage;
    }

    // ------------------------------------------------------------------------
    // Packet -----------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Decode data bytes into object fields.
     *
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    @Override
    protected void readFromData() throws KermitProtocolException {
        try {
            errorMessage = new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Encode object fields into data bytes.
     */
    @Override
    protected void writeToData() {
        try {
            data = errorMessage.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

}
