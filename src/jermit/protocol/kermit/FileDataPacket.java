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

import java.io.InputStream;
import java.io.IOException;

/**
 * FileDataPacket is used to send file data to the wire.
 */
class FileDataPacket extends Packet {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Reference to the underlying file being read from.
     */
    public InputStream input;

    /**
     * Read position of file.
     */
    public long position;

    /**
     * If true, this file is at EOF.
     */
    public boolean eof = false;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param checkType checksum type
     * @param seq sequence number of the packet
     */
    public FileDataPacket(final byte checkType, final int seq) {
        this(checkType, null, -1, seq);
    }

    /**
     * Public constructor - used for sending packets.
     *
     * @param checkType checksum type
     * @param input file input stream
     * @param position file position
     * @param seq sequence number of the packet
     */
    public FileDataPacket(final byte checkType, final InputStream input,
        final long position, final int seq) {

        super(Type.FILE, (byte) 'D', "File Data", checkType, seq);

        this.input      = input;
        this.position   = position;

        // Data packets are allowed to be long
        this.longPacket = true;
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
        // There are no object fields for this.  Higher-level code will look
        // at data[] directly.
    }

    /**
     * Encode object fields into data bytes.
     */
    @Override
    protected void writeToData() {
        // There are no object fields for this.  Higher-level code will look
        // at data[] directly.
    }

    /**
     * Retrieve the next byte for encoding.
     *
     * @param index 0 for the first byte, 1 for the next, ...
     * @return the next byte to encode
     * @throws IndexOutOfBoundsException when index is at end
     */
    @Override
    public byte getNextDataByte(final int index) {
        assert (input != null);

        try {
            int ch = input.read();
            if (ch == -1) {
                // EOF
                if (DEBUG) {
                    System.err.println("      - EOF -");
                }
                eof = true;
                throw new IndexOutOfBoundsException("File has reached EOF");
            }
            // We read a byte
            position++;
            return (byte) ch;
        } catch (IOException e) {
            e.printStackTrace();
            throw new IndexOutOfBoundsException("Error reading from file: " +
                e.getMessage());
        }
    }

}
