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
package jermit.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * LocalFile implements LocalFileInterface against a local file.
 */
public class LocalFile implements LocalFileInterface {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The file on the local filesystem.
     */
    private File file;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Construct an instance using a File.
     *
     * @param file the File on the local filesystem
     * @throws IllegalArgumentException if file is not a regular file
     */
    public LocalFile(final File file) {
        if (file.exists() && !file.isFile()) {
            throw new IllegalArgumentException(file + " is not a normal file");
        }
        this.file = file;
    }

    // ------------------------------------------------------------------------
    // LocalFileInterface -----------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Get an InputStream that will read from this thing.
     *
     * @return the InputStream
     * @throws IOException if an I/O error occurs
     */
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    /**
     * Get an OutputStream that can one can use to write to this thing.
     *
     * @return the OutputStream, positioned to append
     * @throws IOException if an I/O error occurs
     */
    public OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(file, true);
    }

    /**
     * Get the local name of this thing.  For example, its name on the local
     * filesystem.
     *
     * @return the local name
     */
    public String getLocalName() {
        return file.getPath();
    }

    /**
     * Get the name of this thing that would be given to a remote system,
     * i.e. the file part of a path name.
     *
     * @return the remote name
     */
    public String getRemoteName() {
        return file.getName();
    }

    /**
     * Get the file/data creation time.
     *
     * @return the number of milliseconds since midnight Jan 1, 1970
     * UTC.
     */
    public long getTime() {
        return file.lastModified();
    }

    /**
     * Set the file/data creation time to a new value.
     *
     * @param millis the number of milliseconds since midnight Jan 1, 1970
     * UTC.
     * @throws IOException if an I/O error occurs
     */
    public void setTime(long millis) throws IOException {
        Path path = file.toPath();
        Files.setLastModifiedTime(path, FileTime.fromMillis(millis));
    }

    /**
     * Get the file/data total length.
     *
     * @return the number of bytes in the data or file contents
     */
    public long getLength() {
        return file.length();
    }

    /**
     * Set the file protection.
     *
     * @param ownerReadable whether or not the file owner can read this data
     * @param ownerWriteable whether or not the file owner can write this data
     * @param ownerExecutable whether or not the file owner can execute this
     * data
     * @param worldReadable whether or not everybody can read this data
     * @param worldWriteable whether or not everybody can write this data
     * @param worldExecutable whether or not everybody can execute this data
     */
    public void setProtection(boolean ownerReadable, boolean ownerWriteable,
        boolean ownerExecutable, boolean worldReadable, boolean worldWriteable,
        boolean worldExecutable) {

        if (ownerExecutable == worldExecutable) {
            file.setExecutable(ownerExecutable, false);
        } else {
            file.setExecutable(ownerExecutable, true);
        }
        if (ownerReadable == worldReadable) {
            file.setReadable(ownerReadable, false);
        } else {
            file.setReadable(ownerReadable, true);
        }
        if (ownerWriteable == worldWriteable) {
            file.setWritable(ownerWriteable, false);
        } else {
            file.setWritable(ownerWriteable, true);
        }
    }

    /**
     * Get the protection as a mask of POSIX-like attributes.
     *
     * @return the POSIX-like protection attributes
     */
    public int getProtection() {
        int mode = 0;
        if (file.canRead()) {
            mode |= 0400;
        }
        if (file.canWrite()) {
            mode |= 0200;
        }
        if (file.canExecute()) {
            mode |= 0100;
        }
        return mode;
    }

    /**
     * Delete this thing.  Note that implementations may choose to ignore
     * this.
     */
    public void delete() {
        file.delete();
    }

    /**
     * Heuristic check to see if this thing is text-only (ASCII).
     *
     * @return true if the file appears to be ASCII text only
     */
    public boolean isText() {
        FileInputStream input = null;
        try {
            boolean text = true;
            input = new FileInputStream(file);
            for (int i = 0; i < 1024; i++) {
                int ch = input.read();
                if (ch == -1) {
                    break;
                }
                if ((ch & 0x80) != 0) {
                    text = false;
                    break;
                }
            }
            return text;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // An error occurred, assume binary.
        return false;
    }

}
