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
     * The path to the file if stored on the local filesystem.  Note package
     * private access.
     */
    LocalFileInterface localFile;

    /**
     * The name of the file on the remote system.  Note package private
     * access.
     */
    String remoteFilename;

    /**
     * The size of the file.  Note package private access.
     */
    long size;

    /**
     * The modification time of the file.  Xmodem does not support this.
     * Note package private access.
     */
    long modtime;

    /**
     * The number of bytes successfully transferred for this file.  Note
     * package private access.
     */
    long bytesTransferred;

    /**
     * The number of bytes to transfer in total for this file.  (This may not
     * be the same as the file size.)  Note package private access.
     */
    long bytesTotal;

    /**
     * The number of blocks successfully transferred for this file.  Note
     * package private access.
     */
    long blocksTransferred;

    /**
     * The number of blocks in total for this file.  Note package private
     * access.
     */
    long blocksTotal;

    /**
     * The size of a transfer block.  This is typically 128 for Xmodem, 1024
     * for Ymodem and Zmodem, and 96-1024 for Kermit.  Note package private
     * access.
     */
    int blockSize;

    /**
     * The number of errors encountered while transferring this file.  Note
     * package private access.
     */
    int errors;

    /**
     * The time at which this file started transferring.  Note package
     * private access.
     */
    long startTime;

    /**
     * The time at which this file completed transferring.  Note package
     * private access.
     */
    long endTime;

    /**
     * If true, this file was transferred successfully.  Note package private
     * access.
     */
    boolean complete = false;

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
     * Get the remote system file name.
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
