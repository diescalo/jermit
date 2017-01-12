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

import jermit.protocol.XmodemSender;
import jermit.protocol.XmodemSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;

/**
 * Test a basic Xmodem file transfer.
 */
public final class Xmodem9 extends SerialTransferTest implements Runnable {

    /**
     * Public constructor.
     */
    public Xmodem9() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Xmodem9: ASCII file upload - CRC\n");

        // Process:
        //
        //   1. Extract jermit/tests/data/ALICE26A_NO_EOT.TXT to
        //      a temp file.
        //   2. Spawn 'rx /path/to/temp'
        //   3. Spin up XmodemSender to send /path/to/ALICE26A_NO_EOT.TXT.
        //   4. Read both files and compare contents.

        File source = File.createTempFile("send-xmodem", ".txt");
        saveResourceToFile("jermit/tests/data/lady-of-shalott.jpg", source);
        source.deleteOnExit();

        File destination = File.createTempFile("receive-xmodem", ".txt");
        destination.deleteOnExit();

        ProcessBuilder rxb = new ProcessBuilder("rx", "-c",
            destination.getPath());
        Process rx = rxb.start();

        // Allow overwrite of destination file, because we just created it.
        XmodemSender sx = new XmodemSender(XmodemSession.Flavor.CRC,
            rx.getInputStream(), rx.getOutputStream(),
            source.getPath(), true);

        sx.run();

        // Wait for rx to finish before comparing files!
        for (;;) {
            try {
                if (rx.waitFor() == 0) {
                    break;
                }
            } catch (InterruptedException e) {
                // SQUASH
            }
        }

        if (!compareFilesAscii(source, destination)) {
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
            Xmodem9 test = new Xmodem9();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
