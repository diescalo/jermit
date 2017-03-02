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
package jermit.protocol.xmodem;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;

import jermit.io.EOFInputStream;
import jermit.io.LocalFileInterface;
import jermit.io.ReadTimeoutException;
import jermit.io.TimeoutInputStream;
import jermit.protocol.FileInfo;
import jermit.protocol.FileInfoModifier;
import jermit.protocol.Protocol;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.ymodem.YmodemSession;

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
    public static final byte NAK = 0x15;

    /**
     * The ACK byte used to acknowledge an OK packet.
     */
    public static final byte ACK = 0x06;

    /**
     * The SOH byte used to flag a 128-byte block.
     */
    public static final byte SOH = 0x01;

    /**
     * The STX byte used to flag a 1024-byte block.
     */
    public static final byte STX = 0x02;

    /**
     * The EOT byte used to end a transfer.
     */
    public static final byte EOT = 0x04;

    /**
     * The CAN byte used to forcefully terminate a transfer.
     */
    public static final byte CAN = 0x18;

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
     * The current sequence number.
     */
    protected int sequenceNumber = 1;

    /**
     * The number of consecutive errors.  After 10 errors, the transfer is
     * cancelled.
     */
    protected int consecutiveErrors = 0;

    /**
     * If 0, nothing was cancelled.  If 1, cancel and keep partial (default
     * when receiver cancels).  If 2, cancel and do not keep partial.
     */
    protected int cancelFlag = 0;

    /**
     * The bytes received from the remote side.
     */
    protected EOFInputStream input;

    /**
     * The bytes sent to the remote side.
     */
    private OutputStream output;

    /**
     * The type of Xmodem transfer to perform.
     */
    private Flavor flavor = Flavor.VANILLA;

    /**
     * Get the type of Xmodem transfer to perform.
     *
     * @return the Xmodem flavor
     */
    public Flavor getFlavor() {
        return flavor;
    }

    /**
     * Set the current status message.
     *
     * @param message the status message
     */
    protected synchronized void setCurrentStatus(final String message) {
        currentStatus = message;
    }

    /**
     * Set the directory that contains the file(s) of this transfer.
     *
     * @param transferDirectory the directory that contains the file(s) of
     * this transfer
     */
    protected void setTransferDirectory(final String transferDirectory) {
        this.transferDirectory = transferDirectory;
    }

    /**
     * Set the number of bytes transferred in this session.
     *
     * @param bytesTransferred the number of bytes transferred in this
     * session
     */
    protected void setBytesTransferred(final long bytesTransferred) {
        this.bytesTransferred = bytesTransferred;
    }

    /**
     * Set the number of bytes in total to transfer in this session.
     *
     * @param bytesTotal the number of bytes in total to transfer in this
     * session
     */
    protected void setBytesTotal(final long bytesTotal) {
        this.bytesTotal = bytesTotal;
    }

    /**
     * Set the number of blocks transferred in this session.
     *
     * @param blocksTransferred the number of blocks transferred in this
     * session
     */
    protected void setBlocksTransferred(final long blocksTransferred) {
        this.blocksTransferred = blocksTransferred;
    }

    /**
     * Set the time at which last block was sent or received.
     *
     * @param lastBlockMillis the time at which last block was sent or
     * received
     */
    protected void setLastBlockMillis(final long lastBlockMillis) {
        this.lastBlockMillis = lastBlockMillis;
    }

    /**
     * Set the time at which this session started transferring its first
     * file.
     *
     * @param startTime the time at which this session started transferring
     * its first file
     */
    protected void setStartTime(final long startTime) {
        this.startTime = startTime;
    }

    /**
     * Set the time at which this session completed transferring its last
     * file.
     *
     * @param endTime the time at which this session completed transferring
     * its last file
     */
    protected void setEndTime(final long endTime) {
        this.endTime = endTime;
    }

    /**
     * Set the state of this transfer.  Overridden to permit xmodem package
     * access.
     *
     * @param state one of the State enum values
     */
    @Override
    protected void setState(final State state) {
        super.setState(state);
    }

    /**
     * Get the protocol name.  Each protocol can have several variants.
     *
     * @return the protocol name for this transfer
     */
    public String getProtocolName() {
        switch (flavor) {
        case VANILLA:
            return "Xmodem";
        case RELAXED:
            return "Xmodem Relaxed";
        case CRC:
            return "Xmodem/CRC";
        case X_1K:
            return "Xmodem-1K";
        case X_1K_G:
            return "Xmodem-1K/G";
        }

        // Should never get here.
        throw new IllegalArgumentException("Xmodem flavor is not set " +
            "correctly");

    }

    /**
     * Get the block size.  Each protocol can have several variants.
     *
     * @return the block size
     */
    public int getBlockSize() {
        if ((flavor == Flavor.X_1K) || (flavor == Flavor.X_1K_G)) {
            return 1024;
        }
        return 128;
    }

    /**
     * Xmodem CRC routine was transliterated from XYMODEM.DOC.
     *
     * @param data the data bytes to perform the CRC against
     * @return the 16-bit CRC
     */
    protected int crc16(byte [] data) {
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
     * @param filename the name of the file to trim on the local filesystem
     */
    protected void trimEOF(final String filename) {
        try {
            // SetLength() requires the file be open in read-write.
            RandomAccessFile contents = new RandomAccessFile(filename, "rw");
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
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Count a timeout, cancelling the transfer if there are too many
     * consecutive errors.
     *
     * @throws IOException if a java.io operation throws
     */
    protected synchronized void timeout() throws IOException {

        if (DEBUG) {
            System.err.println("TIMEOUT");
        }
        addErrorMessage("TIMEOUT");

        FileInfo file = getCurrentFile();
        if (file != null) {
            // File can be null if we are still waiting to read block 0 for
            // Ymodem.
            FileInfoModifier setFile = getCurrentFileInfoModifier();
            setFile.setErrorCount(file.getErrorCount() + 1);
        }
        consecutiveErrors++;
        if (consecutiveErrors == 10) {
            // Cancel this transfer.
            abort("TOO MANY ERRORS");
            return;
        }
    }

    /**
     * Purge the input stream and send NAK to the remote side.
     *
     * @param message text message to pass to addErrorMessage()
     * @throws IOException if a java.io operation throws
     */
    private synchronized void purge(final String message) throws IOException {

        if (DEBUG) {
            System.err.println("PURGE: " + message);
        }
        addErrorMessage(message);

        // Purge whatever is there, and try it all again.
        input.skip(input.available());

        FileInfo file = getCurrentFile();
        if (file != null) {
            // File can be null if we are still waiting to read block 0 for
            // Ymodem.
            FileInfoModifier setFile = getCurrentFileInfoModifier();
            setFile.setErrorCount(file.getErrorCount() + 1);
        }
        consecutiveErrors++;
        if (consecutiveErrors == 10) {
            // Cancel this transfer.
            abort("TOO MANY ERRORS");
            return;
        }

        // Send NAK
        if (DEBUG) {
            System.err.println("NAK " + bytesTransferred);
        }

        if (sequenceNumber == 1) {
            // We are still trying to kick off the transfer, send NCGbyte
            // rather than NAK.
            sendNCG();
        } else {
            output.write(NAK);
        }
        output.flush();
    }

    /**
     * Ack the packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void ack() throws IOException {

        if (DEBUG) {
            System.err.println("ACK");
        }

        // Send ACK
        output.write(ACK);
        output.flush();
    }

    /**
     * Abort the transfer.
     *
     * @param message text message to pass to addErrorMessage()
     * @throws IOException if a java.io operation throws
     */
    protected synchronized void abort(final String message) {
        if (DEBUG) {
            System.err.println("ABORT: " + message);
        }
        addErrorMessage(message);

        setState(State.ABORT);

        // Send CAN, squashing any errors
        try {
            output.write(CAN);
            output.flush();
        } catch (IOException e) {
            // SQUASH
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Read one Xmodem packet from the stream.
     *
     * @return the raw bytes of the file contents
     * @throws IOException if a java.io operation throws
     */
    protected byte [] getPacket() throws IOException {

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
            boolean discard = false;

            try {

                if (DEBUG) {
                    System.err.println("Calling input.read()");
                }

                int blockType = input.read();

                if (DEBUG) {
                    System.err.printf("blockType: 0x%02x\n", blockType);
                }

                if (blockType == STX) {
                    blockSize = 1024;
                } else if (blockType == SOH) {
                    blockSize = 128;
                } else if (blockType == EOT) {
                    // Normal end of transmission.  ACK the EOT.
                    ack();
                    return new byte[0];
                } else if (blockType == CAN) {
                    // The remote side has cancelled the transfer.
                    abort("TRANSFER CANCELLED BY SENDER");
                    return new byte[0];
                } else {
                    purge("HEADER ERROR IN BLOCK #" + sequenceNumber);
                    continue;
                }

                // We got SOH/STX.  Now read the sequence number and its
                // complement.
                int seqByte = input.read();
                if (DEBUG) {
                    System.err.printf("seqByte: 0x%02x\n", seqByte);
                }
                if ((seqByte & 0xFF) == ((sequenceNumber - 1) & 0xFF)) {
                    addErrorMessage("DUPLICATE BLOCK #" + (sequenceNumber - 1));
                    if ((flavor == Flavor.X_1K_G) && (sequenceNumber == 2)) {
                        // The remote side is not honoring 1K/G mode.
                        // Downgrade to vanilla Xmodem 1K (switch NCGbyte to
                        // 'C').
                        addErrorMessage("DOWNGRADE TO XMODEM/1K");
                        flavor = Flavor.X_1K;
                    }
                    // Finish reading this block, and blindly ack it, but
                    // don't return it to the caller.
                    discard = true;
                } else if (seqByte != (sequenceNumber % 256)) {
                    purge("BAD BLOCK NUMBER IN BLOCK #" + sequenceNumber);
                    continue;
                }

                int compSeqByte = input.read();

                if (discard == false) {
                    if ((255 - compSeqByte) != (sequenceNumber % 256)) {
                        purge("COMPLIMENT BYTE BAD IN BLOCK #" +
                            sequenceNumber);
                        continue;
                    }

                    if (DEBUG) {
                        System.err.printf("SEQ: 0x%02x %d\n", sequenceNumber,
                            sequenceNumber);
                    }
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

                    if (discard == true) {
                        // This was a duplicate block, ACK it even if the
                        // data is crap.
                        if (flavor != Flavor.X_1K_G) {
                            // Send ACK
                            ack();
                        } else {
                            if (DEBUG) {
                                System.err.println("DUP checksum -G, no ack");
                            }
                        }
                        continue;
                    }

                    if (checksum != given) {
                        purge("CHECKSUM ERROR IN BLOCK #" + sequenceNumber);
                        continue;
                    }

                    // Good checksum, OK!
                    sequenceNumber++;
                    if (flavor != Flavor.X_1K_G) {
                        // Send ACK
                        ack();
                    } else {
                        if (DEBUG) {
                            System.err.println("checksum -G, no ack");
                        }
                    }
                    consecutiveErrors = 0;
                    return data;
                }

                // CRC
                int crc = crc16(data);
                int given = input.read();
                int given2 = input.read();
                given = given << 8;
                given |= given2;

                if (discard == true) {
                    // This was a duplicate block, ACK it even if the data is
                    // crap.
                    if (flavor != Flavor.X_1K_G) {
                        // Send ACK
                        ack();
                    } else {
                        if (DEBUG) {
                            System.err.println("DUP CRC -G, no ack");
                        }
                    }
                    continue;
                }

                if (crc != given) {
                    purge("CRC ERROR IN BLOCK #" + sequenceNumber);
                    continue;
                }

                if (DEBUG) {
                    System.err.printf("Good CRC: 0x%04x\n", (given & 0xFFFF));
                }

                // Good CRC, OK!
                sequenceNumber++;
                if (flavor != Flavor.X_1K_G) {
                    ack();
                } else {
                    if (DEBUG) {
                        System.err.println("CRC -G, no ack");
                    }
                }
                consecutiveErrors = 0;
                return data;

            } catch (ReadTimeoutException e) {
                if (this instanceof YmodemSession) {
                    if (DEBUG) {
                        System.err.println("Ymodem - kick it off: " +
                            getState());
                    }
                    if (getState() == State.FILE_INFO) {
                        // We are on block 0.  Need to re-send the 'C' or 'G'
                        // to get the Ymodem receive going.
                        sendNCG();
                    }
                    continue;
                }

                purge("TIMEOUT");
                if ((flavor == Flavor.X_1K_G) && (sequenceNumber == 2)) {
                    // The remote side is not honoring 1K/G mode.  Downgrade
                    // to vanilla Xmodem (switch NCGbyte to NAK).
                    addErrorMessage("DOWNGRADE TO XMODEM/1K");
                    flavor = Flavor.X_1K;
                }
                continue;

            } catch (EOFException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                abort("UNEXPECTED END OF TRANSMISSION");
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
    private int getTimeout() {
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
     * @return true if successful
     */
    protected boolean sendNCG() {
        try {
            switch (flavor) {
            case VANILLA:
            case RELAXED:
                // NAK
                output.write(NAK);
                break;
            case CRC:
            case X_1K:
                // 'C' - 0x43
                output.write('C');
                break;
            case X_1K_G:
                // 'G' - 0x47
                if (DEBUG) {
                    System.err.println("Requested -G");
                }
                output.write('G');
                break;
            }
            output.flush();
            return true;
        } catch (IOException e) {
            // We failed to get the byte out, all done.
            if (DEBUG) {
                e.printStackTrace();
            }
            abort("UNABLE TO SEND STARTING NAK");
            return false;
        }
    }

    /**
     * Compute the checksum or CRC and send that to the other side.
     *
     * @param data the block data
     * @throws IOException if a java.io operation throws
     */
    protected void writeChecksum(byte [] data) throws IOException {

        if ((flavor == Flavor.VANILLA) || (flavor == Flavor.RELAXED)) {
            // Checksum
            int checksum = 0;
            for (int i = 0; i < data.length; i++) {
                int ch = ((int) data[i]) & 0xFF;
                checksum += ch;
            }
            output.write(checksum & 0xFF);
        } else {
            // CRC
            int crc = crc16(data);
            output.write((crc >> 8) & 0xFF);
            output.write( crc       & 0xFF);
        }
        output.flush();
    }

    /**
     * Read a 128 or 1024 byte block from file.
     *
     * @param fileInput a stream reading from the local "file"
     * @return the bytes read, or null of the file is at EOF
     * @throws IOException if a java.io operation throws
     */
    protected byte [] readFileBlock(final InputStream fileInput)
        throws IOException {

        byte [] data = new byte[getBlockSize()];
        int rc = fileInput.read(data);
        if (rc == data.length) {
            return data;
        }
        if (rc == -1) {
            // EOF
            return null;
        }
        // We have a shorter-than-asked block.  For file streams this is
        // typically the very last block.  But if we have a different kind of
        // stream it could just be an incomplete read.  Read either a full
        // block, or definitely hit EOF.

        int blockN = rc;
        while (blockN < data.length) {
            rc = fileInput.read(data, blockN, data.length - blockN);
            if (rc == -1) {
                // Cool, EOF.  This will be the last block.
                if (blockN < 128) {
                    // We can use a shorter block, so do that.
                    byte [] shortBlock = new byte[128];
                    System.arraycopy(data, 0, shortBlock, 0, blockN);
                    data = shortBlock;
                }
                // Now pad it with CPM EOF.
                for (int i = blockN; i < data.length; i++) {
                    data[i] = 0x1A;
                }
                return data;
            }
            blockN += rc;
        }

        // We read several times, but now have a complete block.
        return data;
    }

    /**
     * Read the requested transfer type and downgrade accordingly.  Note that
     * EOFException will not be caught here.
     *
     * @throws IOException if a java.io operation throws
     */
    protected void startUpload() throws IOException {

        int flavorType = -1;
        int timeoutCount = 0;

        setCurrentStatus("FILE");

        while (cancelFlag == 0) {

            try {

                // Get the requested transfer type from the receiver.
                flavorType = input.read();

                if (DEBUG) {
                    System.err.printf("Flavor: 0x%02x '%c'\n", flavorType,
                        flavorType);
                }

                if (flavorType == NAK) {
                    // Vanilla Xmodem - doesn't matter what we think it is.
                    // Downgrade if needed, but send the first packet.
                    if (flavor != Flavor.VANILLA) {
                        addErrorMessage("DOWNGRADE TO VANILLA XMODEM");
                        flavor = Flavor.VANILLA;
                    }
                } else if (flavorType == 'C') {
                    if ((flavor != Flavor.CRC) &&
                        (flavor != Flavor.X_1K)
                    ) {
                        // They have asked for CRC, but we were specified
                        // with something else.  If we were specified with
                        // vanilla, stay on that.  Otherwise, default to CRC.
                        if (flavor == Flavor.VANILLA) {
                            // Wait for the receiver to fallback to vanilla.
                            continue;
                        } else {
                            // Downgrade to CRC and send the first packet.
                            if (this instanceof YmodemSession) {
                                addErrorMessage("DOWNGRADE TO VANILLA YMODEM");
                            } else {
                                addErrorMessage("DOWNGRADE TO XMODEM-CRC");
                            }
                            flavor = Flavor.CRC;
                        }
                    }
                } else if (flavorType == 'G') {
                    if (flavor != Flavor.X_1K_G) {
                        // They have asked for 1K/G, but we were specified
                        // with something else.  Wait for the receiver to
                        // fallback to something else.
                        continue;
                    }
                } else if (flavorType == CAN) {
                    // The receiver is trying to cancel.  Bail out here.
                    if (DEBUG) {
                        System.err.println("*** CAN ***");
                    }
                    abort("CANCELLED BY RECEIVER");
                    cancelFlag = 1;
                    return;
                } else {
                    // We don't know what this is.  Wait for the receiver to
                    // try again with something we know.
                    continue;
                }

            } catch (ReadTimeoutException e) {
                if (cancelFlag != 0) {
                    return;
                }
                timeout();
                continue;
            }

            // At this point, we have flavorType set to one of three values:
            //
            // 1. NAK - We should be on Vanilla Xmodem.
            // 2. 'C' - We should be on CRC or 1K.
            // 3. 'G' - We should be on 1K/G.
            if (flavorType == NAK) {
                assert (flavor == Flavor.VANILLA);
            }
            if (flavorType == 'C') {
                assert ((flavor == Flavor.CRC) || (flavor == Flavor.X_1K));
            }
            if (flavorType == 'G') {
                assert (flavor == Flavor.X_1K_G);
            }
            break;

        } // while (cancelFlag == 0)

    }

    /**
     * Send the next data block to the remote side.  Note that EOFException
     * will not be caught here.
     *
     * @param data the bytes for this block
     * @return true if the data made it out, false if an unrecoverable error
     * occurred.
     * @throws IOException if a java.io operation throws
     */
    protected boolean sendDataBlock(final byte [] data) throws IOException {

        while (cancelFlag == 0) {

            if (cancelFlag != 0) {
                abort("CANCELLED BY USER");
                cancelFlag = 1;
                continue;
            }

            try {

                if (DEBUG) {
                    System.err.printf("SEQ: 0x%02x %d\n",
                        sequenceNumber, sequenceNumber);
                }

                // Send the next data packet, wait for ACK.
                if (data.length == 128) {
                    output.write(SOH);
                } else {
                    output.write(STX);
                }
                output.write(sequenceNumber % 256);
                output.write((255 - sequenceNumber) % 256);
                output.write(data);
                writeChecksum(data);

                if ((flavor == Flavor.X_1K_G) &&
                    (getState() == SerialFileTransferSession.State.TRANSFER)
                ) {
                    // Assume that all is good, increment sequence for the
                    // next outgoing block.
                    sequenceNumber++;
                    consecutiveErrors = 0;
                    return true;
                }

                // Wait for an ACK.
                int ackByte = input.read();
                if (ackByte == ACK) {
                    if (DEBUG) {
                        System.err.println("ACK received");
                    }
                    // All good, increment sequence for the next outgoing
                    // block.
                    sequenceNumber++;
                    consecutiveErrors = 0;
                    return true;

                } else if (ackByte == CAN) {
                    if (DEBUG) {
                        System.err.println("*** CAN ***");
                    }

                    // Receiver has cancelled.
                    abort("TRANSFER CANCELLED BY RECEIVER");
                    cancelFlag = 1;
                    return false;
                } else {
                    if (DEBUG) {
                        System.err.println("! NAK " + bytesTransferred);
                    }

                    consecutiveErrors++;
                    if (consecutiveErrors == 10) {
                        // Cancel this transfer.
                        abort("TOO MANY ERRORS");
                        cancelFlag = 1;

                        return false;
                    }

                    // We either got NAK or something we don't expect.
                    // Resend the block.
                    continue;
                }
            } catch (ReadTimeoutException e) {
                if (cancelFlag != 0) {
                    return false;
                }
                timeout();

                // Send the packet again.
                continue;
            }

        } // while (cancelFlag == 0)

        // User cancelled somewhere down the line.
        return false;
    }

    /**
     * Send the next data block to the remote side.  Note that EOFException
     * will not be caught here.
     *
     * @param fileInput a stream that can read bytes from the local "file"
     * @return true if the transfer should keep going, false if the file is
     * at EOF.
     * @throws IOException if a java.io operation throws
     */
    protected boolean sendNextBlock(final InputStream fileInput)
        throws IOException {

        // Send the next data packet, wait for ACK.
        byte [] data = readFileBlock(fileInput);
        if (DEBUG) {
            System.err.printf("Read %d bytes from file\n",
                (data == null ? -1 : data.length));
        }

        if (data == null) {
            // No more file data, break out.
            return false;
        }

        if (sendDataBlock(data) == true) {
            // All good, increment stats.
            synchronized (this) {
                FileInfo file = getCurrentFile();
                FileInfoModifier setFile = getCurrentFileInfoModifier();
                setFile.setBlocksTransferred(file.getBlocksTransferred() + 1);
                setFile.setBytesTransferred(file.getBytesTransferred() +
                    data.length);
                blocksTransferred += 1;
                bytesTransferred  += data.length;
                lastBlockMillis    = System.currentTimeMillis();
            }

        } else {
            // Network I/O problem, or user cancelled.  Allow the fallthrough
            // to return true so that uploadFile() does not try to send the
            // EOT.
            fileInput.close();
        }

        // We can send another block.
        return true;
    }

    /**
     * Send EOT to the remote side and wait for its ACK.
     *
     * @throws IOException if a java.io operation throws
     */
    protected void sendEOT() throws IOException {

        while (cancelFlag == 0) {

            if (cancelFlag != 0) {
                abort("CANCELLED BY USER");
                continue;
            }

            try {
                // We are at EOF, send the EOT and wait for the final
                // ACK.
                output.write(EOT);
                output.flush();

                int ackByte = input.read();
                if (ackByte == ACK) {
                    if (DEBUG) {
                        System.err.println("ACK received");
                    }
                    return;

                } else if (ackByte == CAN) {
                    if (DEBUG) {
                        System.err.println("*** CAN EOT ***");
                    }

                    // Receiver has cancelled.
                    abort("TRANSFER CANCELLED BY RECEIVER");
                    cancelFlag = 1;
                    return;
                } else {
                    if (DEBUG) {
                        System.err.println("! NAK EOT");
                    }

                    consecutiveErrors++;
                    if (consecutiveErrors == 10) {
                        // Cancel this transfer.
                        abort("TOO MANY ERRORS");
                        cancelFlag = 1;
                        return;
                    }

                    // We either got NAK or something we don't expect.
                    // Resend the EOT.
                }

            } catch (ReadTimeoutException e) {
                if (cancelFlag != 0) {
                    return;
                }
                timeout();

                // Send the EOT again.
                continue;
            }
        } // while (cancelFlag == 0)
    }

    /**
     * Construct an instance to represent a single file upload.
     *
     * @param flavor the Xmodem flavor to use
     * @param input a stream that receives bytes sent by another Xmodem
     * instance
     * @param output a stream to sent bytes to another Xmodem instance
     * @param uploadFiles list of files to upload
     * @throws IllegalArgumentException if uploadFiles contains more than one
     * entry
     */
    public XmodemSession(final Flavor flavor, final InputStream input,
        final OutputStream output, final List<String> uploadFiles) {

        super(uploadFiles);
        if ((uploadFiles.size() != 1) && (!(this instanceof YmodemSession))) {
            // This is a bit ugly. :-( YmodemSession uses this constructor
            // (yay OOP), but it is also a public Xmodem constructor.  Check
            // to see if this is being used by Ymodem, and if so allow
            // multiple files.
            throw new IllegalArgumentException("Xmodem can only upload one " +
                "file at a time");
        }
        this.flavor      = flavor;
        this.protocol    = Protocol.XMODEM;
        this.output      = output;
        this.currentFile = -1;

        if (input instanceof TimeoutInputStream) {
            // Someone has already set the timeout.  Keep their value.
            this.input  = new EOFInputStream(input);
        } else {
            // Use the default value for this flavor of Xmodem.
            this.input  = new EOFInputStream(new TimeoutInputStream(input,
                    getTimeout()));
        }
    }

    /**
     * Construct an instance to represent a single file upload or download.
     *
     * @param flavor the Xmodem flavor to use
     * @param input a stream that receives bytes sent by another Xmodem
     * instance
     * @param output a stream to sent bytes to another Xmodem instance
     * @param file path to one file on the local filesystem
     * @param download If true, this session represents a download.  If
     * false, it represents an upload.
     */
    public XmodemSession(final Flavor flavor, final InputStream input,
        final OutputStream output, final LocalFileInterface file,
        final boolean download) {

        super(file, download);
        this.flavor      = flavor;
        this.protocol    = Protocol.XMODEM;
        this.output      = output;
        this.currentFile = 0;

        if (input instanceof TimeoutInputStream) {
            // Someone has already set the timeout.  Keep their value.
            this.input  = new EOFInputStream(input);
        } else {
            // Use the default value for this flavor of Xmodem.
            this.input  = new EOFInputStream(new TimeoutInputStream(input,
                    getTimeout()));
        }
    }

    /**
     * Construct an instance to represent a batch file upload or download.
     *
     * @param flavor the Xmodem flavor to use
     * @param input a stream that receives bytes sent by another Xmodem
     * instance
     * @param output a stream to sent bytes to another Xmodem instance
     * @param download If true, this session represents a download.  If
     * false, it represents an upload.
     */
    protected XmodemSession(final Flavor flavor, final InputStream input,
        final OutputStream output, final boolean download) {

        super(download);
        this.flavor      = flavor;
        this.protocol    = Protocol.XMODEM;
        this.output      = output;
        this.currentFile = -1;

        if (input instanceof TimeoutInputStream) {
            // Someone has already set the timeout.  Keep their value.
            this.input  = new EOFInputStream(input);
        } else {
            // Use the default value for this flavor of Xmodem.
            this.input  = new EOFInputStream(new TimeoutInputStream(input,
                    getTimeout()));
        }
    }

    /**
     * Cancel this entire file transfer.  The session state will become
     * ABORT.
     *
     * @param keepPartial If true, save whatever has been collected if this
     * was a download.  If false, delete the file.
     */
    public void cancelTransfer(boolean keepPartial) {
        synchronized (this) {
            setState(State.ABORT);
            if (keepPartial == true) {
                cancelFlag = 1;
            } else {
                cancelFlag = 2;
            }
            addErrorMessage("CANCELLED BY USER");
        }
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

    /**
     * Create a FileInfoModifier for the current file being transferred.
     * This is used for XmodemSender and XmodemReceiver to get write access
     * to the FileInfo fields.
     */
    protected FileInfoModifier getCurrentFileInfoModifier() {
        FileInfo file = getCurrentFile();
        return getFileInfoModifier(file);
    }

    /**
     * Add an INFO message to the messages list.  Overridden to permit xmodem
     * package access.
     *
     * @param message the message text
     */
    @Override
    protected synchronized void addInfoMessage(String message) {
        super.addInfoMessage(message);
    }

    /**
     * Add an ERROR message to the messages list.  Overridden to permit
     * xmodem package access.
     *
     * @param message the message text
     */
    @Override
    protected synchronized void addErrorMessage(String message) {
        super.addErrorMessage(message);
    }

    /**
     * Set the current file being transferred.  Overridden to permit xmodem
     * package access.
     *
     * @param currentFile the index in the files list
     */
    @Override
    protected synchronized void setCurrentFile(final int currentFile) {
        this.currentFile = currentFile;
    }

}
