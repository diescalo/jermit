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
package jermit.protocol;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.LinkedList;

import jermit.io.EOFInputStream;
import jermit.io.ReadTimeoutException;

/**
 * XmodemSession encapsulates all the state used by an upload or download
 * using the Xmodem protocol.
 */
public class XmodemSession extends SerialFileTransferSession {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The NAK byte used to request a packet repeat.
     */
    private static final byte NAK = 0x15;

    /**
     * The ACK byte used to acknowledge an OK packet.
     */
    private static final byte ACK = 0x06;

    /**
     * The SOH byte used to flag a 128-byte block.
     */
    private static final byte SOH = 0x01;

    /**
     * The STX byte used to flag a 1024-byte block.
     */
    private static final byte STX = 0x02;

    /**
     * The EOT byte used to end a transfer.
     */
    private static final byte EOT = 0x04;

    /**
     * The CAN byte used to forcefully terminate a transfer.
     */
    private static final byte CAN = 0x18;

    /**
     * Xmodem supports several variants.  These constants can be used to
     * select among them.
     */
    public enum Flavor {
        /**
         * Vanilla Xmodem: 128 byte blocks, checksum, 10-second timeout.
         */
        VANILLA,

        /**
         * Xmodem Relaxed: 128 byte blocks, checksum, 100-second timeout.
         */
        RELAXED,

        /**
         * Xmodem-CRC: 128 byte blocks, a 16-bit CRC, 10-second timeout.
         */
        CRC,

        /**
         * Xmodem-1k: 1024 byte blocks, a 16-bit CRC, 10-second timeout.
         */
        X_1K,

        /**
         * Xmodem-1k/G: 1024 byte blocks, a 16-bit CRC, 10-second timeout, no
         * ACKs.
         */
        X_1K_G,
    }

    /**
     * The type of Xmodem transfer to perform.
     */
    private Flavor flavor = Flavor.VANILLA;

    /**
     * The current sequence number.
     */
    private int sequenceNumber = 1;

    /**
     * Xmodem CRC routine was transliterated from XYMODEM.DOC.
     *
     * @param data the data bytes to perform the CRC against
     * @return the 16-bit CRC
     */
    private int crc16(byte [] data) {
        int crc = 0;
        for (int i = 0; i < data.length; i++) {
            crc = crc ^ (((int) data[i]) << 8);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc = crc << 1;
                }
            }
        }
        return (crc & 0xFFFF);
    }

    /**
     * Trim the CPM EOF byte (0x1A) from the end of a file.
     *
     * @param file the file to trim
     * @throws IOException if a java.io operation throws
     */
    public void trimEOF(final File file) throws IOException {
        // SetLength() requires the file be open in read-write.
        RandomAccessFile contents = new RandomAccessFile(file, "rw");
        while (contents.length() > 0) {
            contents.seek(contents.length() - 1);
            int ch = contents.read();
            if (ch == 0x1A) {
                contents.setLength(contents.length() - 1);
            } else {
                // Found a non-EOF byte
                break;
            }
        }
    }

    /**
     * Purge the input stream and send NAK to the remote side.
     *
     * @param input the stream to read from
     * @param output the stream to write to
     * @throws IOException if a java.io operation throws
     */
    private void purge(final InputStream input,
        final OutputStream output) throws IOException {

        if (DEBUG) {
            System.out.println("PURGE");
        }

        // Purge whatever is there, and try it all again.
        input.skip(input.available());

        // Send NAK
        output.write(NAK);
        output.flush();
    }

    /**
     * Ack the packet.
     *
     * @param input the stream to read from
     * @param output the stream to write to
     * @throws IOException if a java.io operation throws
     */
    private void ack(final InputStream input,
        final OutputStream output) throws IOException {

        if (DEBUG) {
            System.out.println("ACK");
        }

        // Send ACK
        output.write(ACK);
        output.flush();
    }

    /**
     * Abort the transfer.
     *
     * @param output the stream to write to
     * @throws IOException if a java.io operation throws
     */
    private synchronized void abort(final OutputStream output)
        throws IOException {

        state = State.ABORT;

        // Send CAN
        output.write(CAN);
        output.flush();
    }

    /**
     * Read one Xmodem packet from the stream.
     *
     * @param input the stream to read from
     * @param output the stream to write to
     * @return the raw bytes of the file contents
     * @throws IOException if a java.io operation throws
     */
    public byte [] getPacket(final EOFInputStream input,
        final OutputStream output) throws IOException {

        // Packet format:
        //
        //   0   - SOH or STX
        //   1   - Seq
        //   2   - 255 - Seq
        //   3   - [ ... data ... ]
        //   N   - checksum or CRC

        int blockSize = 128;

        // Keep reading until we get a valid packet.
        for (;;) {

            try {

                if (DEBUG) {
                    System.out.println("Calling input.read()");
                }

                int blockType = input.read();

                if (DEBUG) {
                    System.out.printf("blockType: 0x%02x\n", blockType);
                }

                if (blockType == STX) {
                    blockSize = 1024;
                } else if (blockType == SOH) {
                    blockSize = 128;
                } else if (blockType == EOT) {
                    // Normal end of transmission.
                    return new byte[0];
                } else if (blockType == CAN) {
                    // The remote side has cancelled the transfer.
                    addErrorMessage("TRANSFER CANCELLED BY SENDER");
                    abort(output);
                    return new byte[0];
                } else {
                    addErrorMessage("HEADER ERROR IN BLOCK #" + sequenceNumber);
                    purge(input, output);
                    continue;
                }

                // We got SOH/STX.  Now read the sequence number and its
                // complement.
                int seqByte = input.read();
                if ((seqByte & 0xFF) == ((sequenceNumber - 1) & 0xFF)) {
                    addErrorMessage("DUPLICATE BLOCK #" + sequenceNumber);
                    purge(input, output);
                    continue;
                }
                if (seqByte != (sequenceNumber % 256)) {
                    addErrorMessage("BAD BLOCK NUMBER IN BLOCK #" + sequenceNumber);
                    purge(input, output);
                    continue;
                }

                int compSeqByte = input.read();
                if ((255 - compSeqByte) != (sequenceNumber % 256)) {
                    addErrorMessage("COMPLIMENT BYTE BAD IN BLOCK #" +
                        sequenceNumber);
                    purge(input, output);
                    continue;
                }

                if (DEBUG) {
                    System.out.printf("SEQ: 0x%02x %d\n", sequenceNumber,
                        sequenceNumber);
                }

                // Now read the data.  Grab only up to blockSize.
                int blockReadN = 0;
                byte [] data = new byte[blockSize];
                while (blockReadN < data.length) {
                    int rc = input.read(data, blockReadN,
                        blockSize - blockReadN);
                    blockReadN += rc;
                }

                // Finally, check the checksum or CRC.
                if ((flavor == Flavor.VANILLA) || (flavor == Flavor.RELAXED)) {
                    // Checksum
                    int checksum = 0;
                    for (int i = 0; i < data.length; i++) {
                        int ch = ((int) data[i]) & 0xFF;
                        checksum += ch;
                    }
                    checksum = checksum & 0xFF;

                    int given = input.read();
                    if (checksum != given) {
                        addErrorMessage("CHECKSUM ERROR IN BLOCK #" +
                            sequenceNumber);
                        purge(input, output);
                        continue;
                    }

                    // Good checksum, OK!
                    sequenceNumber++;
                    if (flavor != Flavor.X_1K_G) {
                        // Send ACK
                        ack(input, output);
                    }
                    return data;
                }

                // CRC
                int crc = crc16(data);
                int given = input.read();
                int given2 = input.read();
                given = given << 8;
                given |= given2;

                if (crc != given) {
                    addErrorMessage("CRC ERROR IN BLOCK #" +
                        sequenceNumber);
                    purge(input, output);
                    continue;
                }

                if (DEBUG) {
                    System.out.printf("Good CRC: 0x%04x\n", (given & 0xFFFF));
                }

                // Good CRC, OK!
                sequenceNumber++;
                if (flavor != Flavor.X_1K_G) {
                    ack(input, output);
                }
                return data;

            } catch (ReadTimeoutException e) {
                addErrorMessage("TIMEOUT");
                purge(input, output);
                continue;

            } catch (EOFException e) {
                addErrorMessage("UNEXPECTED END OF TRANSMISSION");
                abort(output);
                return new byte[0];
            }

        } // for (;;)

        // We should never get here.

    }

    /**
     * Get the timeout for this flavor of Xmodem.
     *
     * @return the number of millis for this flavor of Xmodem
     */
    public int getTimeout() {
        if (flavor == Flavor.RELAXED) {
            // Relaxed: 100 seconds
            return 100 * 1000;
        }
        // All others: 10 seconds
        return 10 * 1000;
    }

    /**
     * Send the appropriate "NAK/ACK" character for this flavor of Xmodem.
     *
     * @param output the stream to write to
     * @throws IOException if a java.io operation throws
     */
    public void sendNCG(final OutputStream output) throws IOException {
        switch (flavor) {
        case VANILLA:
        case RELAXED:
            // NAK
            output.write(NAK);
            break;
        case CRC:
        case X_1K:
            // 'C'
            output.write(0x43);
            break;
        case X_1K_G:
            // 'G'
            output.write(0x47);
            break;
        }
        output.flush();
    }

    /**
     * Construct an instance to represent a file upload.
     *
     * @param flavor the Xmodem flavor to use
     * @param uploadFiles list of files to upload
     * @throws IllegalArgumentException if uploadFiles contains more than one
     * entry
     */
    public XmodemSession(final Flavor flavor,
        final LinkedList<FileInfo> uploadFiles) {

        super(uploadFiles);
        if (uploadFiles.size() != 1) {
            throw new IllegalArgumentException("Xmodem can only upload one " +
                "file at a time");
        }
        this.flavor = flavor;
    }

    /**
     * Construct an instance to represent a single file upload or download.
     *
     * @param flavor the Xmodem flavor to use
     * @param file path to one file on the local filesystem
     * @param download If true, this session represents a download.  If
     * false, it represents an upload.
     */
    public XmodemSession(final Flavor flavor, final File file,
        final boolean download) {

        super(file, download);
        this.flavor = flavor;
    }

    /**
     * Cancel this entire file transfer.  The session state will become
     * ABORT.
     *
     * @param keepPartial If true, save whatever has been collected if this
     * was a download.  If false, delete the file.
     */
    public void cancelTransfer(boolean keepPartial) {
        // TODO
    }

    /**
     * Skip this file and move to the next file in the transfer.  Note that
     * this does nothing for Xmodem.
     *
     * @param keepPartial If true, save whatever has been collected if this
     * was a download.  If false, delete the file.
     */
    public void skipFile(boolean keepPartial) {
        // Do nothing.  Xmodem cannot skip a file.
    }

}
