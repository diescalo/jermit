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
package jermit.protocol.kermit;

import java.io.UnsupportedEncodingException;

/**
 * FilePacket is used to represent a new file header.
 */
class FilePacket extends Packet {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Name of file.
     */
    public String filename = "";

    /**
     * If true, use the "robust filename" when transmitting on the wire.
     */
    public boolean robustFilename = false;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param checkType checksum type
     * @param seq sequence number of the packet
     */
    public FilePacket(final byte checkType, final byte seq) {
        super(Type.FILE, (byte) 'F', "File Header", checkType, seq);
    }

    // ------------------------------------------------------------------------
    // Packet -----------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Decode data bytes into object fields.
     *
     * @throws KermitProtocolException if the other side violates the Kermit
     * protocol specification
     */
    @Override
    protected void readFromData() throws KermitProtocolException {
        try {
            filename = new String(data, "UTF-8");

            /*
             * Apply gkermit heuristics:
             *
             *    1) All uppercase -> all lowercase
             *    2) Any lowercase -> no change
             */
            boolean doLowercase = true;
            for (int i = 0; i < filename.length(); i++) {
                char ch = filename.charAt(i);
                if (Character.isLowerCase(ch)) {
                    doLowercase = false;
                }
            }
            if (doLowercase == true) {
                filename = filename.toLowerCase();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Encode object fields into data bytes.
     */
    @Override
    protected void writeToData() {
        try {
            if (robustFilename) {
                /*
                 * Kermit "common form":
                 *
                 *   1) Only numbers and letters
                 *   2) Only one '.' in the filename
                 *   3) '.' cannot be the first or last character
                 *   4) All uppercase
                 */
                String newFilename = "";
                int lastPeriod = -1;
                for (int i = 0; i < filename.length(); i++) {
                    char ch = filename.charAt(i);
                    // Convert to "common form"
                    if (ch == '.') {
                        lastPeriod = i;
                        newFilename += '_';
                    } else if ((!Character.isAlphabetic(ch))
                        && (!Character.isDigit(ch))
                    ) {
                        newFilename += '_';
                    } else {
                        newFilename += Character.toUpperCase(ch);
                    }
                }
                if (lastPeriod != -1) {
                    newFilename = newFilename.substring(0, lastPeriod) + '_' +
                        newFilename.substring(lastPeriod + 1);
                }
                while ((newFilename.length() > 0)
                    && (newFilename.charAt(0) == '.')
                ) {
                    newFilename = newFilename.substring(1);
                }
                while ((newFilename.length() > 0)
                    && (newFilename.charAt(newFilename.length() - 1) == '.')
                ) {
                    newFilename = newFilename.substring(0,
                        newFilename.length() - 1);
                }
                filename = newFilename;
                data = newFilename.getBytes("UTF-8");
            } else {
                data = filename.getBytes("UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

}
