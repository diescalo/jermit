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
package jermit.tests.ymodem;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import jermit.protocol.ymodem.YmodemSender;
import jermit.protocol.ymodem.YmodemSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;

/**
 * Test a basic Xmodem file transfer.
 */
public class Ymodem5 extends SerialTransferTest implements Runnable {

    class FilePair {
        public String name;
        public String tmpSourceName;
        public String tmpSourcePath;
        public String tmpDestName;
        public String tmpDestPath;
    }

    /**
     * Public constructor.
     */
    public Ymodem5() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Ymodem5: one binary file upload - Ymodem VANILLA\n");

        // Process:
        //
        //   1. Extract jermit/tests/data/lady-of-shalott.jpg to
        //      a temp file.
        //   2. Spawn 'rb /path/to/temp1' in a temp directory
        //   3. Spin up YmodemSender to send the file.
        //   4. Read all file pairs and compare contents.

        FilePair [] pairs = new FilePair[1];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = new FilePair();
        }
        pairs[0].name = "lady-of-shalott.jpg";

        List<String> files = new LinkedList<String>();

        for (int i = 0; i < pairs.length; i++) {
            File source = File.createTempFile("send-ymodem", "");
            saveResourceToFile("jermit/tests/data/" + pairs[i].name, source);
            source.deleteOnExit();
            pairs[i].tmpSourceName = source.getName();
            pairs[i].tmpSourcePath = source.getPath();
            files.add(source.getPath());
        }

        // Create a directory
        File destinationDirName = File.createTempFile("receive-ymodem", "");
        String destinationPath = destinationDirName.getPath();
        destinationDirName.delete();
        File destinationDir = new File(destinationPath);
        destinationDir.mkdir();
        destinationDir.deleteOnExit();

        for (int i = 0; i < pairs.length; i++) {
            File destination = new File(destinationPath,
                pairs[i].tmpSourceName);
            destination.deleteOnExit();
            pairs[i].tmpDestName = destination.getName();
            pairs[i].tmpDestPath = destination.getPath();
        }

        ProcessBuilder ryb = new ProcessBuilder("rb");
        // Change rb's working dir to be the temp dir
        ryb.directory(destinationDir);
        Process ry = ryb.start();

        YmodemSender sy = new YmodemSender(YmodemSession.YFlavor.Y_G,
            ry.getInputStream(), ry.getOutputStream(), files);
        sy.run();

        // Wait for ry to finish before comparing files!
        for (;;) {
            try {
                if (ry.waitFor() == 0) {
                    break;
                }
            } catch (InterruptedException e) {
                // SQUASH
            }
        }

        for (int i = 0; i < pairs.length; i++) {
            if (!compareFiles(pairs[i].tmpSourcePath, pairs[i].tmpDestPath)) {
                throw new TestFailedException(pairs[i].name +
                    ": Files are not the same");
            }
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
            Ymodem5 test = new Ymodem5();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
