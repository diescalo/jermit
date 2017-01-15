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
import java.io.OutputStream;
import java.util.Calendar;

/**
 * This class bandwidth throttles an OutputStream to simulate the performance
 * of a modem-based connection.
 */
public class ThrottledOutputStream extends OutputStream {

    /**
     * The wrapped stream.
     */
    private OutputStream stream;

    /**
     * Total bytes received from the read() calls.
     */
    private long totalBytes = 0;

    /**
     * Time at which this OutputStream was created.
     */
    private long startTime = System.currentTimeMillis();

    /**
     * Time at which the most recent byte was pushed to a write() call.
     */
    private long lastByteTime;

    /**
     * Time at which the last write(byte) call sent a byte out.
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

    /**
     * Public constructor, at the default rate of 2400 bps and 10 bits per
     * byte.
     *
     * @param stream the wrapped OutputStream
     */
    public ThrottledOutputStream(final OutputStream stream) {
        this.stream             = stream;
        this.bps                = 2400;
        this.bitsPerByte        = 10;
    }

    /**
     * Public constructor, using the default 10 bits per byte.
     *
     * @param stream the wrapped OutputStream
     * @param bps the bits per second to transfer
     */
    public ThrottledOutputStream(final OutputStream stream, final int bps) {
        this.stream             = stream;
        this.bps                = bps;
        this.bitsPerByte        = 10;
    }

    /**
     * Public constructor.
     *
     * @param stream the wrapped OutputStream
     * @param bps the bits per second to transfer
     */
    public ThrottledOutputStream(final OutputStream stream, final int bps,
        final int bitsPerByte) {
        this.stream             = stream;
        this.bps                = bps;
        this.bitsPerByte        = bitsPerByte;
    }

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

    /**
     * Writes the specified byte to this output stream.
     *
     * @param b the byte to write.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(final int b) throws IOException {
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
        // We have slept as long as needed, now write the byte.
        stream.write(b);
        lastByteNanos = System.nanoTime();
        totalBytes++;
        lastByteTime = System.currentTimeMillis();
    }

    /**
     * Writes b.length bytes from the specified byte array to this output
     * stream.
     *
     * @param b the data.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(final byte[] b) throws IOException {
        for (int i = 0; i < b.length; i++) {
            this.write(b[i]);
        }
    }

    /**
     * Writes len bytes from the specified byte array starting at offset off
     * to this output stream.
     *
     * @param b the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(final byte[] b, final int off,
        final int len) throws IOException {

        for (int i = 0; i < len; i++) {
            this.write(b[off + i]);
        }
    }

    /**
     * Closes this output stream and releases any system resources associated
     * with this stream.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        stream.close();
    }

    /**
     * Flushes this output stream and forces any buffered output bytes to be
     * written out.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void flush() throws IOException {
        stream.flush();
    }

}
