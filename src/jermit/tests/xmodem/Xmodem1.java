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
package jermit.tests.xmodem;

import java.io.File;
import java.io.IOException;

import jermit.protocol.xmodem.XmodemReceiver;
import jermit.protocol.xmodem.XmodemSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;

/**
 * Test a basic Xmodem file transfer.
 */
public class Xmodem1 extends SerialTransferTest implements Runnable {

    /**
     * Public constructor.
     */
    public Xmodem1() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Xmodem1: basic ASCII file download\n");

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

        // Allow overwrite of destination file, because we just created it.
        XmodemReceiver rx = new XmodemReceiver(XmodemSession.Flavor.VANILLA,
            sx.getInputStream(), sx.getOutputStream(),
            destination.getPath(), true);

        rx.run();
        if (!compareFiles(source, destination)) {
            throw new TestFailedException("Files are not the same");
        }

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
            Xmodem1 test = new Xmodem1();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
