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
 * A Packet represents one message between each side of the link.
 */
abstract class Packet {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

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
     * Getter for type.
     *
     * @return type
     */
    public Type getType() {
        return type;
    }

    /**
     * The ASCII character used on the wire to denote this packet type.
     */
    private byte wireCharacter;

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
     * A human-readable description of this packet type.
     */
    private String description;

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
     * decode() will set the parseState to one of these values.
     */
    enum ParseState {

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
    ParseState parseState = ParseState.OK;

    /**
     * Sequence number, between 0 and 63.
     */
    byte seq = 0;

    /**
     * The number of send attempts for this packet.
     */
    int sendCount = 0;

    /**
     * If true, include this packet in
     * SlidingWindowManager.getOutboundPackets().
     */
    boolean resend = false;

    /**
     * The time of the last send attempt, as millis since JVM was started.
     */
    long sendTime;

    /**
     * If true, this packet has been ACK'd by the remote side.
     */
    boolean acked = false;

    /**
     * Checksum type: 1, 2, 3, or 12.
     */
    byte checkType = 1;

    /**
     * If true, this packet type can be windowed.
     */
    boolean windowed = false;

    /**
     * The packet data payload in unencoded/raw form.
     */
    byte [] data;

    /**
     * The packet data payload in encoded/wire form.
     */
    byte [] wireData;

    /**
     * If true, this packet should be encoded as a long packet.
     */
    boolean longPacket = false;

    /**
     * If true, this packet should NOT encode the data field.
     */
    protected boolean dontEncodeData = false;

    /**
     * Protected constructor used by subclasses.
     *
     * @param type packet type
     * @param wireCharacter wire character for this packet type
     * @param description description of packet
     * @param checkType checksum type
     */
    protected Packet(final Type type, final byte wireCharacter,
        final String description, final byte checkType) {

        assert (type != Type.STATE_ANY);

        this.type               = type;
        this.wireCharacter      = wireCharacter;
        this.description        = description;
        this.checkType          = checkType;
    }

    /**
     * Retrieve the next byte for encoding.  Subclasses can override this
     * method to provide e.g. a file-backed data input method.  Note that the
     * IndexOutOfBoundsException thrown when index is out of bounds MUST be
     * caught by the caller, as it is used to signal the end of data.
     *
     * @param index 0 for the first byte, 1 for the next, ...
     * @return the next byte to encode
     */
    public byte getNextDataByte(final int index) {
        return data[index];
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

}
