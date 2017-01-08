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
package jermit.tests.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

/**
 * This class randomly screws up read()s on an InputStream.
 */
public final class NoisyInputStream extends InputStream {

    /**
     * The wrapped stream.
     */
    private InputStream stream;

    /**
     * The desired "noise level": average number of bytes that can be passed
     * before a one-byte error occurs.  A negative value disables all noise.
     * A value of 0 means every byte will be corrupted.  Typical values will
     * be in the range of 1,000 to 10,000,000.
     */
    private int noiseLevel = 10000;
    
    /**
     * Public constructor, at the default noise rate of 10,000.
     *
     * @param stream the wrapped InputStream
     */
    public NoisyInputStream(final InputStream stream) {
        this.stream     = stream;
        this.noiseLevel = 10000;
    }

    /**
     * Public constructor.
     *
     * @param stream the wrapped InputStream
     * @param noise the average number of bytes that can be passed before a
     * one-byte error occurs.  A negative value disables all noise.  A value
     * of 0 means every byte will be corrupted.  Typical values will be in
     * the range of 1,000 to 10,000,000.
     */
    public NoisyInputStream(final InputStream stream, final int noise) {
        this.stream     = stream;
        this.noiseLevel = noise;
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
        // See if this is the byte to be corrupted.
        if ((int) (noiseLevel * Math.random()) == noiseLevel / 2) {
            /*
             * This is it!  We now have three choices:
             * 
             *   1. Perform a read() but return something random,
             *      i.e. corrupt the stream.
             *   
             *   2. Return something random and don't perform a read(), i.e.
             *      insert a junk byte.
             *      
             *   3. Perform two reads() and return either one of them,
             *      i.e. delete a byte.
             *
             * We will pick our poison with two rolls of the dice.
             */
            if (Math.random() > 0.5) {
                // 1. Corrupt the stream.
                int rc = read();
                if (rc == -1) {
                    return rc;
                } else {
                    int junk = (int) (Math.random() * 65536);
                    return junk % 256;
                }
            }
            if (Math.random() > 0.5) {
                // 2. Insert a junk byte.
                int junk = (int) (Math.random() * 65536);
                return junk % 256;
            }
            // 3. Delete a byte.
            int rc = read();
            if (rc == -1) {
                return rc;
            }
            return read();
        }

        // We will not insert noise, just pass the byte on.
        return stream.read();
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
