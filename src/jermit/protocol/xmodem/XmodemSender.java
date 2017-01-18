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

/**
 * XmodemSender uploads one file using the Xmodem protocol.
 */
public class XmodemSender implements Runnable {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The Xmodem session state.
     */
    private XmodemSession session;

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

        File file = new File(filename);
        LocalFile localFile = new LocalFile(file);
        session = new XmodemSession(flavor, input, output, localFile, false);

        try {
            session.setTransferDirectory(file.getCanonicalFile().
                getParentFile().getPath());
        } catch (IOException e) {
            // SQUASH
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
        FileInfoModifier setFile;

        synchronized (session) {
            session.setState(SerialFileTransferSession.State.TRANSFER);
            file = session.getCurrentFile();
            setFile = session.getCurrentFileInfoModifier();
            setFile.setStartTime(System.currentTimeMillis());
            session.setStartTime(System.currentTimeMillis());
        }

        if (DEBUG) {
            System.err.println("uploadFile() BEGIN");
        }


        InputStream fileInput = null;

        try {

            // Get the transfer begun.  This may end up changing the Xmodem
            // flavor.
            session.startUpload();

            if (session.cancelFlag != 0) {
                // The session did not start correctly, bail out here.
                return;
            }

            // Open the file.  Local try/catch to separate the read error
            // message from the generic network I/O error message.
            try {
                fileInput = file.getLocalFile().getInputStream();
            } catch (IOException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                session.abort("UNABLE TO READ FROM FILE " +
                    file.getLocalName());
                session.cancelFlag = 1;
                return;
            }

            // Now that we have a transfer agreed to, and the file open, we
            // can update the file's total information for the UI.
            synchronized (session) {
                setFile.setBlockSize(session.getBlockSize());
                setFile.setBytesTotal(file.getLocalFile().getLength());
                setFile.setBlocksTotal(file.getBytesTotal() /
                    session.getBlockSize());
                if (file.getBlocksTotal() * session.getBlockSize() <
                    file.getBytesTotal()
                ) {
                    setFile.setBlocksTotal(file.getBlocksTotal() + 1);
                }
                session.setBytesTotal(file.getBytesTotal());
            }

            // Send the file blocks.
            while (session.cancelFlag == 0) {
                if (session.sendNextBlock(fileInput) == false) {
                    session.sendEOT();
                    break;
                }
            }

            // Transfer is complete!
            if (session.cancelFlag == 0) {
                synchronized (session) {
                    setFile.setEndTime(System.currentTimeMillis());
                }
                session.addInfoMessage("SUCCESS");
            }

        } catch (EOFException e) {
            session.abort("UNEXPECTED END OF TRANSMISSION");
            session.cancelFlag = 1;
            // Fall through to the fileInput.close().
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            session.abort("NETWORK I/O ERROR");
            session.cancelFlag = 1;
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
            if (session.cancelFlag == 0) {
                if (session.getState() == SerialFileTransferSession.State.TRANSFER) {
                    // This is the success exit point.  Transfer was not
                    // aborted or cancelled.
                    session.setState(SerialFileTransferSession.State.END);
                    session.setEndTime(System.currentTimeMillis());
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
            if (session.getCurrentFile() != null) {
                FileInfoModifier setFile = session.getCurrentFileInfoModifier();
                setFile.setEndTime(System.currentTimeMillis());
            }
            session.cancelTransfer(keepPartial);
            if (session.input.getStream() instanceof TimeoutInputStream) {
                ((TimeoutInputStream) session.input.getStream()).cancelRead();
            }
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
