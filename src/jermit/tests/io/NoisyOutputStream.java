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
import java.io.OutputStream;
import java.util.Calendar;

/**
 * This class randomly screws up write()s on an OutputStream.
 */
public class NoisyOutputStream extends OutputStream {

    /**
     * The wrapped stream.
     */
    private OutputStream stream;

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
    public NoisyOutputStream(final OutputStream stream) {
        this.stream     = stream;
        this.noiseLevel = 10000;
    }

    /**
     * Public constructor.
     *
     * @param stream the wrapped OutputStream
     * @param noise the average number of bytes that can be passed before a
     * one-byte error occurs.  A negative value disables all noise.  A value
     * of 0 means every byte will be corrupted.  Typical values will be in
     * the range of 1,000 to 10,000,000.
     */
    public NoisyOutputStream(final OutputStream stream, final int noise) {
        this.stream     = stream;
        this.noiseLevel = noise;
    }

    /**
     * Writes the specified byte to this output stream.
     *
     * @param b the byte to write.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(final int b) throws IOException {
        // See if this is the byte to be corrupted.
        if ((int) (noiseLevel * Math.random()) == noiseLevel / 2) {
            /*
             * This is it!  We now have three choices:
             * 
             *   1. Perform a write() but with something random, i.e. corrupt
             *      the stream.
             *   
             *   2. write() this byte and another random byte, i.e.  insert a
             *      junk byte.
             *      
             *   3. Don't perform this write(), i.e. delete a byte.
             *
             * We will pick our poison with two rolls of the dice.
             */
            if (Math.random() > 0.5) {
                // 1. Corrupt the stream.
                int junk = (int) (Math.random() * 65536);
                stream.write((byte) (junk % 256));
                return;
            }
            if (Math.random() > 0.5) {
                // 2. Insert a junk byte.
                int junk = (int) (Math.random() * 65536);
                stream.write((byte) (junk % 256));
                stream.write(b);
                return;
            }
            // 3. Delete a byte.
            return;
        }

        // We will not insert noise, just pass the byte on.
        stream.write(b);
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
