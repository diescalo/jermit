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
package jermit.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * This class provides an optional millisecond timeout on its read()
 * operations.  This permits callers to bail out rather than block.
 */
public final class TimeoutInputStream extends InputStream {

    /**
     * The wrapped stream.
     */
    private InputStream stream;

    /**
     * The timeout value in millis.  If it takes longer than this for bytes
     * to be available for read then a ReadTimeoutException is thrown.  A
     * value of 0 means to block as a normal InputStream would.
     */
    private int timeoutMillis;

    /**
     * Public constructor, at the default timeout of 10000 millis (10
     * seconds).
     *
     * @param stream the wrapped InputStream
     */
    public TimeoutInputStream(final InputStream stream) {
        this.stream             = stream;
        this.timeoutMillis      = 10000;
    }

    /**
     * Public constructor, using the default 10 bits per byte.
     *
     * @param stream the wrapped InputStream
     * @param timeoutMillis the timeout value in millis.  If it takes longer
     * than this for bytes to be available for read then a
     * ReadTimeoutException is thrown.  A value of 0 means to block as a
     * normal InputStream would.
     */
    public TimeoutInputStream(final InputStream stream,
        final int timeoutMillis) {

        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Invalid timeoutMillis value, " +
                "must be >= 0");
        }

        this.stream             = stream;
        this.timeoutMillis      = timeoutMillis;
    }

    /**
     * Reads the next byte of data from the input stream.
     *
     * @return the next byte of data, or -1 if there is no more data because
     * the end of the stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read() throws IOException {

        if (timeoutMillis == 0) {
            // Block on the read().
            return stream.read();
        }

        if (stream.available() > 0) {
            // A byte is available now, return it.
            return stream.read();
        }

        // We will wait up to timeoutMillis to see if a byte is available.
        // If not, we throw ReadTimeoutException.
        long checkTime = System.currentTimeMillis();
        while (stream.available() == 0) {
            long now = System.currentTimeMillis();
            if (now - checkTime > timeoutMillis) {
                throw new ReadTimeoutException("Timeout on read(): " +
                    (int) (now - checkTime) + " millis and still no data");
            }
            try {
                // How long do we sleep for, eh?  For now we will go with 10
                // millis.
                Thread.currentThread().sleep(10);
            } catch (InterruptedException e) {
                // SQUASH
            }
        }

        if (stream.available() > 0) {
            // A byte is available now, return it.
            return stream.read();
        }

        throw new IOException("InputStream claimed a byte was available, but " +
            "now it is not.  What is going on?");
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

}
