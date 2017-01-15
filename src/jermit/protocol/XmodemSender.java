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
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import jermit.io.EOFInputStream;
import jermit.io.LocalFile;
import jermit.io.LocalFileInterface;
import jermit.io.ReadTimeoutException;
import jermit.io.TimeoutInputStream;

/**
 * XmodemSender uploads one file using the Xmodem protocol.
 */
public class XmodemSender implements Runnable {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The bytes received from the remote side.
     */
    private InputStream input;

    /**
     * The bytes sent to the remote side.
     */
    private OutputStream output;

    /**
     * The Xmodem session state.
     */
    private XmodemSession session;

    /**
     * If true, this transfer has been cancelled by user request.
     */
    private boolean cancel = false;

    /**
     * Get the session.
     *
     * @return the session for this transfer
     */
    public XmodemSession getSession() {
        return session;
    }

    /**
     * Construct an instance to upload a file using existing I/O Streams.
     *
     * @param flavor the Xmodem flavor to use
     * @param input a stream that receives bytes sent by an Xmodem file
     * receiver
     * @param output a stream to sent bytes to an Xmodem file receiver
     * @param filename the file to write the data to
     * @throws IllegalArgumentException if filename already exists and
     * overwrite is false
     */
    public XmodemSender(final XmodemSession.Flavor flavor,
        final InputStream input, final OutputStream output,
        final String filename) {

        LocalFile localFile = new LocalFile(new File(filename));

        this.output     = output;
        session         = new XmodemSession(flavor, localFile, false);
        if (input instanceof TimeoutInputStream) {
            // Someone has already set the timeout.  Keep their value.
            this.input  = new EOFInputStream(input);
        } else {
            // Use the default value for this flavor of Xmodem.
            this.input  = new EOFInputStream(new TimeoutInputStream(input,
                    session.getTimeout()));
        }
    }

    /**
     * Construct an instance to upload a file using existing I/O Streams.
     *
     * @param input a stream that receives bytes sent by an Xmodem file
     * receiver
     * @param output a stream to sent bytes to an Xmodem file receiver
     * @param filename the file to write the data to
     * @throws IllegalArgumentException if filename already exists
     */
    public XmodemSender(final InputStream input, final OutputStream output,
        String filename) {

        this(XmodemSession.Flavor.VANILLA, input, output, filename);
    }

    /**
     * Perform a file upload using the Xmodem protocol.  Any exceptions
     * thrown will be emitted to System.err.
     */
    public void run() {
        uploadFile();
    }

    /**
     * Read the requested transfer type and downgrade accordingly.  Note that
     * EOFException will not be caught here.
     *
     * @param input a stream that receives bytes sent by an Xmodem file
     * receiver
     * @param output a stream to sent bytes to an Xmodem file receiver
     * @throws IOException if a java.io operation throws
     */
    private void startTransfer(final InputStream input,
        final OutputStream output) throws IOException {

        int flavorType = -1;
        int timeoutCount = 0;

        while (cancel == false) {

            try {

                // Get the requested transfer type from the receiver.
                flavorType = input.read();

                if (DEBUG) {
                    System.err.printf("Flavor: 0x%02x '%c'\n", flavorType,
                        flavorType);
                }

                if (flavorType == session.NAK) {
                    // Vanilla Xmodem - doesn't matter what we think it is.
                    // Downgrade if needed, but send the first packet.
                    if (session.getFlavor() != XmodemSession.Flavor.VANILLA) {
                        session.addErrorMessage("DOWNGRADE TO VANILLA XMODEM");
                        session.setFlavor(XmodemSession.Flavor.VANILLA);
                    }
                } else if (flavorType == 'C') {
                    if ((session.getFlavor() != XmodemSession.Flavor.CRC) &&
                        (session.getFlavor() != XmodemSession.Flavor.X_1K)
                    ) {
                        // They have asked for CRC, but we were specified
                        // with something else.  If we were specified with
                        // vanilla, stay on that.  Otherwise, default to CRC.
                        if (session.getFlavor() == XmodemSession.Flavor.VANILLA) {
                            // Wait for the receiver to fallback to vanilla.
                            continue;
                        } else {
                            // Downgrade to CRC and send the first packet.
                            session.addErrorMessage("DOWNGRADE TO XMODEM-CRC");
                            session.setFlavor(XmodemSession.Flavor.CRC);
                        }
                    }
                } else if (flavorType == 'G') {
                    if (session.getFlavor() != XmodemSession.Flavor.X_1K_G) {
                        // They have asked for 1K/G, but we were specified
                        // with something else.  Wait for the receiver to
                        // fallback to something else.
                        continue;
                    }
                } else {
                    // We don't know what this is.  Wait for the receiver to
                    // try again with something we know.
                    continue;
                }

            } catch (ReadTimeoutException e) {
                session.timeout(output);
                continue;
            }

            // At this point, we have flavorType set to one of three values:
            //
            // 1. NAK - We should be on Vanilla Xmodem.
            // 2. 'C' - We should be on CRC or 1K.
            // 3. 'G' - We should be on 1K/G.
            if (flavorType == session.NAK) {
                assert (session.getFlavor() == XmodemSession.Flavor.VANILLA);
            }
            if (flavorType == 'C') {
                assert ((session.getFlavor() == XmodemSession.Flavor.CRC) ||
                    (session.getFlavor() == XmodemSession.Flavor.X_1K));
            }
            if (flavorType == 'G') {
                assert (session.getFlavor() == XmodemSession.Flavor.X_1K_G);
            }
            break;

        } // while (cancel == false)

    }

    /**
     * Send the next data block to the remote side.  Note that EOFException
     * will not be caught here.
     *
     * @param input a stream that receives bytes sent by an Xmodem file
     * receiver
     * @param output a stream to sent bytes to an Xmodem file receiver
     * @param fileInput a stream that can read bytes from the local file
     * @return true if the transfer should keep going, false if the file is
     * at EOF.
     * @throws IOException if a java.io operation throws
     */
    private boolean sendNextBlock(final InputStream input,
        final OutputStream output, final FileInfo file,
        final InputStream fileInput) throws IOException {

        // If true, definitely read data from the file.  If false, we are
        // re-trying the last sent block.
        boolean loadBlock = true;

        byte [] data = null;

        while (cancel == false) {

            try {

                if (DEBUG) {
                    System.err.printf("SEQ: 0x%02x %d\n",
                        session.sequenceNumber, session.sequenceNumber);
                }

                // Send the next data packet, wait for ACK.
                if (loadBlock == true) {
                    data = session.readFileBlock(fileInput);
                    if (DEBUG) {
                        System.err.printf("Read %d bytes from file\n",
                            (data == null ? -1 : data.length));
                    }
                }

                if (data == null) {
                    return false;
                }

                // Got a file block, send it.
                if (data.length == 128) {
                    output.write(session.SOH);
                } else {
                    output.write(session.STX);
                }
                output.write(session.sequenceNumber % 256);
                output.write((255 - session.sequenceNumber) % 256);
                output.write(data);
                session.writeChecksum(output, data);

                int ackByte = input.read();
                if (ackByte == session.ACK) {
                    if (DEBUG) {
                        System.err.println("ACK received");
                    }
                    // All good, increment sequence and set to read the next
                    // block.
                    session.sequenceNumber++;
                    loadBlock = true;
                    session.consecutiveErrors = 0;

                    // Update stats
                    synchronized (session) {
                        file.blocksTransferred      += 1;
                        file.blocksTotal            += 1;
                        file.bytesTransferred       += data.length;
                        file.bytesTotal             += data.length;
                        session.blocksTransferred   += 1;
                        session.blocksTotal         += 1;
                        session.bytesTransferred    += data.length;
                        session.bytesTotal          += data.length;
                    }

                } else if (ackByte == session.CAN) {
                    if (DEBUG) {
                        System.err.println("*** CAN ***");
                    }

                    // Receiver has cancelled.
                    session.addErrorMessage("TRANSFER CANCELLED BY RECEIVER");
                    session.abort(output);
                    fileInput.close();
                    // cancel is now true, so return true to break the outer
                    // loop.
                    return true;
                } else {
                    if (DEBUG) {
                        System.err.println("! NAK " + session.bytesTransferred);
                    }

                    session.consecutiveErrors++;
                    if (session.consecutiveErrors == 10) {
                        // Cancel this transfer.
                        session.abort(output);

                        // cancel is now true, so return true to break the
                        // outer loop.
                        return true;
                    }

                    // We either got NAK or something we don't expect.
                    // Resend the block.
                    loadBlock = false;
                }

            } catch (ReadTimeoutException e) {
                session.timeout(output);

                // Send the packet again.
                continue;
            }

        } // while (cancel == false)

        // We can send another block.
        return true;
    }

    /**
     * Perform a file upload using the Xmodem protocol.
     */
    public void uploadFile() {
        // Xmodem can be done as a state machine, but is actually much easier
        // to do as a direct procedure:
        //
        //   1. Wait for the requested protocol (called the NCGbyte in some
        //      documents).
        //
        //   2. Send packets.
        //
        //   3. Send the EOT.

        FileInfo file;

        synchronized (session) {
            session.state = SerialFileTransferSession.State.TRANSFER;
            file = session.getCurrentFile();
            file.startTime = System.currentTimeMillis();
            session.startTime = System.currentTimeMillis();
        }

        if (DEBUG) {
            System.err.println("uploadFile() BEGIN");
        }


        InputStream fileInput = null;

        try {

            // Get the transfer begun.  This may end up changing the Xmodem
            // flavor.
            startTransfer(input, output);

            // Open the file.  Local try/catch to separate the read error
            // message from the generic network I/O error message.
            try {
                fileInput = file.localFile.getInputStream();
            } catch (IOException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                session.addErrorMessage("UNABLE TO READ FROM FILE " +
                    file.getLocalName());
                session.abort(output);
                return;
            }

            // Send the file blocks.
            while (cancel == false) {
                if (sendNextBlock(input, output, file, fileInput) == false) {

                    // We are at EOF, send the EOT and be done.
                    output.write(session.EOT);
                    output.flush();
                    break;
                }
            } // while (cancel == false)

            // Transfer is complete!

        } catch (EOFException e) {
            if (DEBUG) {
                System.err.println("UNEXPECTED END OF TRANSMISSION");
            }
            session.addErrorMessage("UNEXPECTED END OF TRANSMISSION");
            session.abort(output);
            // Fall through to the fileInput.close().
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            session.addErrorMessage("NETWORK I/O ERROR");
            session.abort(output);
            // Fall through to the fileInput.close().
        }

        // All done.
        if (fileInput != null) {
            try {
                fileInput.close();
            } catch (IOException e) {
                // SQUASH
                if (DEBUG) {
                    e.printStackTrace();
                }
            }
            fileInput = null;
        }

        // Transfer has ended
        synchronized (session) {
            if (cancel == false) {
                if (session.state == SerialFileTransferSession.State.TRANSFER) {
                    // This is the success exit point.  Transfer was not
                    // aborted or cancelled.
                    session.state = SerialFileTransferSession.State.END;
                    session.endTime = System.currentTimeMillis();
                }
            }
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
        synchronized (session) {
            session.cancelTransfer(keepPartial);
            cancel = true;
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
        synchronized (session) {
            session.skipFile(keepPartial);
        }
    }

}
