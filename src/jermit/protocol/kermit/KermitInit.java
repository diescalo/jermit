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
 * KermitInit records the negotiated session parameters sent via the
 * Send-Init/ACK sequence.  This includes things like encoding characters,
 * window sizes, etc.  Several different layers of the protocol stack need to
 * refer to this.  Note package private access.
 */
class KermitInit {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * Default block size.
     */
    static final int DEFAULT_BLOCK_SIZE = 1024;

    /**
     * If true, use 7-bit ASCII for everything.
     */
    private static boolean requireSevenBitOnly = false;

    /**
     * If true, use long packets (up to 9024 bytes).
     */
    private static boolean supportLongPackets = true;

    /**
     * If true, use streaming mode for the file data portion.
     */
    private static boolean supportStreaming = false;

    /**
     * Start-of-packet mark byte.  Default is SOH (0x01).
     */
    public byte MARK = 0x01;

    /**
     * Maximum packet size (not-long packets), up to 94.
     */
    public byte MAXL = 80;

    /**
     * The amount of dead time (in seconds) before assuming a packet was lost
     * and requesting retransmission.
     */
    public byte TIME = 5;

    /**
     * The number of padding characters (bytes) to preceed each packet with.
     */
    public byte NPAD = 0;

    /**
     * Control character used for padding if NPAD > 0.
     */
    public byte PADC = 0;

    /**
     * The character needed to terminate a packet.  Default is carriage
     * return (0x0d).
     */
    public byte EOL = 0x0d;

    /**
     * The escape character for control characters.
     */
    public byte QCTL = '#';

    /**
     * The escape character for 8-bit bytes.
     */
    public byte QBIN = ' ';

    /**
     * Checksum type.
     */
    public byte CHKT = '1';

    /**
     * The prefix character for run-length-encoding.
     */
    public byte REPT = ' ';

    /**
     * A bit-mask describing some capabilities.
     */
    public byte CAPAS = 0x10 | 0x08 | 0x04;

    /**
     * The sliding window size.
     */
    public byte WINDO = 30;

    /**
     * Long packet support.  The maximum size of a long packet is ((MAXLX1 *
     * 95) + MAXLX2).
     */
    public byte MAXLX1 = DEFAULT_BLOCK_SIZE / 95;

    /**
     * Long packet support.  The maximum size of a long packet is ((MAXLX1 *
     * 95) + MAXLX2).
     */
    public byte MAXLX2 = DEFAULT_BLOCK_SIZE % 95;

    /**
     * An undocumented field used to transmit the ability to support
     * streaming.
     */
    public byte WHATAMI = 0;

    /**
     * If true, offer to support Attributes packets Send-Init.
     */
    public boolean attributes = true;

    /**
     * If true, offer to support sliding windows on Send-Init.
     */
    public boolean windowing = false;

    /**
     * If true, offer to support long packets on Send-Init.
     */
    public boolean longPackets = false;

    /**
     * If true, offer to support streaming on Send-Init.
     */
    public boolean streaming = false;

    /**
     * If true, the channel is 7 bit only.
     */
    public boolean sevenBitOnly = true;

    /**
     * If true, support RESEND.
     */
    public boolean doResend = false;

    /**
     * If true, this transfer is in text mode.  Text mode transfers will
     * convert line endings into the local system value (Windows: CRLF, Unix:
     * LF).
     */
    public boolean textMode = true;

    /**
     * Default checksum type.  It can take one of the following values:
     *
     *    1 : 6-bit checksum
     *    2 : 12-bit checksum
     *    3 : CRC16
     *   12 : 12-bit checksum (B)
     */
    public byte checkType = 1;

    /**
     * Set to the official (rubust) Kermit protocol defaults.
     */
    public void kermitDefaults() {
        checkType = 1;
        MARK = 0x01;    // SOH
        MAXL = 80;
        TIME = 5;
        NPAD = 0;
        PADC = 0x00;
        EOL = 0x0d;     // CR
        QCTL = '#';
        QBIN = ' ';
        CHKT = '1';
        REPT = ' ';
        CAPAS = 0;
        WINDO = 1;
        attributes = false;
        windowing = false;
        longPackets = false;
        streaming = false;
        WHATAMI = 0x00;
        sevenBitOnly = true;
        doResend = false;
        textMode = true;
    }

    /**
     * Set to reasonable modern defaults.
     */
    public void reset() {
        textMode = false;
        checkType = 1;
        MARK = 0x01;    // SOH
        MAXL = 80;
        TIME = 5;
        NPAD = 0;
        PADC = 0x00;
        EOL = 0x0d;     // CR
        QCTL = '#';
        if (requireSevenBitOnly == true) {
            // 7 bit channel: do 8th bit prefixing
            QBIN = '&';
            sevenBitOnly = true;
        } else {
            // 8 bit channel: prefer no prefixing
            QBIN = 'Y';
            sevenBitOnly = false;
        }
        CHKT = '3';
        REPT = '~';             // Generally '~'

        // CAPAS flags:
        //    0x10 - Can do RESEND
        //    0x08 - Can accept Attribute packets
        //    0x02 - Can send/receive long packets
        //    0x04 - Can do sliding windows
        CAPAS = 0x10 | 0x08 | 0x04;
        doResend = true;
        attributes = true;
        windowing = true;

        WINDO = 30;
        MAXLX1 = DEFAULT_BLOCK_SIZE / 95;
        MAXLX2 = DEFAULT_BLOCK_SIZE % 95;
        if (supportLongPackets == true) {
            longPackets = true;
            CAPAS |= 0x02;              // Can do long packets
        } else {
            longPackets = false;
        }
        if (supportStreaming == true) {
            streaming = true;
            WHATAMI = 0x28;             // Can do streaming
        } else {
            streaming = false;
            WHATAMI = 0x00;             // No streaming
        }
    }

    /**
     * Public constructor sets Kermit protocol defaults.
     *
     * @param paranoid if true, use the robust but very slow Kermit protocol
     * defaults
     */
    public KermitInit(final boolean paranoid) {
        if (paranoid) {
            kermitDefaults();
        } else {
            reset();
        }
    }

    /**
     * Public constructor sets Kermit protocol defaults.
     */
    public KermitInit() {
        this(false);
    }

    /**
     * Construct a Send-Init packet based on the values in this instance.
     *
     * @return a SendInitPacket
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    public SendInitPacket getSendInit() throws KermitProtocolException {
        return new SendInitPacket(this);
    }

    /**
     * Construct the Ack to a Send-Init packet based on the values in this
     * instance.
     *
     * @return an AckPacket
     */
    public AckPacket getAckPacket() {
        SendInitPacket sendInit = new SendInitPacket(this);
        return new AckPacket(sendInit);
    }

    /**
     * Sets my variables to another KermitInit's values.
     *
     * @param other another KermitInit instance
     */
    public void setTo(final KermitInit other) {
        this.MARK               = other.MARK;
        this.MAXL               = other.MAXL;
        this.TIME               = other.TIME;
        this.NPAD               = other.NPAD;
        this.PADC               = other.PADC;
        this.EOL                = other.EOL;
        this.QCTL               = other.QCTL;
        this.QBIN               = other.QBIN;
        this.CHKT               = other.CHKT;
        this.REPT               = other.REPT;
        this.CAPAS              = other.CAPAS;
        this.WINDO              = other.WINDO;
        this.MAXLX1             = other.MAXLX1;
        this.MAXLX2             = other.MAXLX2;
        this.WHATAMI            = other.WHATAMI;
        this.attributes         = other.attributes;
        this.windowing          = other.windowing;
        this.longPackets        = other.longPackets;
        this.streaming          = other.streaming;
        this.sevenBitOnly       = other.sevenBitOnly;
        this.checkType          = other.checkType;
        this.doResend           = other.doResend;
        this.textMode           = other.textMode;
    }

    /**
     * Updates this instance to values in a Send-Init packet.
     *
     * @param packet a SendInitPacket
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    public void setSendInit(final Packet packet)
        throws KermitProtocolException {

        // Convert the other side's packet type to a SendInit
        SendInitPacket sendInit = new SendInitPacket(packet);
        sendInit.setTo(this);
    }

    /**
     * Send debugging to System.err.
     */
    public void debug() {
        if (DEBUG) {
            System.err.printf("    MAXL: '%c' %d\n", (char) MAXL, MAXL);
            System.err.printf("    TIME: '%c' %d\n", (char) TIME, TIME);
            System.err.printf("    NPAD: '%c' %d\n", (char) NPAD, NPAD);
            System.err.printf("    PADC: '%c' %02x\n", (char) PADC, PADC);
            System.err.printf("    EOL:  '%c' %02x\n", (char) EOL, EOL);
            System.err.printf("    QCTL: '%c' %02x\n", (char) QCTL, QCTL);
            System.err.printf("    QBIN: '%c' %02x\n", (char) QBIN, QBIN);
            System.err.printf("    CHKT: '%c' %02x\n", (char) CHKT, CHKT);
            System.err.printf("    REPT: '%c' %02x\n", (char) REPT, REPT);
            System.err.printf("    CAPAS: '%c' %02x\n", (char) CAPAS, CAPAS);
            System.err.printf("    WHATAMI: '%c' %02x\n", (char) WHATAMI,
                WHATAMI);
            System.err.printf("    attributes: '%s'\n", attributes);
            System.err.printf("    windowing: '%s'\n", windowing);
            System.err.printf("    longPackets: '%s'\n", longPackets);
            System.err.printf("    streaming: '%s'\n", streaming);
            System.err.printf("    doResend: '%s'\n", doResend);
            System.err.printf("    WINDO: '%c' %d\n", (char) WINDO, WINDO);
            System.err.printf("    MAXLX1: '%c' %d\n", (char) MAXLX1, MAXLX1);
            System.err.printf("    MAXLX2: '%c' %d\n", (char) MAXLX2, MAXLX2);
        }
    }

    /**
     * Negotiate the transfer properties between local and remote sides.
     *
     * @param local the transfer parameters I asked for
     * @param remote the transfer parameters they asked for
     */
    public void negotiate(final KermitInit local, final KermitInit remote) {

        if (DEBUG) {
            System.err.println("negotiate() - local side:");
            local.debug();
            System.err.println("negotiate() - remote side:");
            remote.debug();
        }

        // QBIN - see what they ask for
        if (remote.QBIN == 'Y') {
            if (((local.QBIN >= 33) && (local.QBIN <= 62))
                || ((local.QBIN >= 96) && (local.QBIN <= 126))
            ) {
                // Got a valid local QBIN
                this.QBIN = local.QBIN;
            }
        } else if (remote.QBIN == 'N') {
            this.QBIN = ' ';
        } else if (((remote.QBIN >= 33) && (remote.QBIN <= 62))
            || ((remote.QBIN >= 96) && (remote.QBIN <= 126))
        ) {
            // Got a valid remote QBIN
            this.QBIN = remote.QBIN;
        }
        if (this.QBIN == 'Y') {
            // We both offered but don't need to
            this.QBIN = ' ';
        }
        if (remote.QBIN == this.QCTL) {
            // Can't use QCTL as QBIN too
            this.QBIN = ' ';
        }

        // CHKT - if in agreement, use theirs, else use '1'
        if (local.CHKT == remote.CHKT) {
            this.CHKT = remote.CHKT;
        } else {
            this.CHKT = '1';
        }
        if (this.CHKT == 'B') {
            this.checkType = 12;
        } else {
            this.checkType = (byte) (this.CHKT - '0');
        }

        // REPT - if in agreement, use theirs, else use ' '
        if (local.REPT == remote.REPT) {
            if (((local.REPT >= 33) && (local.REPT <= 62))
                || ((local.REPT >= 96) && (local.REPT <= 126))
            ) {
                // Got a valid local REPT
                this.REPT = local.REPT;
            }
            this.REPT = remote.REPT;
        } else {
            this.REPT = ' ';
        }
        if ((remote.REPT == this.QCTL) || (remote.REPT == this.QBIN)) {
            // Can't use QCTL or QBIN as REPT too
            this.REPT = ' ';
        }

        // Attributes - if in agreement, use theirs
        if (local.attributes == remote.attributes) {
            this.attributes = local.attributes;
            this.CAPAS = 0x10 | 0x08;
        } else {
            this.attributes = false;
            this.CAPAS = 0;
        }

        // Check RESEND flag
        if ((this.CAPAS & 0x10) != 0) {
            this.doResend = true;
        }

        // Long packets
        if (local.longPackets == remote.longPackets) {
            this.longPackets = local.longPackets;
            if (local.longPackets == true) {
                this.CAPAS |= 0x02;
            }
        } else {
            this.longPackets = false;
        }
        // Streaming
        this.streaming = false;
        this.WHATAMI = 0;
        if (local.streaming == remote.streaming) {
            this.streaming = local.streaming;
            if (this.streaming == true) {
                this.WHATAMI = 0x28;
            }
        }
        // Windowing
        if (local.windowing == remote.windowing) {
            // Kermit Protocol p. 54, there is only one WINDO for both sides,
            // and it is the minimum value.
            if (remote.WINDO < local.WINDO) {
                this.WINDO = remote.WINDO;
            } else {
                this.WINDO = local.WINDO;
            }

            /*
             * Streaming overrides sliding windows.  If we're both able to
             * stream, don't do windows.
             */
            if (this.streaming == true) {
                this.windowing = false;
            } else {
                this.windowing = local.windowing;
                if (local.windowing == true) {
                    this.CAPAS |= 0x04;
                }
            }
        } else {
            this.windowing = false;
        }
        if (this.windowing == false) {
            this.WINDO = 1;
        }

        if (DEBUG) {
            System.err.println("negotiate() - negotiated values:");
            this.debug();
        }
    }

}
