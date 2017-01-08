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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import jermit.io.EOFInputStream;
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
     * Construct an instance to download a file using existing I/O Streams.
     *
     * @param flavor the Xmodem flavor to use
     * @param input a stream that receives bytes sent by an Xmodem file
     * sender
     * @param output a stream to sent bytes to an Xmodem file sender
     * @param filename the file to write the data to
     * @param overwrite if true, permit writing to filename even if it
     * already exists
     * @throws IllegalArgumentException if filename already exists and
     * overwrite is false
     */
    public XmodemSender(final XmodemSession.Flavor flavor,
        final InputStream input, final OutputStream output,
        final String filename, final boolean overwrite) {

        File file = new File(filename);
        if ((file.exists() == true) && (overwrite == false)) {
            throw new IllegalArgumentException(filename + " already exists, " +
                "will not overwrite");
        }
        this.output     = output;
        session         = new XmodemSession(flavor, file, false);
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
     * Construct an instance to download a file using existing I/O Streams.
     *
     * @param input a stream that receives bytes sent by an Xmodem file
     * sender
     * @param output a stream to sent bytes to an Xmodem file sender
     * @param filename the file to write the data to
     * @throws IllegalArgumentException if filename already exists
     */
    public XmodemSender(final InputStream input, final OutputStream output,
        String filename) {

        this(XmodemSession.Flavor.VANILLA, input, output, filename, false);
    }

    /**
     * Perform a file download using the Xmodem protocol.  Any exceptions
     * thrown will be emitted to System.err.
     */
    public void run() {
        try {
            transferFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Perform a file download using the Xmodem protocol.
     */
    public void transferFile() throws IOException {
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

        FileInputStream fileInput = new FileInputStream(file.localFile);

        while (cancel == false) {
            // TODO
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
        // TODO: send CAN
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
