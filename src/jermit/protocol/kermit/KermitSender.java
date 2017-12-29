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
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import jermit.io.TimeoutInputStream;
import jermit.protocol.FileInfo;
import jermit.protocol.FileInfoModifier;
import jermit.protocol.SerialFileTransferSession;

/**
 * KermitSender downloads one file using the Kermit protocol.
 */
public class KermitSender implements Runnable {

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
     * The stream to read file data from.
     */
    private InputStream fileInput = null;

    /**
     * The current position in the file.
     */
    private long filePosition = 0;

    /**
     * The current file being uploaded.
     */
    private FileInfo file = null;

    /**
     * The current file being uploaded properties setter.
     */
    private FileInfoModifier setFile = null;

    /**
     * Index in files of the file currently being transferred in this
     * session.
     */
    private int currentFile = -1;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Construct an instance to upload multiple files using existing I/O
     * Streams.
     *
     * @param input a stream that receives bytes sent by a Kermit file sender
     * @param output a stream to sent bytes to a Kermit file sender
     * @param uploadFiles list of files to upload
     */
    public KermitSender(final InputStream input, final OutputStream output,
        final List<String> uploadFiles) {

        session = new KermitSession(input, output, uploadFiles);
    }

    /**
     * Construct an instance to upload one file using existing I/O Streams.
     *
     * @param input a stream that receives bytes sent by a Kermit file sender
     * @param output a stream to sent bytes to a Kermit file sender
     * @param uploadFile the file name to upload
     */
    public KermitSender(final InputStream input, final OutputStream output,
        final String uploadFile) {

        List<String> uploadFiles = new ArrayList<String>();
        uploadFiles.add(uploadFile);

        session = new KermitSession(input, output, uploadFiles);
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

                if (DEBUG) {
                    System.err.println("run() kermitState = " +
                        session.kermitState);
                }

                switch (session.kermitState) {

                case INIT:
                    session.kermitState = KermitState.KM_S;
                    break;

                case KM_S:
                    sendBegin();
                    break;

                case KM_SW:
                    sendBeginWait();
                    break;

                case KM_SF:
                    sendFile();
                    break;

                case KM_SFW:
                    sendFileWait();
                    break;

                case KM_SA:
                    sendFileAttributes();
                    break;

                case KM_SAW:
                    sendFileAttributesWait();
                    break;

                case KM_SDW:
                    sendData();
                    break;

                case KM_SZ:
                    sendEof();
                    break;

                case KM_SZW:
                    sendEofWait();
                    break;

                case KM_SB:
                    sendBreak();
                    break;

                case KM_SBW:
                    sendBreakWait();
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
            } catch (KermitCancelledException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
                session.abort("CANCELLED BY REMOTE SIDE");
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
    // KermitSender -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Send the Send-Init packet to start things off.
     *
     * @throws IOException if a java.io operation throws
     */
    private void sendBegin() throws IOException {
        if (DEBUG) {
            System.err.println("sendBegin() sending Send-Init...");
        }
        session.sendPacket(session.transferParameters.local.getSendInit());
        session.kermitState = KermitState.KM_SW;
        session.setCurrentStatus("SENDING SEND-INIT");
    }

    /**
     * Wait for the receiver's Send-Init ACK packet.
     *
     * @throws IOException if a java.io operation throws
     * @throws KermitCancelledException if three Ctrl-C's are encountered in
     * a row
     */
    private void sendBeginWait() throws EOFException, IOException,
                                        KermitCancelledException {

        if (DEBUG) {
            System.err.println("sendBeginWait() waiting for Send-Init ACK...");
        }

        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error.  For this state, we can simply respond with
            // the Send-Init again.
            session.resendLastPacket();
            session.setCurrentStatus("SENDING SEND-INIT");
        } else if (packet instanceof NakPacket) {
            // This is probably the NAK(0) that kicks us off.  Resend the
            // Send-Init.
            session.resendLastPacket();
            session.setCurrentStatus("SENDING SEND-INIT");
        } else if (packet instanceof AckPacket) {
            // We got the remote side's ACK to our Send-Init
            session.sequenceNumber++;
            session.transferParameters.remote.setSendInit(packet);

            // Figure out the negotiated session values
            session.transferParameters.active.negotiate(
                session.transferParameters.local,
                session.transferParameters.remote);

            // Move to the next state
            session.kermitState = KermitState.KM_SF;
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
     * Send the File packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void sendFile() throws IOException {
        if (DEBUG) {
            System.err.println("sendFile() opening file...");
        }
        synchronized (session) {
            currentFile++;
            if (currentFile == session.getFiles().size()) {
                if (DEBUG) {
                    System.err.println("No more files");
                }
                // End of transfer.
                session.kermitState = KermitState.KM_SB;
                return;
            }
            session.setCurrentFile(currentFile);
            session.setState(SerialFileTransferSession.State.TRANSFER);
            file = session.getCurrentFile();
            setFile = session.getCurrentFileInfoModifier();
            setFile.setStartTime(System.currentTimeMillis());
        }

        // Open the file.  Local try/catch to separate the read error
        // message from the generic network I/O error message.
        try {
            fileInput = file.getLocalFile().getInputStream();
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            session.abort("UNABLE TO READ FROM FILE " + file.getLocalName());
            session.cancelFlag = 1;
            return;
        }

        // Now that we have a transfer agreed to, and the file open, we can
        // update the file's total information for the UI.
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

        // Put together the File packet.
        String filename = file.getLocalName();
        if (DEBUG) {
            System.err.printf("Next file to upload: '%s' size %d\n",
                filename, file.getLocalFile().getLength());
        }
        String filePart = (new File(filename)).getName();
        KermitInit active = session.transferParameters.active;
        FilePacket packet = new FilePacket(active.checkType,
            session.sequenceNumber);
        packet.filename = filePart;
        filePosition = 0;
        session.sendPacket(packet);
        session.setCurrentStatus("SENDING FILE");

        // Move to the next state
        session.kermitState = KermitState.KM_SFW;
    }

    /**
     * Wait for the receiver's File ACK packet.
     *
     * @throws IOException if a java.io operation throws
     * @throws KermitCancelledException if three Ctrl-C's are encountered in
     * a row
     */
    private void sendFileWait() throws EOFException, IOException,
                                       KermitCancelledException {

        if (DEBUG) {
            System.err.println("sendFileWait() waiting for File ACK...");
        }
        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error.  Resend the last packet.
            session.resendLastPacket();
            session.setCurrentStatus("SENDING FILE");
        } else if (packet instanceof AckPacket) {
            // We got the remote side's ACK to our File
            session.sequenceNumber++;

            // Move to the next state
            KermitInit active = session.transferParameters.active;
            if (active.attributes) {
                session.kermitState = KermitState.KM_SA;
            } else {
                session.kermitState = KermitState.KM_SDW;
            }
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
     * Send the FileAttributes packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void sendFileAttributes() throws IOException {
        if (DEBUG) {
            System.err.println("sendFileAttributes() sending attributes...");
        }

        // Put together the FileAttributes packet.
        KermitInit active = session.transferParameters.active;
        FileAttributesPacket packet = new FileAttributesPacket(active.checkType,
            session.sequenceNumber);

        packet.fileSize = file.getSize();
        packet.fileSizeK = file.getSize() / 1024;
        packet.fileModTime = file.getModTime();
        packet.filePosition = filePosition;
        packet.fileProtection = file.getLocalFile().getProtection();
        packet.doResend = active.doResend;
        if (System.getProperty("jermit.kermit.upload.forceBinary",
                "true").equals("false")
        ) {
            packet.textMode = file.getLocalFile().isText();
        } else {
            packet.textMode = false;
        }
        session.sendPacket(packet);
        session.setCurrentStatus("SENDING ATTRIBUTES");

        // Move to the next state
        session.kermitState = KermitState.KM_SAW;
    }

    /**
     * Wait for the receiver's FileAttributes ACK packet.
     *
     * @throws IOException if a java.io operation throws
     * @throws KermitCancelledException if three Ctrl-C's are encountered in
     * a row
     */
    private void sendFileAttributesWait() throws EOFException, IOException,
                                                 KermitCancelledException {

        if (DEBUG) {
            System.err.println("sendFileAttributesWait() waiting for " +
                "Attributes ACK...");
        }
        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error.  Resend the last packet.
            session.resendLastPacket();
            session.setCurrentStatus("SENDING ATTRIBUTES");
        } else if (packet instanceof AckPacket) {
            // We got the remote side's ACK to our File-Attributes
            session.sequenceNumber++;

            // Move to the next state
            session.kermitState = KermitState.KM_SDW;
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
     * Send the Data packet, and maybe look for a corresponding ACK.
     *
     * @throws IOException if a java.io operation throws
     * @throws KermitCancelledException if three Ctrl-C's are encountered in
     * a row
     */
    private void sendData() throws IOException, KermitCancelledException {

        if (DEBUG) {
            System.err.printf("sendFileData() sending data packet at %d\n",
                filePosition);
        }

        KermitInit active = session.transferParameters.active;

        // Read another packet's worth from the file and send it out.
        // session.sendPacket() will call packet.encode() which performs the
        // actual reading.  After the packet has hit the wire, we will know
        // how many bytes were sent.
        FileDataPacket dataPacket = new FileDataPacket(active.checkType,
            fileInput, 0, session.sequenceNumber);
        session.sendPacket(dataPacket);
        session.setCurrentStatus("DATA");
        long outstandingBytes = dataPacket.position;
        filePosition += outstandingBytes;

        // Check for the ACK to this data.
        long ackedBytes = 0;
        if (active.streaming) {
            if (DEBUG) {
                System.err.println("streaming: NO ACK");
            }
            // We don't need to wait on the ack.
            ackedBytes = outstandingBytes;
            session.sequenceNumber++;
        } else {
            Packet packet = session.getPacket();
            if (packet.parseState != Packet.ParseState.OK) {
                // We had an error.  Resend the last packet.
                session.resendLastPacket();
                session.setCurrentStatus("DATA");
            } else if (packet instanceof AckPacket) {
                // We got the remote side's ACK to our File-Attributes
                session.sequenceNumber++;

                ackedBytes = outstandingBytes;
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

        // Increment stats.
        synchronized (this) {
            setFile.setBytesTransferred(file.getBytesTransferred() +
                ackedBytes);
            setFile.setBlocksTransferred(file.getBytesTransferred() /
                session.getBlockSize());
            session.setBlocksTransferred(session.getBytesTransferred() /
                session.getBlockSize());
            session.setBytesTransferred(session.getBytesTransferred() +
                ackedBytes);
            session.setLastBlockMillis(System.currentTimeMillis());
        }

        // If this was our last packet, switch to EOF.
        if (dataPacket.eof) {
            session.kermitState = KermitState.KM_SZ;
            synchronized (session) {
                setFile.setBlocksTransferred(file.getBlocksTotal());
            }
        }
    }

    /**
     * Send the EOF packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void sendEof() throws IOException {
        if (DEBUG) {
            System.err.println("sendEof() sending EOF...");
        }
        KermitInit active = session.transferParameters.active;
        EofPacket packet = new EofPacket(active.checkType,
            session.sequenceNumber);
        session.sendPacket(packet);
        session.kermitState = KermitState.KM_SZW;
        session.setCurrentStatus("SENDING EOF");
    }

    /**
     * Wait for the receiver's EOF ACK packet.
     *
     * @throws IOException if a java.io operation throws
     * @throws KermitCancelledException if three Ctrl-C's are encountered in
     * a row
     */
    private void sendEofWait() throws EOFException, IOException,
                                      KermitCancelledException {

        if (DEBUG) {
            System.err.println("sendEofWait() waiting for EOF ACK...");
        }
        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error.  Resend the last packet.
            session.resendLastPacket();
            session.setCurrentStatus("SENDING EOF");
        } else if (packet instanceof AckPacket) {
            // We got the remote side's ACK to our EOF
            session.sequenceNumber++;

            // All done, close file.
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

            // Transfer is complete!
            synchronized (session) {
                setFile.setEndTime(System.currentTimeMillis());
            }
            session.addInfoMessage("SUCCESS");

            // Move to the next state
            session.kermitState = KermitState.KM_SF;
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
     * Send the BREAK (EOT) packet.
     *
     * @throws IOException if a java.io operation throws
     */
    private void sendBreak() throws IOException {
        if (DEBUG) {
            System.err.println("sendBreak() sending BREAK...");
        }
        KermitInit active = session.transferParameters.active;
        BreakPacket packet = new BreakPacket(active.checkType,
            session.sequenceNumber);
        session.sendPacket(packet);
        session.kermitState = KermitState.KM_SBW;
        session.setCurrentStatus("SENDING BREAK");
    }

    /**
     * Wait for the receiver's BREAK ACK packet.
     *
     * @throws IOException if a java.io operation throws
     * @throws KermitCancelledException if three Ctrl-C's are encountered in
     * a row
     */
    private void sendBreakWait() throws EOFException, IOException,
                                        KermitCancelledException {

        if (DEBUG) {
            System.err.println("sendBreakWait() waiting for BREAK ACK...");
        }
        Packet packet = session.getPacket();
        if (packet.parseState != Packet.ParseState.OK) {
            // We had an error.  Resend the last packet.
            session.resendLastPacket();
            session.setCurrentStatus("SENDING BREAK");
        } else if (packet instanceof AckPacket) {
            // We got the remote side's ACK to our BREAK.  ALL DONE!
            session.sequenceNumber++;

            if (DEBUG) {
                System.err.println("ALL TRANSFERS COMPLETE");
            }
            session.kermitState = KermitState.COMPLETE;
            session.setCurrentStatus("COMPLETE");
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
