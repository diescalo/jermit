/*
 * Jermit
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2018 Kevin Lamonte
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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import jermit.io.EOFInputStream;
import jermit.io.LocalFile;
import jermit.io.LocalFileInterface;
import jermit.protocol.FileInfo;
import jermit.protocol.FileInfoModifier;
import jermit.protocol.Protocol;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.xmodem.XmodemSession;

/**
 * YmodemSession encapsulates all the state used by an upload or download
 * using the Ymodem protocol.
 */
public class YmodemSession extends XmodemSession {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    // If true, enable some debugging output.  Note package private access.
    static final boolean DEBUG = false;

    /**
     * Ymodem supports two variants.  These constants can be used to select
     * among them.
     */
    public enum YFlavor {
        /**
         * Vanilla Ymodem: 1024 byte blocks, a 16-bit CRC, 10-second timeout,
         * ACKs.
         */
        VANILLA,

        /**
         * Ymodem/G: 1024 byte blocks, a 16-bit CRC, 10-second timeout, no
         * ACKs.
         */
        Y_G,
    }

    /**
     * The type of Ymodem transfer to perform.
     */
    private YFlavor yFlavor = YFlavor.VANILLA;

    /**
     * If true, permit downloads to overwrite files.
     */
    private boolean overwrite = false;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Construct an instance to represent a file upload.
     *
     * @param yFlavor the Ymodem flavor to use
     * @param input a stream that receives bytes sent by another Ymodem
     * instance
     * @param output a stream to sent bytes to another Ymodem instance
     * @param uploadFiles list of files to upload
     * @throws IllegalArgumentException if uploadFiles contains more than one
     * entry
     */
    public YmodemSession(final YFlavor yFlavor, final InputStream input,
        final OutputStream output, final List<String> uploadFiles) {

        super((yFlavor == YmodemSession.YFlavor.VANILLA ?
                XmodemSession.Flavor.X_1K : XmodemSession.Flavor.X_1K_G),
            input, output, uploadFiles);

        this.yFlavor   = yFlavor;
        this.protocol  = Protocol.YMODEM;
    }

    /**
     * Construct an instance to represent a batch download.
     *
     * @param yFlavor the Ymodem flavor to use
     * @param input a stream that receives bytes sent by another Ymodem
     * instance
     * @param output a stream to sent bytes to another Ymodem instance
     * @param pathname the path to write received files to
     * @param overwrite if true, permit writing to files even if they already
     * exist
     */
    public YmodemSession(final YFlavor yFlavor, final InputStream input,
        final OutputStream output, final String pathname,
        final boolean overwrite) {

        super((yFlavor == YmodemSession.YFlavor.VANILLA ?
                XmodemSession.Flavor.X_1K : XmodemSession.Flavor.X_1K_G),
            input, output, true);

        this.yFlavor            = yFlavor;
        this.protocol           = Protocol.YMODEM;
        this.overwrite          = overwrite;
        this.transferDirectory  = pathname;
    }

    // ------------------------------------------------------------------------
    // XmodemSession ----------------------------------------------------------
    // ------------------------------------------------------------------------

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
     * Set the current status message.  Overridden to provide ymodem package
     * access.
     *
     * @param message the status message
     */
    @Override
    protected synchronized void setCurrentStatus(final String message) {
        super.setCurrentStatus(message);
    }

    /**
     * Set the directory that contains the file(s) of this transfer.
     * Overridden to provide ymodem package access.
     *
     * @param transferDirectory the directory that contains the file(s) of
     * this transfer
     */
    @Override
    protected void setTransferDirectory(final String transferDirectory) {
        super.setTransferDirectory(transferDirectory);
    }

    /**
     * Set the number of bytes transferred in this session.  Overridden to
     * provide ymodem package access.
     *
     * @param bytesTransferred the number of bytes transferred in this
     * session
     */
    @Override
    protected void setBytesTransferred(final long bytesTransferred) {
        super.setBytesTransferred(bytesTransferred);
    }

    /**
     * Set the number of bytes in total to transfer in this session.
     * Overridden to provide ymodem package access.
     *
     * @param bytesTotal the number of bytes in total to transfer in this
     * session
     */
    @Override
    protected void setBytesTotal(final long bytesTotal) {
        super.setBytesTotal(bytesTotal);
    }

    /**
     * Set the number of blocks transferred in this session.  Overridden to
     * provide ymodem package access.
     *
     * @param blocksTransferred the number of blocks transferred in this
     * session
     */
    @Override
    protected void setBlocksTransferred(final long blocksTransferred) {
        super.setBlocksTransferred(blocksTransferred);
    }

    /**
     * Set the time at which last block was sent or received.  Overridden to
     * provide ymodem package access.
     *
     * @param lastBlockMillis the time at which last block was sent or
     * received
     */
    @Override
    protected void setLastBlockMillis(final long lastBlockMillis) {
        super.setLastBlockMillis(lastBlockMillis);
    }

    /**
     * Set the time at which this session started transferring its first
     * file.  Overridden to provide ymodem package access.
     *
     * @param startTime the time at which this session started transferring
     * its first file
     */
    @Override
    protected void setStartTime(final long startTime) {
        super.setStartTime(startTime);
    }

    /**
     * Set the time at which this session completed transferring its last
     * file.  Overridden to provide ymodem package access.
     *
     * @param endTime the time at which this session completed transferring
     * its last file
     */
    @Override
    protected void setEndTime(final long endTime) {
        super.setEndTime(endTime);
    }

    /**
     * Set the state of this transfer.  Overridden to provide ymodem package
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
    @Override
    public String getProtocolName() {
        switch (yFlavor) {
        case VANILLA:
            return "Ymodem";
        case Y_G:
            return "Ymodem/G";
        }

        // Should never get here.
        throw new IllegalArgumentException("Ymodem flavor is not set " +
            "correctly");

    }

    /**
     * Get the block size.  Ymodem requires 1024 block size.
     *
     * @return the block size
     */
    @Override
    public int getBlockSize() {
        return 1024;
    }

    /**
     * Count a timeout, cancelling the transfer if there are too many
     * consecutive errors.  Overridden to provide ymodem package access.
     *
     * @throws IOException if a java.io operation throws
     */
    @Override
    protected synchronized void timeout() throws IOException {
        super.timeout();
    }

    /**
     * Abort the transfer.  Overridden to provide ymodem package access.
     *
     * @param message text message to pass to addErrorMessage()
     * @throws IOException if a java.io operation throws
     */
    @Override
    protected synchronized void abort(final String message) {
        super.abort(message);
    }

    /**
     * Create a FileInfoModifier for the current file being transferred.
     * This is used for YmodemSender and YmodemReceiver to get write access
     * to the FileInfo fields.
     */
    @Override
    protected FileInfoModifier getCurrentFileInfoModifier() {
        return getFileInfoModifier(getCurrentFile());
    }

    /**
     * Add an INFO message to the messages list.  Overridden to permit ymodem
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
     * ymodem package access.
     *
     * @param message the message text
     */
    @Override
    protected synchronized void addErrorMessage(String message) {
        super.addErrorMessage(message);
    }

    /**
     * Set the current file being transferred.  Overridden to permit ymodem
     * package access.
     *
     * @param currentFile the index in the files list
     */
    @Override
    protected synchronized void setCurrentFile(final int currentFile) {
        this.currentFile = currentFile;
    }

    /**
     * Send the appropriate "NAK/ACK" character for this flavor of Xmodem.
     * Overridden to permit ymodem package access.
     *
     * @return true if successful
     */
    @Override
    protected boolean sendNCG() {
        return super.sendNCG();
    }

    // ------------------------------------------------------------------------
    // YmodemSession ----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Get the type of Ymodem transfer to perform.
     *
     * @return the Ymodem flavor
     */
    public YFlavor getYFlavor() {
        return yFlavor;
    }

    /**
     * Get input stream to the remote side.  Used by
     * YmodemReceiver/YmodemSender to cancel a pending read.
     *
     * @return the input stream
     */
    protected EOFInputStream getInput() {
        return input;
    }

    /**
     * Read and parse the special sequence 0 Ymodem packet from the stream.
     *
     * @return true if the transfer is ready to download another file
     */
    protected boolean readBlock0() {
        sequenceNumber = 0;
        byte [] data = null;

        try {
            // Get the packet.  Note that it will already be ACK'd when
            // getPacket() returns.
            data = getPacket();
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            abort("NETWORK I/O ERROR");
            return false;
        }

        // Pull the fields from the block: file name, file size.  The rest is
        // optional.

        int begin = 0;
        String filename = null;
        long fileSize = -1;
        long fileModTime = -1;
        for (int i = begin; i < data.length; i++) {
            if (data[i] == ' ') {
                if ((i - begin) > 1) {
                    try {
                        // We have a value, is it filename or file size?
                        if (filename == null) {
                            // This is part of the filename, ignore it.
                        } else if (fileSize == -1) {
                            // This is the file size.
                            String fileSizeString = new String(data, begin,
                                i - begin, "UTF-8");
                            fileSize = Long.parseLong(fileSizeString);
                            begin = i + 1;
                        } else if (fileModTime == -1) {
                            // This is the file size.
                            String fileModTimeString = new String(data, begin,
                                i - begin, "UTF-8");
                            fileModTime = Long.parseLong(fileModTimeString, 8);

                            // We are done looking for values.
                            break;
                        }
                    } catch (UnsupportedEncodingException e) {
                        abort("BAD STRING DATA IN BLOCK 0");
                        return false;
                    }
                }
            }

            if (data[i] == 0) {
                if ((i - begin) > 1 || (filename != null && filename.length() > 1 && (i - begin) > 0)) {
                    try {
                        // We have a value, is it filename or file size?
                        if (filename == null) {
                            // Filename it is.
                            filename = new String(data, begin, i - begin, "UTF-8");
                        } else if (fileSize == -1) {
                            // File size.
                            String fileSizeString = new String(data, begin,
                                i - begin, "UTF-8");
                            System.err.println("i " + i + " begin " + begin);
                            System.err.println("fileSizeString '" +
                                fileSizeString + "'");

                            fileSize = Long.parseLong(fileSizeString);

                            // We are done looking for values.
                            break;
                        }
                    } catch (UnsupportedEncodingException e) {
                        abort("BAD STRING DATA IN BLOCK 0");
                        return false;
                    }
                }
                // Skip past the NUL and set the beginning of the next
                // string.
                i++;
                begin = i;
            }
        }

        if (DEBUG) {
            System.err.println("Name: '" + filename + "'");
            System.err.println("Size: " + fileSize + " bytes");
            System.err.println("Time: " + fileModTime + " seconds");
        }

        if (filename == null) {
            // This is the normal termination point.
            return false;
        }

        // The Ymodem "tower of Babel" document permits the file size to be
        // optional.  However, no one in their right mind should use Ymodem
        // without the file size being specified -- it would be a truly
        // ancient implementation, old even by the standards of Ymodem
        // (e.g. a CPM type system).  So we will kill any transfer that does
        // not send the file size, or sends an invalid file size.
        if (fileSize < 0) {
            abort("Invalid file size: " + fileSize);
            return false;
        }

        // Make sure we cannot overwrite this file.
        File checkExists = new File(transferDirectory, filename);
        if ((checkExists.exists() == true) && (overwrite == false)) {
            abort(filename + " already exists, will not overwrite");
            return false;
        } else if (checkExists.exists() && overwrite) {
            checkExists.delete();
            try {
                checkExists.createNewFile();
            } catch (IOException e) { }
        }

        // TODO: allow callers to provide a class name for the
        // LocalFileInterface implementation and use reflection to get it.
        LocalFileInterface localFile = new LocalFile(checkExists);
        if (DEBUG) {
            System.err.println("Transfer directory: " + transferDirectory);
            System.err.println("Download to: " + localFile.getLocalName());
        }

        synchronized (this) {
            // Add the file to the files list and make it the current file.
            FileInfo file = new FileInfo(localFile);
            files.add(file);
            currentFile = files.size() - 1;

            // Now perform the stats update.  Since we have the file size we
            // can do it all though.
            if (getState() == SerialFileTransferSession.State.FILE_INFO) {
                // The first file, set the total start time.
                startTime = file.getStartTime();
            }

            // Set state BEFORE getCurrentFileModifier(), otherwise
            // getCurrentFile() might return null.
            setState(SerialFileTransferSession.State.TRANSFER);
            FileInfoModifier setFile = getCurrentFileInfoModifier();

            setFile.setModTime(fileModTime * 1000);
            setFile.setStartTime(System.currentTimeMillis());
            setFile.setBlockSize(getBlockSize());
            setFile.setBytesTotal(fileSize);
            setFile.setBlocksTotal(file.getBytesTotal() / getBlockSize());
            if (file.getBlocksTotal() * getBlockSize() < file.getBytesTotal()) {
                setFile.setBlocksTotal(file.getBlocksTotal() + 1);
            }
            bytesTotal = bytesTotal + file.getBytesTotal();
        }

        // Good to go on another download.
        return true;
    }

    /**
     * Read the requested transfer type and downgrade accordingly.  Note that
     * EOFException will not be caught here.
     */
    protected boolean startYmodemUpload() {
        try {
            super.startUpload();
        } catch (IOException e) {
            abort("NETWORK I/O ERROR");
            return false;
        }

        // Modify the Ymodem flavor based on the Xmodem flavor.
        switch (getFlavor()) {
        case X_1K_G:
            // 1K/G = Ymodem/G
            yFlavor = YmodemSession.YFlavor.Y_G;
            break;
        default:
            // All others: try Ymodem vanilla.  Really these should only be
            // CRC or 1K, but we will try even with Xmodem vanilla.
            yFlavor = YmodemSession.YFlavor.VANILLA;
            break;
        }

        if (cancelFlag == 0) {
            return true;
        }
        return false;
    }

    /**
     * Switch to the next file to upload, send the special sequence 0 Ymodem
     * packet to the remote side, and wait for the ACK.
     *
     * @return true if the transfer is ready to upload another file
     */
    protected boolean sendBlock0() {

        sequenceNumber = 0;

        try {
            byte [] data = null;
            currentFile++;
            if (currentFile == files.size()) {
                if (DEBUG) {
                    System.err.println("No more files");
                }
                // End of transfer.
                data = new byte[128];
            } else {
                synchronized (this) {
                    setState(SerialFileTransferSession.State.FILE_INFO);
                }
                FileInfo file = getCurrentFile();
                String filename = file.getLocalName();
                String filePart = (new File(filename)).getName();
                byte [] name = filePart.getBytes("UTF-8");
                byte [] size = Long.toString(file.getSize()).getBytes("UTF-8");
                byte [] modtime = Long.toOctalString(file.
                    getModTime() / 1000).getBytes("UTF-8");
                if (name.length + size.length + modtime.length > 110) {
                    data = new byte[1024];
                } else {
                    data = new byte[128];
                }
                System.arraycopy(name, 0, data, 0, name.length);
                System.arraycopy(size, 0, data, name.length + 1, size.length);
                data[name.length + size.length + 1] = ' ';
                System.arraycopy(modtime, 0, data,
                    name.length + 1 + size.length + 1, modtime.length);
            }

            if (DEBUG) {
                System.err.println("Block 0 data:");
                for (int i = 0; i < data.length; i++) {
                    System.err.printf("%02x '%c' ", data[i], data[i]);
                    if (i % 8 == 0) {
                        System.err.println();
                    }
                }
                System.err.println();
            }

            boolean rc = sendDataBlock(data);
            if (currentFile == files.size()) {
                // Always return false, we are done whether or not we had a
                // network error.
                return false;
            }
            return rc;
        } catch (UnsupportedEncodingException e) {
            abort("LOCAL JVM ERROR: NO SUPPORT FOR UTF-8");
            cancelFlag = 1;
            return false;
        } catch (EOFException e) {
            abort("UNEXPECTED END OF TRANSMISSION");
            cancelFlag = 1;
            return false;
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            abort("NETWORK I/O ERROR");
            cancelFlag = 1;
            return false;
        }
    }

}
