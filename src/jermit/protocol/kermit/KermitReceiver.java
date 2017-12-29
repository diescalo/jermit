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

import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import jermit.io.TimeoutInputStream;
import jermit.protocol.FileInfo;
import jermit.protocol.FileInfoModifier;
import jermit.protocol.SerialFileTransferSession;

/**
 * KermitReceiver downloads one or more files using the Kermit protocol.
 */
public class KermitReceiver implements Runnable {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The Kermit session state.
     */
    private KermitSession session;

    /**
     * The name of the current file to download.  This will either come from
     * a File or a FileAttributes packet.
     */
    private String downloadFilename;

    /**
     * The stream to write file data to.
     */
    private OutputStream fileOutput = null;

    /**
     * The current file being downloaded.
     */
    private FileInfo file = null;

    /**
     * The current file being downloaded properties setter.
     */
    private FileInfoModifier setFile = null;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Construct an instance to download multiple files using existing I/O
     * Streams.
     *
     * @param input a stream that receives bytes sent by a Kermit file sender
     * @param output a stream to sent bytes to a Kermit file sender
     * @param pathname the path to write received files to
     * @param overwrite if true, permit writing to files even if they already
     * exist
     */
    public KermitReceiver(final InputStream input, final OutputStream output,
        final String pathname, final boolean overwrite) {

        session = new KermitSession(input, output, pathname, overwrite);
    }

    /**
     * Construct an instance to download multiple files using existing I/O
     * Streams.
     *
     * @param input a stream that receives bytes sent by a Kermit file sender
     * @param output a stream to sent bytes to a Kermit file sender
     * @param pathname the path to write received files to
     */
    public KermitReceiver(final InputStream input, final OutputStream output,
        final String pathname) {

        this(input, output, pathname, false);
    }

    // ------------------------------------------------------------------------
    // Runnable ---------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Perform a file download using the Kermit protocol.  Any exceptions
     * thrown will be emitted to System.err.
     */
    public void run() {
        // Start with init.
        session.setCurrentStatus("INIT");
        session.setStartTime(System.currentTimeMillis());

        boolean done = false;

        while ((session.cancelFlag == 0) && (done == false)) {

            try {

                switch (session.kermitState) {

                case INIT:
                    session.kermitState = KermitState.KM_R;
                    break;

                case KM_R:
                    receiveBegin();
                    break;

                case KM_RW:
                    receiveBeginWait();
                    break;

                case KM_RF:
                    receiveFile();
                    break;

                case KM_RA:
                    receiveFileAttributes();
                    break;

                case KM_RDW:
                    receiveData();
                    break;

                case COMPLETE:
                    done = true;
                    break;

                default:
                    // TODO: red flag a programming error
                    break;

                } // switch (session.kermitState)

            } catch (EOFException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                try {
                    session.timeout();
                } catch (IOException e2) {
                    if (DEBUG) {
                        e2.printStackTrace();
                    }
                    session.abort("NETWORK I/O ERROR DURING TIMEOUT");
                    session.cancelFlag = 1;
                }
            } catch (IOException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                session.abort("NETWORK I/O ERROR");
                session.cancelFlag = 1;
            }

        } // while ((session.cancelFlag == 0) && (done == false))

        // Switch to the next file.
        synchronized (session) {
            if (done) {
                session.addInfoMessage("ALL FILES TRANSFERRED");

                // This is the success exit point for a batch.  Transfer was
                // not aborted or cancelled.
                session.setState(SerialFileTransferSession.State.END);
                session.setEndTime(System.currentTimeMillis());
            }
        }

    }

    // ------------------------------------------------------------------------
    // KermitReceiver ---------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Send a NAK(0) to prompt the other side to send a Send-Init packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void receiveBegin() throws IOException {
        if (DEBUG) {
            System.err.println("receiveBegin() sending NAK(0)...");
        }
        session.sendPacket(new NakPacket(0));
        session.kermitState = KermitState.KM_RW;
        session.setCurrentStatus("SENDING NAK(0)");
    }

    /**
     * Wait for the Send-Init packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void receiveBeginWait() throws EOFException, IOException {
        if (DEBUG) {
            System.err.println("receiveBeginWait() waiting for Send-Init...");
        }

        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error.  For this state, we can simply respond with
            // NAK(0).  For other states, we need to NAK the correct sequence
            // number.
            session.sendPacket(new NakPacket(0));
            session.setCurrentStatus("SENDING NAK(0)");
        } else if (packet instanceof SendInitPacket) {
            // We got the remote side's Send-Init
            session.setCurrentStatus("SEND-INIT");
            session.transferParameters.remote.setSendInit(packet);

            // Figure out the negotiated session values
            session.transferParameters.active.negotiate(
                session.transferParameters.local,
                session.transferParameters.remote);

            // ACK it
            session.sendPacket(session.transferParameters.local.getAckPacket());
            session.sequenceNumber++;

            // Move to the next state
            session.kermitState = KermitState.KM_RF;
        } else if (packet instanceof ErrorPacket) {
            // Remote side signalled error
            session.abort(((ErrorPacket) packet).errorMessage);
            session.cancelFlag = 1;
        } else {
            // Something else came in I'm not looking for.  This will always
            // be a protocol error.
            session.abort("PROTOCOL ERROR");
            session.cancelFlag = 1;
        }
    }

    /**
     * Wait for a File-Header packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void receiveFile() throws EOFException, IOException {
        if (DEBUG) {
            System.err.println("receiveFile() waiting for File...");
        }

        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error of some kind.  Respond with a NAK.
            session.sendPacket(new NakPacket(session.sequenceNumber));
        } else if (packet instanceof FilePacket) {
            FilePacket filePacket = (FilePacket) packet;
            // Got the remote side's File.
            session.setCurrentStatus("FILE");
            if (DEBUG) {
                System.err.printf("Receive file: '%s'\n", filePacket.filename);
            }
            downloadFilename = filePacket.filename;

            // ACK it
            session.sendAck(packet.seq);
            session.sequenceNumber++;

            // Move to the next state
            session.kermitState = KermitState.KM_RA;

        } else if (packet instanceof BreakPacket) {
            if (DEBUG) {
                System.err.println("Break session received");
            }

            // ACK it
            session.sendAck(packet.seq);
            session.sequenceNumber++;

            // Transfer has ended
            synchronized (session) {
                setFile.setEndTime(System.currentTimeMillis());
                session.addInfoMessage("SUCCESS");
                session.setState(SerialFileTransferSession.State.FILE_DONE);
            }
            session.kermitState = KermitState.COMPLETE;
        } else if (packet instanceof ErrorPacket) {
            // Remote side signalled error
            session.abort(((ErrorPacket) packet).errorMessage);
            session.cancelFlag = 1;
        } else {
            // Something else came in I'm not looking for
            session.abort("PROTOCOL ERROR");
            session.cancelFlag = 1;
        }
    }

    /**
     * Wait for a File-Attributes packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void receiveFileAttributes() throws EOFException, IOException {
        if (DEBUG) {
            System.err.println("receiveFile() waiting for Attributes...");
        }

        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error of some kind.  Respond with a NAK.
            session.sendPacket(new NakPacket(session.sequenceNumber));
        } else if (packet instanceof FileAttributesPacket) {
            FileAttributesPacket fileAttributes = (FileAttributesPacket) packet;
            // Got the remote side's File-Attributes.  Use this information
            // to open the file.
            session.setCurrentStatus("ATTRIBUTES");
            if (DEBUG) {
                System.err.println("Got attributes packet");
            }

            if (fileAttributes.filename == null) {
                fileAttributes.filename = downloadFilename;
            }
            session.openDownloadFile(fileAttributes.filename,
                fileAttributes.fileModTime, fileAttributes.fileSize);
            synchronized (session) {
                file = session.getCurrentFile();
                setFile = session.getCurrentFileInfoModifier();
                fileOutput = file.getLocalFile().getOutputStream();
            }

            // ACK it
            session.sendAck(packet.seq);
            session.sequenceNumber++;

            // Move to the next state
            session.kermitState = KermitState.KM_RDW;

        } else if (packet instanceof FileDataPacket) {
            // The remote side didn't send a File-Attributes packet.  We need
            // to open the file using the filename from the File-Header
            // packet, and then save this packet's data to it.

            session.setCurrentStatus("DATA");

            // First, move to the next state.
            session.kermitState = KermitState.KM_RDW;

            session.openDownloadFile(downloadFilename, -1, -1);
            synchronized (session) {
                file = session.getCurrentFile();
                setFile = session.getCurrentFileInfoModifier();
                fileOutput = file.getLocalFile().getOutputStream();
            }

            // ACK it
            session.sendAck(packet.seq);
            session.sequenceNumber++;

            // Now save this packet.
            receiveSaveData((FileDataPacket) packet);
        } else if (packet instanceof ErrorPacket) {
            // Remote side signalled error
            session.abort(((ErrorPacket) packet).errorMessage);
            session.cancelFlag = 1;
        } else {
            // Something else came in I'm not looking for
            session.abort("PROTOCOL ERROR");
            session.cancelFlag = 1;
        }
    }

    /**
     * Wait for a Data packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void receiveData() throws EOFException, IOException {
        if (DEBUG) {
            System.err.println("receiveData() waiting for Data...");
        }

        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error of some kind.  Respond with a NAK.
            session.sendPacket(new NakPacket(session.sequenceNumber));
        } else if (packet instanceof FileDataPacket) {
            session.setCurrentStatus("DATA");

            if (session.transferParameters.active.streaming == true) {
                // Don't ACK when streaming
                if (DEBUG) {
                    System.err.printf("  -- streaming, don't ack SEQ %d --\n",
                        packet.seq);
                }
            } else {
                // ACK it
                session.sendAck(packet.seq);
            }
            session.sequenceNumber++;

            // Save this packet.
            receiveSaveData((FileDataPacket) packet);
        } else if (packet instanceof EofPacket) {
            session.setCurrentStatus("EOF");

            // ACK it
            session.sendAck(packet.seq);
            session.sequenceNumber++;

            try {
                fileOutput.close();
            } catch (IOException e) {
                // SQUASH
                if (DEBUG) {
                    e.printStackTrace();
                }
            }

            if (file.getModTime() > 0) {
                if (DEBUG) {
                    System.err.println("Setting file mod time to " +
                        (new java.util.Date(file.getModTime())));
                }

                try {
                    file.getLocalFile().setTime(file.getModTime());
                } catch (IOException e) {
                    if (DEBUG) {
                        System.err.println("Warning: error updating file time");
                        e.printStackTrace();
                    }
                }
            }

            // TODO: set file protection

            // Move to the next state
            session.kermitState = KermitState.KM_RF;
        } else if (packet instanceof ErrorPacket) {
            // Remote side signalled error
            session.abort(((ErrorPacket) packet).errorMessage);
            session.cancelFlag = 1;
        } else {
            // Something else came in I'm not looking for
            session.abort("PROTOCOL ERROR");
            session.cancelFlag = 1;
        }
    }

    /**
     * Save a Data packet to file.
     *
     * @throws IOException if a java.io operation throws
     */
    private void receiveSaveData(final FileDataPacket data) throws IOException {
        if (DEBUG) {
            System.err.println("receiveSaveData() saving to file...");
        }

        try {
            fileOutput.write(data.data);

            // Update stats
            synchronized (session) {
                session.lastBlockSize = data.data.length;
                setFile.setBlockSize(data.data.length);
                setFile.setBytesTransferred(file.getBytesTransferred() +
                    data.data.length);
                session.setBytesTransferred(session.getBytesTransferred() +
                    data.data.length);
                setFile.setBlocksTransferred(file.getBytesTransferred() /
                    session.getBlockSize());
                session.setBlocksTransferred(session.getBytesTransferred() /
                    session.getBlockSize());
                session.setLastBlockMillis(System.currentTimeMillis());
                setFile.setBlocksTotal(file.getBytesTotal() /
                    file.getBlockSize());
            }

        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            session.abort("UNABLE TO WRITE TO FILE " + file.getLocalName());
            return;
        }
    }

    /**
     * Get the session.
     *
     * @return the session for this transfer
     */
    public KermitSession getSession() {
        return session;
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
            if (session.getInput().getStream() instanceof TimeoutInputStream) {
                ((TimeoutInputStream) session.getInput().
                    getStream()).cancelRead();
            }
        }
    }

    /**
     * Skip this file and move to the next file in the transfer.
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
