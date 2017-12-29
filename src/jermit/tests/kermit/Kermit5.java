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
package jermit.tests.kermit;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import jermit.protocol.kermit.KermitSender;
import jermit.protocol.kermit.KermitSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;

/**
 * Test a Kermit batch upload file transfer.
 */
public class Kermit5 extends SerialTransferTest implements Runnable {

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
    public Kermit5() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Kermit5: 4 binary file uploads - ckermit VANILLA\n");

        // Process:
        //
        //   1. Extract jermit/tests/data/lady-of-shalott.jpg to
        //      a temp file.
        //   2. Extract jermit/tests/data/qm5.zip to
        //      a temp file.
        //   3. Extract jermit/tests/data/William-Adolphe_Bouguereau_(1825-1905)_-_A_Young_Girl_Defending_Herself_Against_Eros_(1880).jpg
        //      to a temp file.
        //   4. Extract jermit/tests/data/rfc856.txt to a temp file.
        //   5. Spawn 'rb /path/to/temp1' in a temp directory
        //   6. Spin up KermitSender to send the files.
        //   7. Read all file pairs and compare contents.

        FilePair [] pairs = new FilePair[4];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = new FilePair();
        }
        pairs[0].name = "lady-of-shalott.jpg";
        pairs[3].name = "qm5.zip";
        pairs[2].name = "William-Adolphe_Bouguereau_(1825-1905)_-_A_Young_Girl_Defending_Herself_Against_Eros_(1880).jpg";
        pairs[1].name = "rfc856.txt";

        List<String> files = new LinkedList<String>();

        for (int i = 0; i < pairs.length; i++) {
            File source = File.createTempFile("send-kermit", "");
            saveResourceToFile("jermit/tests/data/" + pairs[i].name, source);
            source.deleteOnExit();
            pairs[i].tmpSourceName = source.getName();
            pairs[i].tmpSourcePath = source.getPath();
            files.add(source.getPath());
        }

        // Create a directory
        File destinationDirName = File.createTempFile("receive-kermit", "");
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

        ProcessBuilder kermitPB = new ProcessBuilder("script", "-fqe",
            "/dev/null", "-c", "kermit -V -r");
        /*
        ProcessBuilder kermitPB = new ProcessBuilder("script", "-fqe",
            "/dev/null", "-c", "gkermit -r -i");
         */
        // Change kermit's working dir to be the temp dir
        kermitPB.directory(destinationDir);
        Process kermitReceiver = kermitPB.start();

        KermitSender kermitSender = new KermitSender(
                kermitReceiver.getInputStream(),
                kermitReceiver.getOutputStream(), files);

        kermitSender.run();

        // Wait for kermit to finish before comparing files!
        for (;;) {
            try {
                kermitReceiver.waitFor();
                if (!kermitReceiver.isAlive()) {
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
            Kermit5 test = new Kermit5();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
