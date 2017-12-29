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

/**
 * NakPacket is used to request a packet retransmission.  There are special
 * rules with these packets:
 *
 *   - The checksum type MUST be 1 (single byte).
 */
class NakPacket extends Packet {

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param parseState reason this NAK was generated
     * @param seq sequence number of the packet this NAK is in response to
     */
    public NakPacket(final ParseState parseState, final int seq) {
        super(Type.NAK, (byte) 'N', "NAK Negative Acknowledge", (byte) 1, seq);
        this.parseState = parseState;
    }

    /**
     * Public constructor.
     *
     * @param seq sequence number of the packet this NAK is in response to
     */
    public NakPacket(final int seq) {
        this(ParseState.OK, seq);
    }

    // ------------------------------------------------------------------------
    // Packet -----------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * NAKs by definition have no data field.
     *
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    @Override
    protected final void readFromData() throws KermitProtocolException {
        data = new byte[0];
    }

    /**
     * NAKs by definition have no data field.
     */
    @Override
    protected final void writeToData() {
        data = new byte[0];
    }

}
