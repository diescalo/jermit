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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import jermit.io.EOFInputStream;

/**
 * A Packet represents one message between each side of the link.
 */
abstract class Packet {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    // If true, enable some debugging output.
    protected static final boolean DEBUG = false;

    /**
     * Carriage return constant.
     */
    private static final byte C_CR = 0x0d;

    /**
     * Line feed constant.
     */
    private static final byte C_LF = 0x0a;

    /**
     * Used by computeCRC16().
     */
    private static short [] crc16Table = new short[256];

    /**
     * Packet type flag.  These are used by the higher-level state machine,
     * so there are some types not defined by the Kermit protocol standard.
     */
    public enum Type {

        /**
         * SendInit, used to establish the session parameters.
         */
        SINIT,

        /**
         * Ack.
         */
        ACK,

        /**
         * Nak.
         */
        NAK,

        /**
         * Data, file data.
         */
        DATA,

        /**
         * File, the beginning of a new file transfer.
         */
        FILE,

        /**
         * EOF, the end of a file transfer.
         */
        EOF,

        /**
         * Break, used to end the session.
         */
        BREAK,

        /**
         * Error, used to signal an irrecoverable error.
         */
        ERROR,

        /**
         * ServInit, used to establish a Kermit server session.
         */
        SERVINIT,

        /**
         * Text, used to transfer file listings and messages.
         */
        TEXT,

        /**
         * RecieveInit, used to initiate a server file transfer.
         */
        RINIT,

        /**
         * FileAttributes, containing file metadata.
         */
        ATTRIBUTES,

        /**
         * Command, used to execute commands on a Kermit server.
         */
        COMMAND,

        /**
         * KermitCommand, used to execute commands on a Kermit server.
         */
        KERMIT_COMMAND,

        /**
         * GenericCommand, used to execute commands on a Kermit server.
         */
        GENERIC_COMMAND,

        /**
         * Reserved.
         */
        RESERVED1,

        /**
         * Reserved.
         */
        RESERVED2,

        /**
         * This is used internally by KermitStateHandler to represent an
         * "any" state.  It cannot be instantiated.
         */
        STATE_ANY,
    }

    /**
     * The packet type.
     */
    private Type type;

    /**
     * The ASCII character used on the wire to denote this packet type.
     */
    private byte wireCharacter;

    /**
     * A human-readable description of this packet type.
     */
    private String description;

    /**
     * decode() will set the parseState to one of these values.
     */
    public enum ParseState {

        /**
         * Packet decoded OK.
         */
        OK,

        /**
         * Packet had a CRC error.
         */
        TRANSMIT_CRC,

        /**
         * Protocol error: LEN field is wrong.
         */
        PROTO_LEN,

        /**
         * Protocol error: SEQ field is wrong.
         */
        PROTO_SEQ,

        /**
         * Protocol error: TYPE field is wrong.
         */
        PROTO_TYPE,

        /**
         * Protocol error: HCHECK field is wrong.
         */
        PROTO_HCHECK,

        /**
         * Protocol error: low-level encoding error (QBIN QBIN).
         */
        PROTO_ENCODING,
    }

    /**
     * State after parsing data received from the wire.
     */
    protected ParseState parseState = ParseState.OK;

    /**
     * Sequence number, between 0 and 63.
     */
    protected byte seq = 0;

    /**
     * The number of send attempts for this packet.
     */
    private int sendCount = 0;

    /**
     * The time of the last send attempt, as millis since JVM was started.
     */
    private long sendTime;

    /**
     * If true, this packet has been ACK'd by the remote side.
     */
    private boolean acked = false;

    /**
     * Checksum type: 1, 2, 3, or 12.
     */
    protected byte checkType = 1;

    /**
     * The packet data payload in unencoded/raw form.  Note package private
     * access.
     */
    byte [] data;

    /**
     * The packet's data payload in encoded/wire form.  Note package private
     * access.
     */
    byte [] wireData;

    /**
     * If true, this packet should be encoded as a long packet.
     */
    protected boolean longPacket = false;

    /**
     * If true, this packet should NOT encode the data field.
     */
    protected boolean dontEncodeData = false;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Static initializer sets up CRC table.
     */
    static {
        makeCrc16Table();
    }

    /**
     * Protected constructor used by subclasses.
     *
     * @param type packet type
     * @param wireCharacter wire character for this packet type
     * @param description description of packet
     * @param checkType checksum type
     * @param seq sequence number of the packet
     */
    protected Packet(final Type type, final byte wireCharacter,
        final String description, final byte checkType, final int seq) {

        assert (type != Type.STATE_ANY);

        this.type               = type;
        this.wireCharacter      = wireCharacter;
        this.description        = description;
        this.checkType          = checkType;

        assert (seq >= 0);
        if (seq >= 64) {
            this.seq            = (byte) (seq % 64);
        } else {
            this.seq            = (byte) seq;
        }
    }

    // ------------------------------------------------------------------------
    // Packet -----------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Getter for type.
     *
     * @return type
     */
    public Type getType() {
        return type;
    }

    /**
     * Getter for wireCharacter.
     *
     * @return the ASCII character used on the wire to denote this packet
     * type
     */
    public byte getWireCharacter() {
        return wireCharacter;
    }

    /**
     * Getter for description.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get a packet type enum from the transmitted character.
     *
     * @param typeChar the character sent/to the wire
     * @return the packet type
     */
    public static Type getPacketType(final byte typeChar) {
        switch (typeChar) {
        case 'S':
            return Type.SINIT;
        case 'Y':
            return Type.ACK;
        case 'N':
            return Type.NAK;
        case 'D':
            return Type.DATA;
        case 'F':
            return Type.FILE;
        case 'Z':
            return Type.EOF;
        case 'B':
            return Type.BREAK;
        case 'E':
            return Type.ERROR;
        case 'I':
            return Type.SERVINIT;
        case 'X':
            return Type.TEXT;
        case 'R':
            return Type.RINIT;
        case 'A':
            return Type.ATTRIBUTES;
        case 'C':
            return Type.COMMAND;
        case 'K':
            return Type.KERMIT_COMMAND;
        case 'G':
            return Type.GENERIC_COMMAND;
        case 'T':
            return Type.RESERVED1;
        case 'Q':
            return Type.RESERVED2;
        default:
            // Catch-all: protocol error
            return Type.ERROR;
        }
    }

    /**
     * Retrieve the next byte for encoding.  Subclasses can override this
     * method to provide e.g. a file-backed data input method.  Note that the
     * IndexOutOfBoundsException thrown when index is out of bounds MUST be
     * caught by the caller, as it is used to signal the end of data.
     *
     * @param index 0 for the first byte, 1 for the next, ...
     * @return the next byte to encode
     * @throws IndexOutOfBoundsException when index is at end
     */
    public byte getNextDataByte(final int index) {
        if (data != null) {
            return data[index];
        }
        throw new IndexOutOfBoundsException("data is null");
    }

    /**
     * Copy the raw buffer data field to object values.
     *
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    protected abstract void readFromData() throws KermitProtocolException;

    /**
     * Copy the object values to the raw buffer data field.
     */
    protected abstract void writeToData();

    // ------------------------------------------------------------------------
    // Encoder/decoder --------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Converts a control character or integer to a printable ASCII
     * character.
     *
     * @param ch non-printable character
     * @return printable ASCII representation
     */
    protected static byte toChar(final byte ch) {
        return (byte) (ch + 32);
    }

    /**
     * Converts a printable ASCII character to a control character or
     * integer.
     *
     * @param ch printable ASCII representation
     * @return non-printable character
     */
    protected static byte unChar(final byte ch) {
        return (byte) (ch - 32);
    }

    /**
     * Converts a control character to a printable ASCII character, and vice
     * versa.
     *
     * @param ch control character or printable ASCII character
     * @return printable ASCII character or control character
     */
    protected byte ctl(final byte ch) {
        return (byte) (ch ^ 0x40);
    }

    /**
     * Initialize CRC table.  Called by static constructor.
     */
    private static void makeCrc16Table() {
        final int CRC16 = 0x8408;
        int crc;
        for (int i = 0; i < 256; i++) {
            crc = i;
            for (int j = 0; j < 8; j++) {
                crc = (crc >> 1) ^ (((crc & 1) != 0) ? CRC16 : 0);
            }
            crc16Table[i] = (short) (crc & 0xFFFF);
        }
    }

    /**
     * This CRC16 routine is modeled after "The Working Programmer's Guide To
     * Serial Protocols" by Tim Kientzle, Coriolis Group Books.  Calculates
     * the CRC used by the Kermit Protocol.
     *
     * @param buffer input bytes to checksum
     * @param start the beginning index, inclusive
     * @param end the ending index, exclusive
     * @param transferParameters low-level transfer handshake details
     * @return the 16-bit crc
     */
    private static int computeCRC16(final byte [] buffer, final int start,
        final int end, final TransferParameters transferParameters) {

        int crc = 0;
        for (int i = start; i < end; i++) {
            byte ch = buffer[i];
            if (transferParameters.active.sevenBitOnly == true) {
                ch &= 0x7F;
            }
            crc = crc16Table[(crc ^ ch) & 0xFF] ^ (crc >> 8);
            crc &= 0xFFFF;
        }
        return crc & 0xFFFF;
    }

    /**
     * Computes checksum type 1.
     *
     * @param buffer input bytes to checksum
     * @param start the beginning index, inclusive
     * @param end the ending index, exclusive
     * @param transferParameters low-level transfer handshake details
     * @return the 8-bit checksum
     */
    private static byte computeChecksum1(final byte [] buffer, final int start,
        final int end, final TransferParameters transferParameters) {

        int sum = 0;
        for (int i = start; i < end; i++) {
            int ch = buffer[i];
            if (transferParameters.active.sevenBitOnly == true) {
                sum += (ch & 0x7F);
            } else {
                sum += ch;
            }
        }
        return (byte) ((sum + (sum & 0xC0)/0x40) & 0x3F);
    }

    /**
     * Computes checksum type 2.
     *
     * @param buffer input bytes to checksum
     * @param start the beginning index, inclusive
     * @param end the ending index, exclusive
     * @param transferParameters low-level transfer handshake details
     * @return the 12-bit checksum
     */
    private static short computeChecksum2(final byte [] buffer,
        final int start, final int end,
        final TransferParameters transferParameters) {

        short sum = 0;
        for (int i = start; i < end; i++) {
            byte ch = buffer[i];
            if (transferParameters.active.sevenBitOnly == true) {
                sum += (ch & 0x7F);
            } else {
                sum += ch;
            }
        }
        return (short) (sum & 0x0FFF);
    }

    /**
     * Encodes one raw byte to a stream of bytes appropriate for transmitting
     * on the wire.
     *
     * @param output array to write to
     * @param transferParameters low-level transfer handshake details
     * @param ch byte to encode
     * @param repeatCount number of repetitions to encode
     * @return encoded bytes
     */
    protected void encodeByte(final ByteArrayOutputStream output,
        final TransferParameters transferParameters, final byte ch,
        byte repeatCount) {

        KermitInit active = transferParameters.active;
        KermitInit local = transferParameters.local;

        // Repeat count
        if ((repeatCount > 3)
            // Also force repeat count on spaces with 'B' check type
            || ((transferParameters.active.checkType == 12) && (ch == ' '))
        ) {
            output.write(active.REPT);
            output.write(toChar((byte) repeatCount));
            repeatCount = 1;
        }

        for (int i = 0; i < repeatCount; i++) {
            byte ch7bit = (byte) (ch & 0x7F);
            boolean needQbin = false;
            boolean needQctl = false;
            boolean isCtl = false;
            byte outputCH = ch;

            if (((ch & 0x80) != 0) && (active.QBIN != ' ')) {
                needQbin = true;
            }
            if ((active.REPT != ' ') && (ch7bit == active.REPT)) {
                // Quoted REPT character
                needQctl = true;
            } else if ((active.QBIN != ' ') && (ch7bit == active.QBIN)) {
                // Quoted QBIN character
                needQctl = true;
            } else if (ch7bit == local.QCTL) {
                // Quoted QCTL character
                needQctl = true;
            } else if ((ch7bit < 0x20) || (ch7bit == 0x7F)) {
                // ctrl character
                needQctl = true;
                isCtl = true;
            }
            if (needQbin == true) {
                output.write(active.QBIN);
                outputCH = ch7bit;
            }
            if (needQctl == true) {
                output.write(active.QCTL);
            }
            if (isCtl) {
                // Either 7-bit OR 8-bit control character
                output.write(ctl(outputCH));
            } else {
                // Regular character
                output.write(outputCH);
            }
        }
    }

    /**
     * Encodes this packet's data field into a stream of bytes appropriate
     * for transmitting on the wire.
     *
     * @param transferParameters low-level transfer handshake details
     * @return encoded bytes
     */
    private byte [] encodeToWire(final TransferParameters transferParameters) {

        // Have the subclass create the raw unencoded data field bytes.
        writeToData();

        if (DEBUG) {
            System.err.printf("encodeToWire() %s %s %d\n", type, description,
                (data == null ? 0 : data.length));
        }

        byte ch;
        byte lastCH = 0;
        byte repeatCount = 0;
        int begin = 0;
        boolean crlf = false;
        boolean first = true;
        int dataMax = 0;

        // Compute maximum space
        if (longPacket == true) {
            // Packet lengths are the largest size the receiver will accept
            dataMax = transferParameters.remote.MAXLX1 * 95 +
                transferParameters.remote.MAXLX2;
            dataMax -= 9;
        } else {
            dataMax = transferParameters.remote.MAXL;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(dataMax);

        for (;;) {

            // Pull next character from either input or file
            if (output.size() >= dataMax - 5) {
                // No more room in destination
                break;
            }

            /*
             * Check for enough space for the next character - include extra
             * for the LF -> CRLF conversion.
             */
            if ((transferParameters.active.textMode == true)
                && (output.size() >= transferParameters.remote.MAXL - 5 - 2)
            ) {
                // No more room in destination
                break;
            }

            if (crlf == true) {
                // System.err.println("    INSERT LF");
                ch = C_LF;
            } else {
                try {
                    ch = getNextDataByte(begin);
                    begin++;
                } catch (IndexOutOfBoundsException e) {
                    // System.err.println("      - end of input, break -");
                    // No more characters to read
                    break;
                }
            }

            if (dontEncodeData) {
                /*
                 * Special case: do not do any prefix handling for several
                 * packets: the Send-Init, its ACK packet, and the Attributes
                 * packet.
                 */
                if (DEBUG) {
                    System.err.printf("NO ENCODE --> ch '%c' %02x\n",
                        (char) ch, ch);
                }
                output.write(ch);
                continue;
            }

            // Text files: strip any CR's, and replace LF's with CRLF.
            if ((transferParameters.active.textMode == true) && (ch == C_CR)) {
                // System.err.println("    STRIP CR");
                continue;
            }
            if ((transferParameters.active.textMode == true) && (ch == C_LF)) {
                if (crlf == false) {
                    // System.err.println("    SUB LF -> CR");
                    crlf = true;
                    ch = C_CR;
                } else {
                    crlf = false;
                }
            }

            if (first == true) {
                // Special case: first character to read
                lastCH = ch;
                first = false;
                repeatCount = 0;
            }

            // Normal case: do repeat count and prefixing
            if ((lastCH == ch) && (repeatCount < 94)) {
                repeatCount++;
            } else {
                /*
                System.err.printf("   encode ch '%c' %02x repeat %d\n",
                    (char) lastCH, lastCH, repeatCount);
                 */
                encodeByte(output, transferParameters, lastCH, repeatCount);
                repeatCount = 1;
                lastCH = ch;
            }

        } // for (;;)

        /*
        System.err.printf("   last_ch '%c' %02x repeat %d\n",
            (char) lastCH, lastCH, repeatCount);
         */

        if (repeatCount > 0) {
            /*
            System.err.printf("   LAST encode ch '%c' %02x repeat %d\n",
                (char) lastCH, lastCH, repeatCount);
             */
            encodeByte(output, transferParameters, lastCH, repeatCount);
        }
        if ((transferParameters.active.textMode == true) && (crlf == true)) {
            // Terminating LF
            if (DEBUG) {
                System.err.println("   LAST TERMINATING LF");
            }
            encodeByte(output, transferParameters, C_LF, (byte) 1);
        }

        if (DEBUG) {
            System.err.printf("encodeToWire() output = %d bytes\n",
                output.size());
        }
        return output.toByteArray();
    }

    /**
     * Encodes this packet into a stream of bytes appropriate for
     * transmitting on the wire.
     *
     * @param transferParameters low-level transfer handshake details
     * @return encoded bytes
     * @throws IOException if a java.io operation throws
     */
    public byte [] encode(final TransferParameters transferParameters) throws IOException {

        KermitInit active = transferParameters.active;
        KermitInit remote = transferParameters.remote;

        int dataMax = 0;
        // Compute maximum space
        if (longPacket == true) {
            // Packet lengths are the largest size the receiver will accept
            dataMax = transferParameters.remote.MAXLX1 * 95 +
                transferParameters.remote.MAXLX2;
            dataMax -= 9;
        } else {
            dataMax = transferParameters.remote.MAXL;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(dataMax);

        if (DEBUG) {
            System.err.printf("encode(): SEQ %d TYPE %c (%s) data.length %d " +
                "wireData.length %d\n", seq, (char) wireCharacter, description,
                (data == null ? 0 : data.length),
                (wireData == null ? 0 : wireData.length));
        }

        int dataCheckDiff = 3;
        boolean longPacket = false;

        byte checkType = this.checkType;
        int checkTypeLength = checkType;
        if (checkType == 12) {
            checkTypeLength = 2;
        }

        // MARK - mark has to agree between both systems ahead of time
        output.write(active.MARK);

        // LEN - leave space but compute later.  We will replace output[1]
        // with output1 at the end.
        output.write(0);
        byte output1 = 0;

        // SEQ
        output.write(toChar((byte) seq));

        // TYPE
        output.write(wireCharacter);

        if ((active.longPackets == true) && (this.longPacket == true)) {
            // We are allowed to use a long packet
            longPacket = true;
            dataCheckDiff = 6;
        }

        // Encode the data field
        if (wireData == null) {
            wireData = encodeToWire(transferParameters);
        }
        byte [] packetData = wireData;

        int packetLength = packetData.length + dataCheckDiff - 1 +
                checkTypeLength;

        if (longPacket == true) {
            output1 = toChar((byte) 0);
            // LENX1 and LENX2
            output.write(toChar((byte) ((packetData.length + 3) / 95)));
            output.write(toChar((byte) ((packetData.length + 3) % 95)));
            // HCHECK
            byte [] header = output.toByteArray();
            int hcheckComputed = output1 + header[2] + header[3] +
                header[4] + header[5];
            hcheckComputed = (hcheckComputed +
                ((hcheckComputed & 192)/64)) & 63;
            output.write(toChar((byte) hcheckComputed));
        } else {
            output1 = toChar((byte) packetLength);
        }

        if (DEBUG) {
            System.err.printf("encode(): longPacket %s packetLength %d " +
                "packetData.length %d checkType %d\n", longPacket,
                packetLength, packetData.length, checkType);
        }

        // Hang onto the encoded data
        output.write(packetData);

        // Add the checksum/crc
        byte [] currentOutput = null;

        switch (checkType) {

        case 1:
            currentOutput = output.toByteArray();
            currentOutput[1] = output1;
            byte checksum = computeChecksum1(currentOutput, 1, output.size(),
                transferParameters);
            if (DEBUG) {
                System.err.printf("encode(): type 1 checksum: %c (%02x)\n",
                    (char) checksum, checksum);
            }
            output.write((byte) toChar(checksum));
            break;

        case 2:
            currentOutput = output.toByteArray();
            currentOutput[1] = output1;
            int checksum2 = computeChecksum2(currentOutput, 1, output.size(),
                transferParameters);
            if (DEBUG) {
                System.err.printf("encode(): type 2 checksum: %c %c (%04x)\n",
                    (char) (toChar((byte) ((checksum2 >> 6) & 0x3F))),
                    (char) (toChar((byte) (checksum2 & 0x3F))), checksum2);
            }
            output.write(toChar((byte) ((checksum2 >> 6) & 0x3F)));
            output.write(toChar((byte) (checksum2 & 0x3F)));
            break;

        case 12:
            currentOutput = output.toByteArray();
            currentOutput[1] = output1;
            checksum2 = computeChecksum2(currentOutput, 1, output.size(),
                transferParameters);
            if (DEBUG) {
                System.err.printf("encode(): type B checksum: %c %c (%04x)\n",
                    (char) (toChar((byte) (((checksum2 >> 6) & 0x3F) + 1))),
                    (char) (toChar((byte) ((checksum2 & 0x3F) + 1))),
                    checksum2);
            }
            output.write(toChar((byte) (((checksum2 >> 6) & 0x3F) + 1)));
            output.write(toChar((byte) ((checksum2 & 0x3F) + 1)));
            break;

        case 3:
            currentOutput = output.toByteArray();
            currentOutput[1] = output1;
            long crc = computeCRC16(currentOutput, 1, output.size(),
                transferParameters);
            if (DEBUG) {
                System.err.printf("encode(): type 3 CRC16: %c %c %c (%04x)\n",
                    (char) (toChar((byte) ((crc >> 12) & 0x0F))),
                    (char) (toChar((byte) ((crc >> 6) & 0x3F))),
                    (char) (toChar((byte) (crc & 0x3F))), crc);
            }
            output.write((byte) toChar((byte) ((crc >> 12) & 0x0F)));
            output.write((byte) toChar((byte) ((crc >> 6) & 0x3F)));
            output.write((byte) toChar((byte) (crc & 0x3F)));
            break;

        default:
            throw new IllegalArgumentException("Internal error: unknown " +
                "checkType " + checkType);
        }

        // Add EOL - which is requested by the other side
        output.write(remote.EOL);
        if (DEBUG) {
            System.err.printf("encode(): %d bytes total for packet\n",
                output.size());
        }
        byte [] result = output.toByteArray();
        result[1] = output1;
        return result;
    }

    /**
     * Decodes the data field according to transfer parameters.  parseState
     * will be changed if the decode encounters an error.
     *
     * @param transferParameters low-level transfer handshake details
     */
    private byte [] decodeData(TransferParameters transferParameters) {
        assert (parseState == ParseState.OK);

        final boolean DEBUG3 = false;

        if (dontEncodeData == true) {
            if (DEBUG) {
                System.err.printf("decodeData() - dontEncodeData is true, " +
                    "NOP.  wireData.length = %d\n",
                    wireData.length);
            }
            return wireData;
        }

        if (DEBUG) {
            System.err.printf("decodeData() %s wireData.length %d\n",
                type, wireData.length);
        }

        KermitInit remote = transferParameters.remote;
        KermitInit active = transferParameters.active;
        boolean doOutputCH = false;
        boolean prefixCtrl = false;
        boolean prefix8bit = false;
        boolean prefixRept = false;
        int repeatCount = 1;
        byte ch;
        byte outputCH = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (int begin = 0; begin < wireData.length; begin++) {

            // Output a previously-escaped character
            if (doOutputCH == true) {
                for (int i = 0; i < repeatCount; i++) {
                    if ((active.textMode == true) && (outputCH == C_CR)) {
                        // Strip CR's
                        if (DEBUG3) {
                            System.err.printf("  '%c' %02x --> STRIP CR\n",
                                (char) outputCH, outputCH);
                        }
                    } else {
                        if (DEBUG3) {
                            System.err.printf("  '%c' %02x --> ch '%c' %02x\n",
                                (char) outputCH, outputCH,
                                (char) outputCH, outputCH);
                        }
                        output.write(outputCH);
                    }
                }
                repeatCount = 1;
                doOutputCH = false;
            }

            // Pull next character from input
            ch = wireData[begin];
            if (DEBUG3) {
                System.err.printf("decodeData() ch '%c' %02x ctrl %s 8bit %s " +
                    "repeat %s repeatCount %d\n",
                    (char) ch, ch, prefixCtrl, prefix8bit, prefixRept,
                    repeatCount);
            }

            if ((active.REPT != ' ') && (ch == active.REPT)) {
                if ((prefixCtrl == true) && (prefix8bit == true)) {
                    // Escaped 8-bit REPT
                    outputCH = (byte) (active.REPT | 0x80);
                    doOutputCH = true;

                    if (DEBUG3) {
                        System.err.println(" - escaped 8-bit REPT -");
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) (wireData[begin]), wireData[begin],
                            (char) (active.REPT | 0x80),
                            active.REPT | 0x80);
                    }

                    prefixCtrl = false;
                    prefix8bit = false;
                    prefixRept = false;
                    continue;
                }

                if (prefixCtrl == true) {
                    // Escaped REPT
                    outputCH = active.REPT;
                    doOutputCH = true;

                    if (DEBUG3) {
                        System.err.println(" - escaped REPT -");
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) (wireData[begin]), wireData[begin],
                            (char) (active.REPT), active.REPT);
                    }

                    prefixCtrl = false;
                    prefixRept = false;
                    continue;
                }

                if (prefixRept == true) {
                    repeatCount = unChar(active.REPT);
                    if (DEBUG3) {
                        System.err.printf(" - 1 REPT count %d\n", repeatCount);
                    }
                    prefixRept = false;
                    continue;
                }

                // Flip rept bit
                prefixRept = true;
                doOutputCH = false;
                continue;
            }

            if (prefixRept == true) {
                repeatCount = unChar(ch);
                if (DEBUG3) {
                    System.err.printf(" - 2 REPT count %d", repeatCount);
                }
                prefixRept = false;
                continue;
            }

            if (ch == remote.QCTL) {
                if ((prefix8bit == true) && (prefixCtrl == true)) {

                    // 8-bit QCTL
                    outputCH = (byte) (remote.QCTL | 0x80);
                    doOutputCH = true;

                    if (DEBUG3) {
                        System.err.println(" - 8-bit QCTL -");
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) (wireData[begin]), wireData[begin],
                            (char) (remote.QCTL | 0x80),
                            remote.QCTL | 0x80);
                    }

                    prefixCtrl = false;
                    prefix8bit = false;
                    continue;
                }

                if (prefixCtrl == true) {
                    // Escaped QCTL
                    outputCH = remote.QCTL;
                    doOutputCH = true;

                    if (DEBUG3) {
                        System.err.println(" - escaped QCTL -");
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) (wireData[begin]), wireData[begin],
                            (char) (remote.QCTL), remote.QCTL);
                    }

                    prefixCtrl = false;
                    continue;
                }
                // Flip ctrl bit
                prefixCtrl = true;
                doOutputCH = false;
                continue;
            }

            if ((active.QBIN != ' ') && (ch == active.QBIN)) {
                if ((prefix8bit == true) && (prefixCtrl == false)) {

                    // This is an error
                    parseState = ParseState.PROTO_ENCODING;

                    if (DEBUG) {
                        System.err.println(" - ERROR QBIN QBIN -");
                    }
                    return data;
                }

                if ((prefix8bit == true) && (prefixCtrl == true)) {
                    // 8-bit QBIN
                    outputCH = (byte) (active.QBIN | 0x80);
                    doOutputCH = true;

                    if (DEBUG3) {
                        System.err.println(" - 8bit QBIN -");
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) (wireData[begin]), wireData[begin],
                            (char) (active.QBIN | 0x80),
                            active.QBIN | 0x80);
                    }

                    prefixCtrl = false;
                    prefix8bit = false;
                    continue;
                }

                if (prefixCtrl == true) {
                    // Escaped QBIN
                    outputCH = active.QBIN;
                    doOutputCH = true;

                    if (DEBUG3) {
                        System.err.println(" - escaped QBIN -");
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) (wireData[begin]), wireData[begin],
                            (char) (active.QBIN), active.QBIN);
                    }

                    prefixCtrl = false;
                    continue;
                }

                // Flip 8bit bit
                prefix8bit = true;
                doOutputCH = false;
                continue;
            }

            // Regular character
            if (prefixCtrl == true) {
                /*
                 * Control prefix can quote anything, so make sure to UN-ctl
                 * only for control characters.
                 */
                if (((ctl(ch) & 0x7F) < 0x20) || ((ctl(ch) & 0x7F) == 0x7F)) {
                    ch = ctl(ch);
                }
                prefixCtrl = false;
            }
            if (prefix8bit == true) {
                ch |= 0x80;
                prefix8bit = false;
            }

            for (int i = 0; i < repeatCount; i++) {
                if ((active.textMode == true) && (ch == C_CR)) {
                    // Strip CR's
                    if (DEBUG3) {
                        System.err.printf("    '%c' %02x --> STRIP CR\n",
                            (char) wireData[begin], wireData[begin]);
                    }
                } else {
                    if (DEBUG3) {
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) wireData[begin], wireData[begin],
                            (char) ch, ch);
                    }
                    output.write(ch);
                }
            }
            repeatCount = 1;
        } // for (begin = 0; begin < data.length; begin++)

        // Output a previously-escaped character (boundary case)
        if (doOutputCH == true) {
            for (int i = 0; i < repeatCount; i++) {
                if ((active.textMode == true) && (outputCH == C_CR)) {
                    // Strip CR's
                    if (DEBUG3) {
                        System.err.printf("    '%c' %02x --> STRIP CR\n",
                            (char) outputCH, outputCH);
                    }
                } else {
                    if (DEBUG3) {
                        System.err.printf("    '%c' %02x --> ch '%c' %02x\n",
                            (char) outputCH, outputCH,
                            (char) outputCH, outputCH);
                    }
                    output.write(outputCH);
                }
            }
            repeatCount = 1;
            doOutputCH = false;
        }

        // Data was OK
        return output.toByteArray();
    }

    /**
     * Decode wire-encoded bytes into the a packet.
     *
     * @param input stream to read from
     * @param transferParameters low-level transfer handshake details
     * @return the next packet, mangled or not.  In insufficient data is
     * present to determine the correct packet type, a NakPacket will be
     * returned.
     * @param kermitState overall protocol state, used for the special case
     * of the Ack to a Send-Init
     * @throws IOException if a java.io operation throws
     */
    public static Packet decode(final EOFInputStream input,
        final TransferParameters transferParameters,
        final KermitState kermitState) throws IOException {

        KermitInit remote = transferParameters.remote;
        KermitInit active = transferParameters.active;
        boolean longPacket = false;
        int dataBegin = 3;
        byte checkType = 1;
        int checkTypeLength = checkType;

        // Find the start of the packet
        int ch = input.read();
        // System.err.printf("scanning for MARK: %c %02x\n", (char) ch, ch);

        for (;ch != active.MARK; ch = input.read()) {
            // Keep scanning until we see the MARK byte.
            // System.err.printf("scanning for MARK: %c %02x\n", (char) ch, ch);
        }
        if (DEBUG) {
            System.err.printf("MARK: %c %02x\n", (char) ch, ch);
        }

        // LEN
        int len = unChar((byte) input.read());
        if (DEBUG) {
            System.err.printf("LEN: %c %02x %d\n", (char) len, len, len);
        }

        if (len == 0) {
            // LEN is 0.  This is either an error or an extended-length
            // packet.
            if (active.longPackets == true) {
                // Extended-length packet, this may be OK.
                longPacket = true;
            } else {
                // Invalid LEN value.  Bail out.
                return new NakPacket(ParseState.PROTO_LEN, (byte) 0);
            }
        } else if ((len == 1) || (len == 2)) {
            // This is definitely an error, length must be 3 or more.
            return new NakPacket(ParseState.PROTO_LEN, (byte) 0);
        }
        // Sanity check the length field
        if ((longPacket == false) && (len > remote.MAXL)) {
            // Bad LEN field - it is too big
            return new NakPacket(ParseState.PROTO_LEN, (byte) 0);
        }

        // Begin collecting bytes that will be included in the checksum.
        ByteArrayOutputStream checkArray = new ByteArrayOutputStream(2048);
        checkArray.write(toChar((byte) len));

        // SEQ
        byte seq = unChar((byte) input.read());
        if (DEBUG) {
            System.err.printf("SEQ: %c %02x %d\n", (char) seq, seq, seq);
        }
        if ((seq < 0) || (seq > 63)) {
            // Bad packet, SEQ is outside valid range.
            return new NakPacket(ParseState.PROTO_SEQ, (byte) 0);
        }
        checkArray.write(toChar(seq));

        // TYPE
        byte typeChar = (byte) input.read();
        if (DEBUG) {
            System.err.printf("TYPE: %c %02x %d\n", (char) typeChar, typeChar,
                typeChar);
        }
        checkArray.write(typeChar);

        if (longPacket == true) {
            // LENX1, LENX2, HCHECK
            byte lenx1 = unChar((byte) input.read());
            byte lenx2 = unChar((byte) input.read());
            len = lenx1 * 95 + lenx2;

            if (DEBUG) {
                System.err.printf("decode(): LENX1 %d LENX2 %d LEN %d\n",
                    lenx1, lenx2, len);
            }

            // Sanity check the length field
            if (len > remote.MAXLX1 * 95 + remote.MAXLX2) {
                // Bad LEN field - it is too big
                return new NakPacket(ParseState.PROTO_LEN, (byte) 0);
            }
            checkArray.write(toChar(lenx1));
            checkArray.write(toChar(lenx2));

            // Grab and compute the extended header checksum
            byte hcheckGiven = unChar((byte) input.read());
            checkArray.write(toChar((byte) hcheckGiven));

            short hcheckComputed = (short) (toChar((byte) 0) + toChar(seq) +
                typeChar + toChar(lenx1) + toChar(lenx2));
            hcheckComputed = (byte) ((hcheckComputed +
                    ((hcheckComputed & 192) / 64)) & 63);

            if (DEBUG) {
                System.err.printf("decode(): HCHECK given %02x computed %02x\n",
                    hcheckGiven, hcheckComputed);
            }

            // Sanity check the HCHECK field
            if (hcheckGiven != hcheckComputed) {
                // Bad extended header checksum
                return new NakPacket(ParseState.PROTO_HCHECK, (byte) 0);
            }

            dataBegin = 6;
            if (DEBUG) {
                System.err.printf("decode(): got EXTENDED packet. " +
                    "HCHECK %04x %04x extended-length %d\n",
                    hcheckGiven, hcheckComputed, len);
            }

        } else {
            // We have already read SEQ and TYPE, take these off the
            // remaining bytes to read.
            len -= 2;
        }

        // At this point, 'len' contains the number of bytes remaining to
        // read, INCLUDING the block check.  Let's read them!
        if (DEBUG) {
            System.err.printf("Reading:");
        }
        while (len > 0) {
            ch = input.read();
            if (DEBUG) {
                System.err.printf(" %02x", (byte) ch);
            }
            checkArray.write(ch);
            len--;
        }
        if (DEBUG) {
            System.err.println();
        }

        Type packetType = getPacketType(typeChar);

        switch (packetType) {
        case SINIT:
            checkType = 1;
            break;
        case NAK:
            checkType = (byte) len;
            if ((checkType < 1) || (checkType > 3)) {
                checkType = 1;
            }
            break;
        default:
            checkType = active.checkType;
            break;
        }

        if (checkType != 12) {
            checkTypeLength = checkType;
        } else {
            checkTypeLength = 2;
        }

        if (DEBUG) {
            System.err.printf("decode(): got packet. LEN %d SEQ %d " +
                "TYPE %c (%s)\n", checkArray.size(), seq,
                (char) typeChar, packetType);
        }

        // Check the checksum
        ParseState checksumState = ParseState.OK;
        byte [] rawData = checkArray.toByteArray();
        if (checkType == 1) {
            byte checksum = toChar(computeChecksum1(rawData, 0,
                    rawData.length - checkTypeLength, transferParameters));
            if (DEBUG) {
                System.err.printf("decode(): type 1 checksum: %c (%02x)\n",
                    (char) checksum, checksum);
                System.err.printf("decode():           given: %c (%02x)\n",
                    (char) rawData[rawData.length - 1],
                    rawData[rawData.length - 1]);
            }

            if (checksum == rawData[rawData.length - 1]) {
                if (DEBUG) {
                    System.err.println("decode(): type 1 checksum OK");
                }
            } else {
                if (DEBUG) {
                    System.err.println("decode(): type 1 checksum FAIL");
                }
                checksumState = ParseState.TRANSMIT_CRC;
            }
        }

        if (checkType == 2) {
            short checksum2 = computeChecksum2(rawData, 0,
                rawData.length - checkTypeLength, transferParameters);
            if (DEBUG) {
                System.err.printf("decode(): type 2 checksum: %c %c (%04x)\n",
                    (char) (toChar((byte) ((checksum2 >> 6) & 0x3F))),
                    (char) (toChar((byte) (checksum2 & 0x3F))),
                    checksum2);
                System.err.printf("decode():           given: %c %c (%04x)\n",
                    rawData[rawData.length - 2],
                    rawData[rawData.length - 1],
                    ((unChar(rawData[rawData.length - 2]) << 6) |
                        unChar(rawData[rawData.length - 1])));
            }
            if (checksum2 == ((unChar(rawData[rawData.length - 2]) << 6) |
                    unChar(rawData[rawData.length - 1]))
            ) {
                if (DEBUG) {
                    System.err.println("decode(): type 2 checksum OK");
                }
            } else {
                if (DEBUG) {
                    System.err.println("decode(): type 2 checksum FAIL");
                }
                checksumState = ParseState.TRANSMIT_CRC;
            }
        }

        if (checkType == 12) {
            short checksum2 = computeChecksum2(rawData, 0,
                rawData.length - checkTypeLength, transferParameters);

            if (DEBUG) {
                System.err.printf("decode(): type B checksum: %c %c (%04x)\n",
                    (char) (toChar((byte) (((checksum2 >> 6) & 0x3F) + 1))),
                    (char) (toChar((byte) ((checksum2 & 0x3F) + 1))),
                    checksum2);
                System.err.printf("decode():           given: %c %c (%04x)\n",
                    rawData[rawData.length - 2],
                    rawData[rawData.length - 1],
                    (((unChar(rawData[rawData.length - 2]) - 1) << 6) |
                        (unChar(rawData[rawData.length - 1]) - 1)));
            }

            if (checksum2 == (((unChar(rawData[rawData.length - 2]) - 1) << 6) |
                    (unChar(rawData[rawData.length - 1]) - 1))
            ) {
                if (DEBUG) {
                    System.err.println("decode(): type B checksum OK");
                }
            } else {
                if (DEBUG) {
                    System.err.println("decode(): type B checksum FAIL");
                }
                checksumState = ParseState.TRANSMIT_CRC;
            }
        }

        if (checkType == 3) {
            int crc = computeCRC16(rawData, 0,
                rawData.length - checkTypeLength, transferParameters);

            if (DEBUG) {
                System.err.printf("decode(): type 3 CRC16: %c %c %c (%04x)\n",
                    (char) (toChar((byte) ((crc >> 12) & 0x0F))),
                    (char) (toChar((byte) ((crc >> 6) & 0x3F))),
                    (char) (toChar((byte) (crc & 0x3F))),
                    crc);
                System.err.printf("decode():       given: %c %c %c (%04x)\n",
                    rawData[rawData.length - 3],
                    rawData[rawData.length - 2],
                    rawData[rawData.length - 1],
                    ((unChar(rawData[rawData.length - 3]) << 12) |
                        (unChar(rawData[rawData.length - 2]) << 6) |
                        unChar(rawData[rawData.length - 1])));
            }
            if (crc == ((unChar(rawData[rawData.length - 3]) << 12) |
                    (unChar(rawData[rawData.length - 2]) << 6) |
                    unChar(rawData[rawData.length - 1]))
            ) {
                if (DEBUG) {
                    System.err.println("decode(): type 3 CRC16 OK");
                }
            } else {
                if (DEBUG) {
                    System.err.println("decode(): type 3 CRC16 FAIL");
                }
                checksumState = ParseState.TRANSMIT_CRC;
            }
        }

        // We have the raw data, save it.
        byte [] packetData = new byte[rawData.length - dataBegin -
            checkTypeLength];
        System.arraycopy(rawData, dataBegin, packetData, 0, packetData.length);

        // Construct a new packet of the right type and type to decode it.
        Packet packet = null;
        switch (packetType) {
        case SINIT:
            packet = new SendInitPacket();
            break;
        case ACK:
            packet = new AckPacket(checkType, seq);
            break;
        case NAK:
            packet = new NakPacket(ParseState.OK, seq);
            break;
        case ERROR:
            packet = new ErrorPacket(checkType,
                "Placeholder - no error string yet", seq);
            break;
        case FILE:
            packet = new FilePacket(checkType, seq);
            break;
        case ATTRIBUTES:
            packet = new FileAttributesPacket(checkType, seq);
            break;
        case DATA:
            packet = new FileDataPacket(checkType, seq);
            break;
        case EOF:
            packet = new EofPacket(checkType, seq);
            break;
        case BREAK:
            packet = new BreakPacket(checkType, seq);
            break;
        /*
        case SERVINIT:
            packet = new ServerInitPacket(checkType, seq);
            break;
        case TEXT:
            packet = new TextPacket(checkType, seq);
            break;
        case RINIT:
            packet = new ReceiveInitPacket(checkType, seq);
            break;
        case COMMAND:
            packet = new HostCommandPacket(checkType, seq);
            break;
        case KERMIT_COMMAND:
            packet = new KermitCommandPacket(checkType, seq);
            break;
        case GENERIC_COMMAND:
            packet = new GenericCommandPacket(checkType, seq);
            break;
        case RESERVED1:
            packet = new Reserved1Packet(checkType, seq);
            break;
        case RESERVED2:
            packet = new Reserved2Packet(checkType, seq);
            break;
        */
        case STATE_ANY:
            // Fall through...
        default:
            // We should never transmit these packets, so see them as errors
            packet = new ErrorPacket(checkType,
                "Internal Error!  Saw STATE_ANY on the wire.", seq);
            break;
        }

        packet.parseState = checksumState;
        packet.checkType = checkType;
        packet.longPacket = longPacket;
        packet.wireData = packetData;

        if (packet.parseState != ParseState.OK) {
            // We encountered an error in the checksum/crc, bail out before
            // decoding the data field.
            if (DEBUG) {
                System.err.printf("decode(): packet type %s failed check/CRC\n",
                    packet.type);
            }
            return packet;
        }

        // Special case: the Ack to a SendInit must not decode its data field
        if ((kermitState == KermitState.KM_SW) && (packet.type == Type.ACK)) {
            if (DEBUG) {
                System.err.println("ACK to the Send-Init");
            }
            packet.dontEncodeData = true;
        }

        // Decode the data field, check the new parse state
        packet.data = packet.decodeData(transferParameters);
        if (packet.parseState != ParseState.OK) {
            // There was some kind of error decoding the data field.
            if (DEBUG) {
                System.err.printf("decode(): packet parse fail, received %s\n",
                    packet.type);
            }
            return packet;
        }

        // The packet appears to have made it across the wire OK.  Now
        // populate its higher-level fields.
        packet.readFromData();

        if (DEBUG) {
            System.err.printf("decode(): ALL OK, received %s\n", packet.type);

            if (packet.type == Type.ERROR) {
                System.err.printf("    ERROR packet text: '%s'\n",
                    ((ErrorPacket) packet).errorMessage);
            }
        }
        return packet;
    }

}
