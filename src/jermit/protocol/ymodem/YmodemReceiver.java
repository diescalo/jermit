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
package jermit.protocol.ymodem;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import jermit.io.EOFInputStream;
import jermit.io.LocalFile;
import jermit.io.LocalFileInterface;
import jermit.io.ReadTimeoutException;
import jermit.io.TimeoutInputStream;
import jermit.protocol.FileInfo;
import jermit.protocol.FileInfoModifier;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.xmodem.XmodemReceiver;
import jermit.protocol.xmodem.XmodemSession;

/**
 * YmodemReceiver downloads one file using the Ymodem protocol.
 */
public class YmodemReceiver extends XmodemReceiver implements Runnable {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * Get the session.
     *
     * @return the session for this transfer
     */
    public YmodemSession getSession() {
        return (YmodemSession) session;
    }

    /**
     * Construct an instance to download multiple files using existing I/O
     * Streams.
     *
     * @param yFlavor the Ymodem flavor to use
     * @param input a stream that receives bytes sent by a Ymodem file sender
     * @param output a stream to sent bytes to a Ymodem file sender
     * @param pathname the path to write received files to
     * @param overwrite if true, permit writing to files even if they already
     * exist
     */
    public YmodemReceiver(final YmodemSession.YFlavor yFlavor,
        final InputStream input, final OutputStream output,
        final String pathname, final boolean overwrite) {

        super((yFlavor == YmodemSession.YFlavor.VANILLA ?
                XmodemSession.Flavor.X_1K : XmodemSession.Flavor.X_1K_G),
            input, output);

        session = new YmodemSession(yFlavor, input, output, pathname,
            overwrite);
    }

    /**
     * Construct an instance to download multiple files using existing I/O
     * Streams.
     *
     * @param input a stream that receives bytes sent by a Ymodem file sender
     * @param output a stream to sent bytes to a Ymodem file sender
     * @param pathname the path to write received files to
     */
    public YmodemReceiver(final InputStream input, final OutputStream output,
        final String pathname) {

        this(YmodemSession.YFlavor.VANILLA, input, output, pathname, false);
    }

    /**
     * Perform a file download using the Ymodem protocol.  Any exceptions
     * thrown will be emitted to System.err.
     */
    public void run() {
        // Cast the XmodemSession to YmodemSession to gain access to its
        // protected methods.
        YmodemSession session = getSession();

        // Start with init.
        session.setCurrentStatus("INIT");

        for (;;) {
            if (DEBUG) {
                System.out.println("Sending NCG...");
            }

            if (session.sendNCG() == false) {
                // Failed to handshake, bail out.
                break;
            }

            // Read block 0, setup new file or terminate.
            if (session.readBlock0() == false) {
                // No more files to transfer.
                break;
            }

            // Download the file.
            downloadFile();

        } // for (;;)

        // Switch to the next file.
        if (session.getState() == SerialFileTransferSession.State.FILE_DONE) {
            // This is the success exit point for one file.  Transfer was not
            // aborted or cancelled.
            session.setState(SerialFileTransferSession.State.END);
            session.setEndTime(System.currentTimeMillis());
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
        // Cast the XmodemSession to YmodemSession to gain access to its
        // protected methods.
        YmodemSession session = getSession();

        synchronized (session) {
            if (session.getCurrentFile() != null) {
                FileInfoModifier setFile = session.getCurrentFileInfoModifier();
                setFile.setEndTime(System.currentTimeMillis());
            }
            session.cancelTransfer(keepPartial);
            if (session.getInput().getStream() instanceof TimeoutInputStream) {
                ((TimeoutInputStream) session.getInput().
                    getStream()).cancelRead();
            }
        }
    }

    /**
     * Skip this file and move to the next file in the transfer.  Note that
     * this does nothing for Ymodem.
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
