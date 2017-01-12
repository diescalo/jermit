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
package jermit.tests.xmodem;

import java.io.File;
import java.io.IOException;

import jermit.protocol.XmodemReceiver;
import jermit.protocol.XmodemSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;
import jermit.tests.io.NoisyInputStream;
import jermit.tests.io.NoisyOutputStream;

/**
 * Test a basic binary file Xmodem file transfer with 16-bit CRC.
 */
public final class Xmodem11 extends SerialTransferTest {

    /**
     * Public constructor.
     */
    public Xmodem11() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Xmodem11: binary file download NOISY - CRC\n");

        // Process:
        //
        //   1. Extract jermit/tests/data/lady-of-shalott.jpg to
        //      a temp file.
        //   2. Spawn 'sx /path/to/lady-of-shalott.jpg'
        //   3. Spin up XmodemReceiver to download to a temp file.
        //   4. Read both files and compare contents.

        File source = File.createTempFile("send-xmodem", ".jpg");
        saveResourceToFile("jermit/tests/data/lady-of-shalott.jpg", source);
        source.deleteOnExit();

        File destination = File.createTempFile("receive-xmodem", ".jpg");
        destination.deleteOnExit();

        ProcessBuilder sxb = new ProcessBuilder("sx", source.getPath());
        Process sx = sxb.start();

        // Allow overwrite of destination file, because we just created it.
        XmodemReceiver rx = new XmodemReceiver(XmodemSession.Flavor.CRC,
            new NoisyInputStream(sx.getInputStream()),
            new NoisyOutputStream(sx.getOutputStream()),
            destination.getPath(), true);

        rx.run();
        if (!compareFiles(source, destination)) {
            throw new TestFailedException("Files are not the same");
        }

    }

    /**
     * Main entry point.
     *
     * @param args Command line arguments
     */
    public static void main(final String [] args) {
        try {
            Xmodem11 test = new Xmodem11();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
