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
 * FileInfoModifier provides access to package private fields in FileInfo.
 * It can only be instantiated by a subclass of SerialFileTransferSession.
 */
public class FileInfoModifier {

    /**
     * The FileInfo being modified.
     */
    private FileInfo fileInfo;

    /**
     * Set the name of the file on the remote system.
     *
     * @param filename the filename
     */
    public void setRemoteFilename(final String filename) {
        fileInfo.remoteFilename = filename;
    }

    /**
     * Set the size of the file.
     *
     * @param size the size in bytes
     */
    public void setSize(final long size) {
        fileInfo.size = size;
    }

    /**
     * Set the modification time of the file.
     *
     * @param modtime the number of milliseconds since the start of the epoch
     */
    public void setModTime(final long modtime) {
        fileInfo.modtime = modtime;
    }

    /**
     * Set the number of bytes successfully transferred for this file.
     *
     * @param bytesTransferred the number of bytes transferred
     */
    public void setBytesTransferred(final long bytesTransferred) {
        fileInfo.bytesTransferred = bytesTransferred;
    }

    /**
     * Set the number of bytes to transfer in total for this file.  (This may
     * not be the same as the file size.)
     *
     * @param bytesTotal the bytes to transfer
     */
    public void setBytesTotal(final long bytesTotal) {
        fileInfo.bytesTotal = bytesTotal;
    }

    /**
     * Set the number of blocks successfully transferred for this file.
     *
     * @param blocksTransferred the blocks transferred
     */
    public void setBlocksTransferred(final long blocksTransferred) {
        fileInfo.blocksTransferred = blocksTransferred;
    }

    /**
     * Set the number of blocks in total for this file.
     *
     * @param blocksTotal the total blocks
     */
    public void setBlocksTotal(final long blocksTotal) {
        fileInfo.blocksTotal = blocksTotal;
    }

    /**
     * Set the size of a transfer block.  This is typically 128 for Xmodem,
     * 1024 for Ymodem and Zmodem, and 96-1024 for Kermit.
     *
     * @param blockSize the block size
     */
    public void setBlockSize(final int blockSize) {
        fileInfo.blockSize = blockSize;
    }

    /**
     * Set the number of errors encountered while transferring this file.
     *
     * @param errors the number of errors
     */
    public void setErrorCount(final int errors) {
        fileInfo.errors = errors;
    }

    /**
     * Set the time in millis at which this file started transferring.
     *
     * @param startTime the start time in millis
     */
    public void setStartTime(final long startTime) {
        fileInfo.startTime = startTime;
    }

    /**
     * Set the time in millis at which this file completed transferring.
     *
     * @param endTime the end time in millis
     */
    public void setEndTime(final long endTime) {
        fileInfo.endTime = endTime;
    }

    /**
     * Set complete flag.
     *
     * @param complete true if this file was transferred successfully
     */
    public void setComplete(final boolean complete) {
        fileInfo.complete = complete;
    }

    /**
     * Set the local path to file.
     *
     * @param localFile the local file
     */
    public void setLocalFile(final LocalFileInterface localFile) {
        fileInfo.localFile = localFile;
    }

    /**
     * Set the full path and file name on the remote system.
     *
     * @param remoteFilename the remote file name
     */
    public void setRemoteName(final String remoteFilename) {
        fileInfo.remoteFilename = remoteFilename;
    }

    /**
     * Package private instance constructor.
     *
     * @param fileInfo the FileInfo that will be modified by one of the
     * protocol implementations
     */
    FileInfoModifier(final FileInfo fileInfo) {
        if (fileInfo == null) {
            throw new IllegalArgumentException("fileInfo cannot be null");
        }
        this.fileInfo = fileInfo;
    }

}
