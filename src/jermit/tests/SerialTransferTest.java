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
package jermit.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Abstract class to test a serial file transfer.
 */
public abstract class SerialTransferTest implements Runnable {

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
     * Extract a resource in the jar file to a file.
     *
     * @param resource the name of the resource
     * @param file the file to save the resource data to
     * @throws IOException if a java.io operation throws
     */
    public void saveResourceToFile(String resource, File file)
        throws IOException {

        InputStream in = getClass().getClassLoader().
                                getResourceAsStream(resource);
        OutputStream out = new FileOutputStream(file);
        for (;;) {
            int ch = in.read();
            if (ch == -1) {
                break;
            }
            out.write(ch);
        }
        in.close();
        out.close();
    }

    /**
     * Compare the data contents of two files, ignoring any amount of
     * trailing ^Z's.
     *
     * @param file1 the first file to compare
     * @param file2 the second file to compare
     * @return true if the files have the same content and length
     * @throws IOException if a java.io operation throws
     */
    public boolean compareFilesAscii(String file1,
        String file2) throws IOException {

        return compareFilesAscii(new File(file1), new File(file2));
    }

    /**
     * Compare the data contents of two files, ignoring any amount of
     * trailing ^Z's.
     *
     * @param file1 the first file to compare
     * @param file2 the second file to compare
     * @return true if the files have the same content and length
     * @throws IOException if a java.io operation throws
     */
    public boolean compareFilesAscii(File file1,
        File file2) throws IOException {

        InputStream in1 = new FileInputStream(file1);
        InputStream in2 = new FileInputStream(file2);
        for (;;) {
            int ch1 = in1.read();
            int ch2 = in2.read();

            // System.out.printf("ch1: 0x%02x ch2: 0x%02x\n", ch1, ch2);

            if ((ch1 == -1) && (ch2 == 0x1A)) {
                // This is OK: in1 is at EOF, in2 has a terminating ^Z
                break;
            }
            if ((ch1 == 0x1A) && (ch2 == -1)) {
                // This is OK: in1 has a terminating ^Z, in2 is at EOF
                break;
            }

            if (ch1 != ch2) {
                /*
                System.out.printf("compareFilesAscii(): false ");
                System.out.printf("--> ch1: 0x%02x ch2: 0x%02x\n", ch1, ch2);
                 */
                in1.close();
                in2.close();
                return false;
            }
            if (ch1 == 0x1A) {
                // Both files have encountered ^Z, stop the comparison.
                break;
            }
        }

        // System.out.println("compareFilesAscii(): true");

        in1.close();
        in2.close();
        return true;
    }

    /**
     * Compare the data contents of two files.
     *
     * @param file1 the first file to compare
     * @param file2 the second file to compare
     * @return true if the files have the same content and length
     * @throws IOException if a java.io operation throws
     */
    public boolean compareFiles(String file1, String file2) throws IOException {
        return compareFiles(new File(file1), new File(file2));
    }

    /**
     * Compare the data contents of two files.
     *
     * @param file1 the first file to compare
     * @param file2 the second file to compare
     * @return true if the files have the same content and length
     * @throws IOException if a java.io operation throws
     */
    public boolean compareFiles(File file1, File file2) throws IOException {

        if (file1.length() != file2.length()) {
            return false;
        }

        InputStream in1 = new FileInputStream(file1);
        InputStream in2 = new FileInputStream(file2);
        for (;;) {
            int ch1 = in1.read();
            if (ch1 == -1) {
                break;
            }
            int ch2 = in2.read();
            if (ch1 != ch2) {
                in1.close();
                in2.close();
                return false;
            }
        }

        in1.close();
        in2.close();
        return true;
    }

    /**
     * Run the test.
     */
    public abstract void doTest() throws IOException, TestFailedException;

}
