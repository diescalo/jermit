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

import jermit.io.LocalFileInterface;

/**
 * FileInfo stores one file's metadata for a serial file transfer.
 */
public class FileInfo {

    /**
     * The path to the file if stored on the local filesystem.
     */
    protected LocalFileInterface localFile;

    /**
     * The full path and file name on the remote system.
     */
    protected String remoteFilename;

    /**
     * The size of the file.
     */
    protected long size;

    /**
     * The modification time of the file in milliseconds since the epoch.
     * Xmodem does not support this.
     */
    protected long modtime;

    /**
     * The number of bytes successfully transferred for this file.
     */
    protected long bytesTransferred;

    /**
     * The number of bytes to transfer in total for this file.  (This may not
     * be the same as the file size.)
     */
    protected long bytesTotal;

    /**
     * The number of blocks successfully transferred for this file.
     */
    protected long blocksTransferred;

    /**
     * The number of blocks in total for this file.
     */
    protected long blocksTotal;

    /**
     * The size of a transfer block.  This is typically 128 for Xmodem, 1024
     * for Ymodem and Zmodem, and 96-1024 for Kermit.
     */
    protected int blockSize;

    /**
     * The number of errors encountered while transferring this file.
     */
    protected int errors;

    /**
     * The time at which this file started transferring.
     */
    protected long startTime;

    /**
     * The time at which this file completed transferring.
     */
    protected long endTime;

    /**
     * If true, this file was transferred successfully.
     */
    protected boolean complete = false;

    /**
     * Get the size of the file.
     *
     * @return the size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Get the modification time of the file.
     *
     * @return the number of milliseconds since the start of the epoch
     */
    public long getModTime() {
        return modtime;
    }

    /**
     * Get the number of bytes successfully transferred for this file.
     *
     * @return the number of bytes transferred
     */
    public long getBytesTransferred() {
        return bytesTransferred;
    }

    /**
     * Get the number of bytes to transfer in total for this file.  (This may
     * not be the same as the file size.)
     *
     * @return the bytes to transfer
     */
    public long getBytesTotal() {
        return bytesTotal;
    }

    /**
     * Get the number of blocks successfully transferred for this file.
     *
     * @return the blocks transferred
     */
    public long getBlocksTransferred() {
        return blocksTransferred;
    }

    /**
     * Get the number of blocks in total for this file.
     *
     * @return the total blocks
     */
    public long getBlocksTotal() {
        return blocksTotal;
    }

    /**
     * Get the size of a transfer block.  This is typically 128 for Xmodem,
     * 1024 for Ymodem and Zmodem, and 96-1024 for Kermit.
     *
     * @return the block size
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Get the number of errors encountered while transferring this file.
     *
     * @return the number of errors
     */
    public int getErrorCount() {
        return errors;
    }

    /**
     * Get the time in millis at which this file started transferring.
     *
     * @return the start time in millis
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Get the time in millis at which this file completed transferring.
     *
     * @return the end time in millis
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Get complete flag.
     *
     * @return true if this file was transferred successfully
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Get the local path to file.
     *
     * @return the local file
     */
    public LocalFileInterface getLocalFile() {
        return localFile;
    }

    /**
     * Get the local system file name.
     *
     * @return the local file name
     */
    public String getLocalName() {
        return localFile.getLocalName();
    }

    /**
     * Get the full path and file name on the remote system.
     *
     * @return the remote file name
     */
    public String getRemoteName() {
        return remoteFilename;
    }

    /**
     * Get the estimated percent completion (0.0 - 100.0) for this file.
     * Note that Xmodem downloads will always report 0.0.
     *
     * @return the percentage of completion.  This will be 0.0 if the
     * transfer has not yet started.
     */
    public double getPercentComplete() {
        if (bytesTotal == 0) {
            return 0.0;
        }
        if ((bytesTransferred >= bytesTotal) || (complete == true)) {
            return 100.0;
        }
        return ((double) bytesTransferred / (double) bytesTotal) * 100.0;
    }

    /**
     * Construct an instance based on a local file.
     *
     * @param file path to file on the local filesystem
     */
    public FileInfo(final LocalFileInterface file) {
        localFile       = file;
        size            = file.getLength();
        modtime         = file.getTime();
        remoteFilename  = file.getRemoteName();
    }

}
