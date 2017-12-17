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
 * SendInitPacket is used to establish session parameters.  There are special
 * rules with these packets:
 *
 *   - They cannot encode/decode the data field.
 *
 *   - The checksum type MUST be 1 (single byte).
 */
class SendInitPacket extends Packet {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The parameters that this SendInitPacket needs to encode.
     */
    private KermitInit init = new KermitInit();

    /**
     * Public constructor.
     */
    public SendInitPacket() {
        super(Type.SINIT, (byte) 'S', "Send-Init", (byte) 1);
        init = new KermitInit();
        dontEncodeData = true;
    }

    /**
     * Build a SendInit out of a KermitInit instance.
     *
     * @param init KermitInit instance
     */
    public SendInitPacket(final KermitInit init) {
        this();
        setTo(init);
    }

    /**
     * Build a SendInit out of an AckPacket instance.
     *
     * @param packet AckPacket
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    public SendInitPacket(final Packet packet) throws KermitProtocolException {
        this();

        assert ((packet instanceof AckPacket)
            || (packet instanceof SendInitPacket));

        // Grab a copy of their data
        this.data = new byte[packet.data.length];
        System.arraycopy(packet.data, 0, this.data, 0, this.data.length);

        // Decode it
        readFromData();
    }

    /**
     * Sets my variables to a KermitInit's values.
     *
     * @param init KermitInit instance
     */
    public void setTo(final KermitInit init) {
        this.init.setTo(init);
        writeToData();
    }

    /**
     * Decode a SendInit data field to the KermitInit object.
     *
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    @Override
    protected void readFromData() throws KermitProtocolException {
        int capasI = 9;

        // Reset to bare Kermit defaults
        init.kermitDefaults();

        if ((data.length >= 1) && (data[0] != ' ')) {
            // Byte 1: MAXL
            init.MAXL = Encoder.unChar(data[0]);
            if (DEBUG) {
                System.err.printf("    MAXL: '%c' %d\n", (char) (data[0]),
                    init.MAXL);
            }

            if (init.MAXL > 94) {
                // Error: invalid maximum packet length
                throw new KermitProtocolException("Invalid MAXL");
            }
        }

        if ((data.length >= 2) && (data[1] != ' ')) {
            // Byte 2: TIME
            init.TIME = Encoder.unChar(data[1]);
            if (DEBUG) {
                System.err.printf("    TIME: '%c' %d\n", (char) (data[1]),
                    init.TIME);
            }
        }

        if ((data.length >= 3) && (data[2] != ' ')) {
            // Byte 3: NPAD
            init.NPAD = Encoder.unChar(data[2]);
            if (DEBUG) {
                System.err.printf("    NPAD: '%c' %d\n", (char) (data[2]),
                    init.NPAD);
            }
        }

        if ((data.length >= 4) && (data[3] != ' ')) {
            // Byte 4: PADC - ctl
            init.PADC = Encoder.ctl(data[3]);
            if (DEBUG) {
                System.err.printf("    PADC: '%c' %02x\n", (char) (data[3]),
                    init.PADC);
            }
        }

        if ((data.length >= 5) && (data[4] != ' ')) {
            // Byte 5: EOL
            init.EOL = Encoder.unChar(data[4]);
            if (DEBUG) {
                System.err.printf("    EOL:  '%c' %02x\n", (char) (data[4]),
                    init.EOL);
            }
        }

        if ((data.length >= 6) && (data[5] != ' ')) {
            // Byte 6: QCTL - verbatim
            init.QCTL = data[5];
            if (DEBUG) {
                System.err.printf("    QCTL: '%c' %02x\n", (char) (data[5]),
                    init.QCTL);
            }
        }

        if ((data.length >= 7) && (data[6] != ' ')) {
            // Byte 7: QBIN - verbatim
            init.QBIN = data[6];
            if (DEBUG) {
                System.err.printf("    QBIN: '%c' %02x\n", (char) (data[6]),
                    init.QBIN);
            }
        }

        if ((data.length >= 8) && (data[7] != ' ')) {
            // Byte 8: CHKT - verbatim
            init.CHKT = data[7];
            if (DEBUG) {
                System.err.printf("    CHKT: '%c' %02x\n", (char) (data[7]),
                    init.CHKT);
            }
        }

        if ((data.length >= 9) && (data[8] != ' ')) {
            // Byte 9: REPT - verbatim
            init.REPT = data[8];
            if (DEBUG) {
                System.err.printf("    REPT: '%c' %02x\n", (char) (data[8]),
                    init.REPT);
            }
        }

        if (data.length >= 10) {
            while (data.length > capasI) {
                // Byte 10-?: CAPAS
                byte capas = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    CAPAS %d: '%c' %02x\n", (capasI - 9),
                        (char) (data[capasI]), capas);
                }

                if (capasI == 9) {
                    if ((byte) (capas & 0x10) != 0) {
                        // Ability to support RESEND
                        if (DEBUG) {
                            System.err.printf("    CAPAS %d: Can do RESEND\n",
                                (capasI - 9));
                        }
                    }

                    if ((byte) (capas & 0x08) != 0) {
                        // Ability to accept "A" packets
                        if (DEBUG) {
                            System.err.printf("    CAPAS %d: Can accept A packets\n",
                                (capasI - 9));
                        }
                        init.attributes = true;
                    }

                    if ((byte) (capas & 0x04) != 0) {
                        // Ability to do full duplex sliding window
                        if (DEBUG) {
                            System.err.printf("    CAPAS %d: Can do full duplex sliding windows\n",
                                (capasI - 9));
                        }
                        init.windowing = true;
                    }

                    if ((byte) (capas & 0x02) != 0) {
                        // Ability to transmit and receive extended-length
                        // packets
                        if (DEBUG) {
                            System.err.printf("    CAPAS %d: Can do extended-length packets\n",
                                (capasI - 9));
                        }
                        init.longPackets = true;
                    }
                }

                // Point to next byte
                capasI++;

                    if ((byte) (capas & 0x01) == 0) {
                    // Last capas byte
                    break;
                }
            }

            if (data.length >= capasI + 1) {
                // WINDO
                init.WINDO = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    WINDO:  '%c' %02x %d\n",
                        (char) (data[capasI]), init.WINDO, init.WINDO);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // MAXLX1
                init.MAXLX1 = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    MAXLX1: '%c' %02x %d\n",
                        (char) (data[capasI]), init.MAXLX1, init.MAXLX1);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // MAXLX2
                init.MAXLX2 = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    MAXLX2: '%c' %02x %d\n",
                        (char) (data[capasI]), init.MAXLX2, init.MAXLX2);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // CHECKPOINT1 - Discard
                if (DEBUG) {
                    System.err.printf("    CHECKPOINT 1: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // CHECKPOINT2 - Discard
                if (DEBUG) {
                    System.err.printf("    CHECKPOINT 2: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // CHECKPOINT3 - Discard
                if (DEBUG) {
                    System.err.printf("    CHECKPOINT 3: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // CHECKPOINT4 - Discard
                if (DEBUG) {
                    System.err.printf("    CHECKPOINT 4: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // WHATAMI
                byte whatami = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    WHATAMI: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }

                if ((byte) (whatami & 0x08) != 0) {
                    // Ability to stream
                    if (DEBUG) {
                        System.err.println("    WHATAMI: Can stream");
                    }
                    init.streaming = true;
                }

                capasI++;
            }

            if (data.length >= capasI + 1) {
                // System type - Length
                byte id_length = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    System ID length: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }

                if (data.length >= capasI + 1 + id_length) {
                    StringBuilder systemID = new StringBuilder("");
                    for (int j = 0; j < id_length; j++) {
                        systemID.append(data[capasI + 1 + j]);
                    }
                    if (DEBUG) {
                        System.err.printf("        System ID: \"%s\"\n",
                            systemID.toString());
                    }
                    capasI += id_length;
                }
                capasI++;
            }

            if (data.length >= capasI + 1) {
                // WHATAMI2
                byte whatami2 = Encoder.unChar(data[capasI]);
                if (DEBUG) {
                    System.err.printf("    WHATAMI2: '%c' %02x %d\n",
                        (char) (data[capasI]), data[capasI], data[capasI]);
                }
                capasI++;
            }
        }

        /*
         * If long packets are supported, but MAXLX1 and MAXLX2 were not
         * provided, there is a default of 500.
         */
        if (init.longPackets == true) {
            if ((init.MAXLX1 == 0) && (init.MAXLX2 == 0)) {
                init.MAXLX1 = 500 / 95;
                init.MAXLX2 = 500 % 95;
            }
            if (((init.MAXLX1 * 95) + init.MAXLX2) > KermitInit.DEFAULT_BLOCK_SIZE) {
                init.MAXLX1 = KermitInit.DEFAULT_BLOCK_SIZE / 95;
                init.MAXLX2 = KermitInit.DEFAULT_BLOCK_SIZE % 95;
            }
        }

    }

    /**
     * Encode the KermitInit object to a SendInit data field
     */
    @Override
    protected void writeToData() {
        data = new byte[18];
        data[0] = Encoder.toChar(init.MAXL);
        data[1] = Encoder.toChar(init.TIME);
        data[2] = Encoder.toChar(init.NPAD);
        data[3] = Encoder.ctl(init.PADC);
        data[4] = Encoder.toChar(init.EOL);
        data[5] = init.QCTL;
        data[6] = init.QBIN;
        data[7] = init.CHKT;
        data[8] = init.REPT;
        data[9] = Encoder.toChar(init.CAPAS);
        // Long packets
        data[10] = Encoder.toChar(init.WINDO);
        data[11] = Encoder.toChar(init.MAXLX1);
        data[12] = Encoder.toChar(init.MAXLX2);
        // Checkpointing - never implemented in the protocol
        data[13] = '0';
        data[14] = '_';
        data[15] = '_';
        data[16] = '_';
        data[17] = Encoder.toChar(init.WHATAMI);
    }

}
