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

/**
 * KermitSession encapsulates all the state used by an upload or download
 * using the Kermit protocol.
 */
public class KermitSession extends SerialFileTransferSession {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

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
     * Get the batchable flag.
     *
     * @return If true, this protocol can transfer multiple files.  If false,
     * it can only transfer one file at a time.
     */
    @Override
    public boolean isBatchable() {
        return true;
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
        return "Kermit";
    }

    /**
     * Get the block size.  Each protocol can have several variants.
     *
     * @return the block size
     */
    public int getBlockSize() {
        // TODO
        return 128;
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

        // TODO
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

        // TODO
    }

    /**
     * Construct an instance to represent a single file upload.
     *
     * @param input a stream that receives bytes sent by another Kermit
     * instance
     * @param output a stream to sent bytes to another Kermit instance
     * @param uploadFiles list of files to upload
     * @throws IllegalArgumentException if uploadFiles contains more than one
     * entry
     */
    public KermitSession(final InputStream input, final OutputStream output,
        final List<String> uploadFiles) {

        super(uploadFiles);
        this.output             = output;
        this.currentFile        = -1;

        if (input instanceof TimeoutInputStream) {
            // Someone has already set the timeout.  Keep their value.
            this.input  = new EOFInputStream(input);
        } else {
            // Use the default value of 10 seconds.
            this.input  = new EOFInputStream(new TimeoutInputStream(input,
                    10 * 1000));
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
     * this does nothing for Kermit.
     *
     * @param keepPartial If true, save whatever has been collected if this
     * was a download.  If false, delete the file.
     */
    public void skipFile(boolean keepPartial) {
        // TODO
    }

    /**
     * Create a FileInfoModifier for the current file being transferred.
     * This is used for KermitSender and KermitReceiver to get write access
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
