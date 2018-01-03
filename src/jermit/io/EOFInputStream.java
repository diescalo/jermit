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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class overrides read() to throw an EOFException rather than return
 * -1.  This is used to shortcut a lot of error handling code.
 */
public class EOFInputStream extends InputStream {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The wrapped stream.
     */
    private InputStream stream;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param stream the wrapped InputStream
     */
    public EOFInputStream(final InputStream stream) {
        this.stream = stream;
    }
    
    // ------------------------------------------------------------------------
    // InputStream ------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Reads the next byte of data from the input stream.
     *
     * @return the next byte of data.
     * @throws EOFException if there is no more data because the end of the
     * stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read() throws IOException, EOFException {
        int rc = stream.read();
        if (rc == -1) {
            throw new EOFException();
        }
        return rc;
    }

    /**
     * Reads some number of bytes from the input stream and stores them into
     * the buffer array b.
     *
     * @param b the buffer into which the data is read.
     * @return the total number of bytes read into the buffer.
     * @throws EOFException if there is no more data because the end of the
     * stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b) throws IOException, EOFException {
        int rc = stream.read(b);
        if (rc == -1) {
            throw new EOFException();
        }
        return rc;
    }

    /**
     * Reads up to len bytes of data from the input stream into an array of
     * bytes.
     *
     * @param b the buffer into which the data is read.
     * @param off the start offset in array b at which the data is written.
     * @param len the maximum number of bytes to read.
     * @return the total number of bytes read into the buffer.
     * @throws EOFException if there is no more data because the end of the
     * stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b, final int off,
        final int len) throws IOException, EOFException {

        int rc = stream.read(b, off, len);
        if (rc == -1) {
            throw new EOFException();
        }
        return rc;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream.
     *
     * @return an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking or 0 when it
     * reaches the end of the input stream.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int available() throws IOException {
        return stream.available();
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        stream.close();
    }

    /**
     * Marks the current position in this input stream.
     *
     * @param readLimit the maximum limit of bytes that can be read before
     * the mark position becomes invalid
     */
    @Override
    public void mark(final int readLimit) {
        stream.mark(readLimit);
    }

    /**
     * Tests if this input stream supports the mark and reset methods.
     *
     * @return true if this stream instance supports the mark and reset
     * methods; false otherwise
     */
    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    /**
     * Repositions this stream to the position at the time the mark method
     * was last called on this input stream.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void reset() throws IOException {
        stream.reset();
    }

    /**
     * Skips over and discards n bytes of data from this input stream.
     *
     * @param n the number of bytes to be skipped
     * @return the actual number of bytes skipped
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long skip(final long n) throws IOException {
        return stream.skip(n);
    }
    
    // ------------------------------------------------------------------------
    // EOFInputStream ---------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Get the wrapped stream.
     *
     * @return the wrapped stream
     */
    public InputStream getStream() {
        return stream;
    }

}
