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
 * XmodemReceiver downloads one file using the Xmodem protocol.
 */
public class XmodemReceiver implements Runnable {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The Xmodem session state.
     */
    protected XmodemSession session;

    /**
     * Get the session.
     *
     * @return the session for this transfer
     */
    public XmodemSession getSession() {
        return session;
    }

    /**
     * Construct an instance to download multiple files using existing I/O
     * Streams.
     *
     * @param flavor the Xmodem flavor to use
     * @param input a stream that receives bytes sent by an Xmodem file
     * sender
     * @param output a stream to sent bytes to an Xmodem file sender
     */
    protected XmodemReceiver(final XmodemSession.Flavor flavor,
        final InputStream input, final OutputStream output) {

        session = new XmodemSession(flavor, input, output, true);
    }

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
    public XmodemReceiver(final XmodemSession.Flavor flavor,
        final InputStream input, final OutputStream output,
        final String filename, final boolean overwrite) {

        File file = new File(filename);
        if ((file.exists() == true) && (overwrite == false)) {
            throw new IllegalArgumentException(filename + " already exists, " +
                "will not overwrite");
        }

        LocalFile localFile = new LocalFile(file);
        session = new XmodemSession(flavor, input, output, localFile, true);

        try {
            session.setTransferDirectory(file.getCanonicalFile().
                getParentFile().getPath());
        } catch (IOException e) {
            // SQUASH
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
    public XmodemReceiver(final InputStream input, final OutputStream output,
        final String filename) {

        this(XmodemSession.Flavor.VANILLA, input, output, filename, false);
    }

    /**
     * Perform a file download using the Xmodem protocol.  Any exceptions
     * thrown will be emitted to System.err.
     */
    public void run() {
        // Start with init.
        session.setCurrentStatus("INIT");

        // We will download only the first file.
        synchronized (session) {
            session.setState(SerialFileTransferSession.State.TRANSFER);
            session.setCurrentFile(0);
        }

        // Download the file.
        downloadFile();

        // Note that the session is over.
        if (session.getState() == SerialFileTransferSession.State.FILE_DONE) {
            // This is the success exit point.  Transfer was not aborted or
            // cancelled.
            session.setState(SerialFileTransferSession.State.END);
            session.setEndTime(System.currentTimeMillis());
        }
    }

    /**
     * Perform a file download using the Xmodem protocol.
     */
    public void downloadFile() {
        // Xmodem can be done as a state machine, but is actually much easier
        // to do as a direct procedure:
        //
        //   1. Send the requested protocol (called the NCGbyte in some
        //      documents).
        //
        //   2. Read packets until the EOT.
        //
        //   3. Save file.

        FileInfo file;
        FileInfoModifier setFile;

        synchronized (session) {
            file = session.getCurrentFile();
            setFile = session.getCurrentFileInfoModifier();
            setFile.setStartTime(System.currentTimeMillis());
            session.setStartTime(file.getStartTime());
        }

        OutputStream fileOutput = null;
        try {
            fileOutput = file.getLocalFile().getOutputStream();
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            session.abort("UNABLE TO WRITE TO FILE " + file.getLocalName());
            return;
        }

        if (DEBUG) {
            System.out.println("Sending NCG...");
        }

        if (session.sendNCG() == false) {
            session.cancelFlag = 1;
        }

        while (session.cancelFlag == 0) {

            if (session.cancelFlag != 0) {
                session.abort("CANCELLED BY USER");
                continue;
            }

            if (DEBUG) {
                System.out.println("Calling getPacket()");
            }

            byte [] data = null;
            try {
                data = session.getPacket();
            } catch (IOException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                session.abort("NETWORK I/O ERROR");
                break;
            }

            if (DEBUG) {
                System.out.println("Read " + data.length + " bytes");
            }

            synchronized (session) {
                if (session.getState() == SerialFileTransferSession.State.ABORT) {
                    // Transfer was aborted for whatever reason.
                    break;
                }
            }

            session.setCurrentStatus("DATA");

            if (data.length == 0) {
                // This is EOT.  Close the file first, then trim the
                // trailing CPM EOF's (0x1A) in it.
                if (file.getLocalFile() instanceof LocalFile) {
                    // We know that this is wrapping a file, hence we can
                    // trimEOF() it.
                    session.trimEOF(file.getLocalFile().getLocalName());
                }
                break;
            }

            try {
                // Save data
                fileOutput.write(data);
            } catch (IOException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                session.abort("CANNOT WRITE TO FILE " + file.getLocalName());
                break;
            }

            // Update stats
            synchronized (session) {
                setFile.setBlocksTransferred(file.getBlocksTransferred() + 1);
                setFile.setBlocksTotal(file.getBlocksTotal() + 1);
                setFile.setBytesTransferred(file.getBytesTransferred() +
                    data.length);
                setFile.setBytesTotal(file.getBytesTotal() + data.length);
                session.setBlocksTransferred(session.getBlocksTransferred() +
                    1);
                session.setBytesTransferred(session.getBytesTransferred() +
                    data.length);
                session.setBytesTotal(session.getBytesTotal() + data.length);
                session.setLastBlockMillis(System.currentTimeMillis());
            }

        } // while (session.cancelFlag == 0)

        if (fileOutput != null) {
            try {
                fileOutput.close();
            } catch (IOException e) {
                // SQUASH
                if (DEBUG) {
                    e.printStackTrace();
                }
            }
            fileOutput = null;
        }

        // Transfer has ended
        synchronized (session) {
            if (session.cancelFlag == 0) {
                // Success!
                setFile.setEndTime(System.currentTimeMillis());
                session.addInfoMessage("SUCCESS");
                session.setState(SerialFileTransferSession.State.FILE_DONE);
            }
            if (session.cancelFlag == 2) {
                // Transfer was cancelled, and we need to unlink the file.
                file.getLocalFile().delete();
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
