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

import jermit.io.ThrottledInputStream;
import jermit.io.ThrottledOutputStream;
import jermit.protocol.XmodemReceiver;
import jermit.protocol.XmodemSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;

/**
 * Test a basic Xmodem file transfer.
 */
public final class Xmodem7 extends SerialTransferTest implements Runnable {

    /**
     * Public constructor.
     */
    public Xmodem7() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Xmodem7: basic ASCII file transfer\n");

        // Process:
        //
        //   1. Extract jermit/tests/data/ALICE26A_NO_EOT.TXT to
        //      a temp file.
        //   2. Spawn 'sx /path/to/ALICE26A_NO_EOT.TXT'
        //   3. Spin up XmodemReceiver to download to a temp file.
        //   4. Read both files and compare contents.

        File source = File.createTempFile("send-xmodem", ".txt");
        saveResourceToFile("jermit/tests/data/ALICE26A_NO_EOT.TXT", source);
        source.deleteOnExit();

        File destination = File.createTempFile("receive-xmodem", ".txt");
        destination.deleteOnExit();

        ProcessBuilder sxb = new ProcessBuilder("sx", source.getPath());
        Process sx = sxb.start();

        // Throttle the connection.
        ThrottledInputStream is = new ThrottledInputStream(sx.getInputStream(),
            19200);
        ThrottledOutputStream os = new ThrottledOutputStream(sx.getOutputStream(),
            19200);


        // Allow overwrite of destination file, because we just created it.
        XmodemReceiver rx = new XmodemReceiver(XmodemSession.Flavor.VANILLA,
            is, os, destination.getPath(), true);

        rx.run();
        if (!compareFiles(source, destination)) {
            throw new TestFailedException("Files are not the same");
        }

        System.out.println("Input: " + is.getStats());
        System.out.println("Output: " + os.getStats());

        System.out.println("Input bytes/sec should read between " +
            "1745 and 1920, due to 19200 bps.  Does it?");

    }

    /**
     * Run the test.  Any exceptions thrown will be emitted to System.err.
     */
    public void run() {
        try {
            doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Main entry point.
     *
     * @param args Command line arguments
     */
    public static void main(final String [] args) {
        try {
            Xmodem7 test = new Xmodem7();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
