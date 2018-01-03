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

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

/**
 * This class bandwidth throttles an InputStream to simulate the performance
 * of a modem-based connection.
 */
public class ThrottledInputStream extends InputStream {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The wrapped stream.
     */
    private InputStream stream;

    /**
     * Total bytes received from the read() calls.
     */
    private long totalBytes = 0;

    /**
     * Time at which this InputStream was created.
     */
    private long startTime = System.currentTimeMillis();

    /**
     * Time at which the most recent byte was received by a read() call.
     */
    private long lastByteTime;

    /**
     * Time at which the last read(byte) call sent a byte out.
     */
    private long lastByteNanos = -1;

    /**
     * The bits per second to throttle to.  Typical values are: 110, 300,
     * 1200, 2400, 9600, 14400, 19200, 28800, 33600, 38400, 57600.
     */
    private int bps = 2400;

    /**
     * The number of bits per byte.  This will typically be 10 or 11: 1 mark
     * bit, 8 data + parity bits, 1 or 2 stop bits.
     */
    private int bitsPerByte = 10;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor, at the default rate of 2400 bps and 10 bits per
     * byte.
     *
     * @param stream the wrapped InputStream
     */
    public ThrottledInputStream(final InputStream stream) {
        this.stream             = stream;
        this.bps                = 2400;
        this.bitsPerByte        = 10;
    }

    /**
     * Public constructor, using the default 10 bits per byte.
     *
     * @param stream the wrapped InputStream
     * @param bps the bits per second to transfer
     */
    public ThrottledInputStream(final InputStream stream, final int bps) {
        this.stream             = stream;
        this.bps                = bps;
        this.bitsPerByte        = 10;
    }

    /**
     * Public constructor.
     *
     * @param stream the wrapped InputStream
     * @param bps the bits per second to transfer
     * @param bitsPerByte the number of bits to assume per byte transferred
     */
    public ThrottledInputStream(final InputStream stream, final int bps,
        final int bitsPerByte) {
        this.stream             = stream;
        this.bps                = bps;
        this.bitsPerByte        = bitsPerByte;
    }

    // ------------------------------------------------------------------------
    // InputStream ------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Reads the next byte of data from the input stream.
     *
     * @return the next byte of data, or -1 if there is no more data because
     * the end of the stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read() throws IOException {
        // See if we need to wait on this byte, based on lastByteNanos.
        if (lastByteNanos != -1) {
            long nanos = System.nanoTime() - lastByteNanos;
            long nanosPerByte = 1000000000L * bitsPerByte / bps;
            if (nanos - nanosPerByte < 0) {
                // We have not waited enough nanos for this byte.
                try {
                    int millis = (int) ((nanosPerByte - nanos) / 1000000);
                    int nanosToSleep = (int) ((nanosPerByte - nanos) % 1000000);
                    Thread.currentThread().sleep(millis, nanosToSleep);
                } catch (InterruptedException e) {
                    // SQUASH
                }
            }
        }
        // We have slept as long as needed, now read the byte.
        int ch = stream.read();
        lastByteNanos = System.nanoTime();
        if (ch != -1) {
            totalBytes++;
            lastByteTime = System.currentTimeMillis();
        }
        return ch;
    }

    /**
     * Reads some number of bytes from the input stream and stores them into
     * the buffer array b.
     *
     * @param b the buffer into which the data is read.
     * @return the total number of bytes read into the buffer, or -1 if there
     * is no more data because the end of the stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b) throws IOException {
        // See if we need to wait on this byte, based on lastByteNanos.
        if (lastByteNanos != -1) {
            long nanos = System.nanoTime() - lastByteNanos;
            long nanosPerByte = 1000000000L * bitsPerByte / bps;
            long nanosNeeded = nanosPerByte * b.length;
            if (nanos - nanosNeeded < 0) {
                // We have not waited enough nanos for this byte.
                try {
                    int millis = (int) ((nanosNeeded - nanos) / 1000000);
                    int nanosToSleep = (int) ((nanosNeeded - nanos) % 1000000);
                    Thread.currentThread().sleep(millis, nanosToSleep);
                } catch (InterruptedException e) {
                    // SQUASH
                }
            }
        }
        // We have slept as long as needed, now read the byte.
        int rc = stream.read(b);
        lastByteNanos = System.nanoTime();
        if (rc != -1) {
            totalBytes += rc;
            lastByteTime = System.currentTimeMillis();
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
     * @return the total number of bytes read into the buffer, or -1 if there
     * is no more data because the end of the stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b, final int off,
        final int len) throws IOException {

        // See if we need to wait on this byte, based on lastByteNanos.
        if (lastByteNanos != -1) {
            long nanos = System.nanoTime() - lastByteNanos;
            long nanosPerByte = 1000000000L * bitsPerByte / bps;
            long nanosNeeded = nanosPerByte * len;
            if (nanos - nanosNeeded < 0) {
                // We have not waited enough nanos for this byte.
                try {
                    int millis = (int) ((nanosNeeded - nanos) / 1000000);
                    int nanosToSleep = (int) ((nanosNeeded - nanos) % 1000000);
                    Thread.currentThread().sleep(millis, nanosToSleep);
                } catch (InterruptedException e) {
                    // SQUASH
                }
            }
        }
        // We have slept as long as needed, now read the byte.
        int rc = stream.read(b, off, len);
        lastByteNanos = System.nanoTime();
        if (rc != -1) {
            totalBytes += rc;
            lastByteTime = System.currentTimeMillis();
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
    // ThrottledInputStream ---------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Return general use of this stream as a reportable string.
     *
     * @return duration, bytes, and bytes/sec values
     */
    public String getStats() {
        StringBuilder stats = new StringBuilder();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startTime);
        long now = System.currentTimeMillis();
        long duration = now - startTime;
        long transferDuration = lastByteTime - startTime;
        long days = (duration / 1000) / 86400;
        long rem = duration - (days * 1000 * 86400);
        long hours = (rem / 1000) / 3600;
        rem = rem - (hours * 1000 * 3600);
        long minutes = (rem / 1000) / 60;
        rem = rem - (minutes * 1000 * 60);
        long seconds = rem / 1000;
        long millis = rem % 1000;
        stats.append(String.format("Connect time:   %1$tF %1$tT.%1$tL\n", cal));
        if (days > 0) {
            stats.append(String.format("Duration:  %d d %02d:%02d:%02d.%03d\n",
                    days, hours, minutes, seconds, millis));
        } else {
            stats.append(String.format("Duration:       %02d:%02d:%02d.%03d\n",
                    hours, minutes, seconds, millis));
        }
        stats.append(String.format("Bytes:          %d\n", totalBytes));
        stats.append(String.format("Bytes/sec:      %.3f",
                (1000.0 * totalBytes) / (double)transferDuration));

        return stats.toString();
    }

}
