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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * FileAttributesPacket is used to represent file metadata, which is a large
 * superset of POSIX file attributes.
 */
class FileAttributesPacket extends Packet {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * File creation options.
     */
    public enum NewFileAccessMode {

        /**
         * Always create a new file (never collide).
         */
        NEW,

        /**
         * Always overwrite the existing file.
         */
        SUPERSEDE,

        /**
         * Always append to the existing file.
         */
        APPEND,

        /**
         * Warn and rename the old file (crash recovery).
         */
        WARN,
    }

    /**
     * Unmangled filename.
     */
    public String filename;

    /**
     * Size of file in bytes.
     */
    public long fileSize;

    /**
     * Size of file in k-bytes.
     */
    public long fileSizeK;

    /**
     * Modification time of file.
     */
    public long fileModTime;

    /**
     * Current file position.
     */
    public long filePosition;

    /**
     * File protection - native format.
     */
    public int fileProtection = 0xFFFF;

    /**
     * File protection - kermit format.
     */
    public byte kermitProtection = 0;

    /**
     * File access.
     */
    public NewFileAccessMode fileAccess = NewFileAccessMode.WARN;

    /**
     * If true, treat this as a text file.
     */
    public boolean textMode = false;

    /**
     * If true, support the RESEND option.
     */
    public boolean doResend = false;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param checkType checksum type
     * @param seq sequence number of the packet
     */
    public FileAttributesPacket(final byte checkType, final int seq) {
        super(Type.FILE, (byte) 'A', "File Attributes", checkType, seq);

        // Don't encode it when serializing
        dontEncodeData = true;

        // Default to now time
        fileModTime = System.currentTimeMillis();
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
        int i;

        for (i = 0; i + 1 < data.length; ) {
            byte type = data[i];
            i++;
            byte length = unChar(data[i]);
            i++;

            if (DEBUG) {
                System.err.printf("   ATTRIBUTE TYPE '%c' LENGTH %d i " +
                    "%d data.length %d\n", (char) type, length, i, data.length);
            }

            if (i + length > data.length) {
                if (DEBUG) {
                    System.err.println("   ERROR SHORT ATTRIBUTE PACKET");
                }
                // Sender isn't Kermit compliant, abort.
                throw new KermitProtocolException("Invalid Attributes " +
                    "packet (too short)");
            }

            String buffer = "";
            try {
                buffer = (new String(data, "UTF-8")).substring(i,
                    i + length);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            switch (type) {

            case '!':
                // File size in k-bytes
                fileSizeK = Long.parseLong(buffer);
                if (DEBUG) {
                    System.err.printf("   file size (K) %d\n", fileSizeK);
                }
                break;

            case '\"':
                // File type
                if (DEBUG) {
                    System.err.printf("   file type: \"%s\"\n", buffer);
                }

                // See if ASCII
                if ((length > 0) && (buffer.charAt(0) == 'A')) {
                    if (DEBUG) {
                        System.err.println("   file type: ASCII");
                    }
                    /*
                     * The Kermit Protocol book allows for multiple ways to
                     * encode EOL, but also specifies CRLF as the canonical
                     * standard.  We will always assume ASCII files are CRLF
                     * format.
                     *
                     * Actually, all we do is strip CR's in the input, even
                     * if they aren't paired with LF.
                     */
                    if (System.getProperty("jermit.kermit.download.forceBinary",
                            "true").equals("false")) {
                        textMode = true;
                        if (DEBUG) {
                            System.err.println("  - WILL do CRLF xlate -");
                        }
                    } else {
                        textMode = false;
                        if (DEBUG) {
                            System.err.println("  - will NOT do CRLF xlate -");
                        }
                    }
                }
                break;

            case '#':
                // Creation date
                fileModTime = kermitTime(buffer);
                if (DEBUG) {
                    System.err.printf("   creation date %s %s ('%s')\n",
                        fileModTime, (new java.util.Date(fileModTime)), buffer);
                }
                break;

            case '$':
                // Creator ID - skip
                break;
            case '%':
                // Charge account - skip
                break;
            case '&':
                // Area to store the file - skip
                break;
            case '\'':
                // Area storage password - skip
                break;
            case '(':
                // Block size - skip
                break;

            case ')':
                // Access
                switch (data[i]) {
                case 'N':
                    // Create a new file
                    if (DEBUG) {
                        System.err.println("   CREATE NEW FILE");
                    }
                    fileAccess = NewFileAccessMode.NEW;
                    break;
                case 'S':
                    // Supersede - overwrite if a file already exists
                    if (DEBUG) {
                        System.err.println("   SUPERSEDE");
                    }
                    fileAccess = NewFileAccessMode.SUPERSEDE;
                    break;
                case 'A':
                    // Append to file
                    if (DEBUG) {
                        System.err.println("   APPEND TO FILE");
                    }
                    fileAccess = NewFileAccessMode.APPEND;
                    break;
                case 'W':
                    // Warn - rename if file exists
                    if (DEBUG) {
                        System.err.println("   WARN AND RENAME IF FILE EXISTS");
                    }
                    fileAccess = NewFileAccessMode.WARN;
                    break;
                default:
                    // Others, ignore
                    break;
                }
                break;

            case '*':
                // Encoding
                switch (data[i]) {
                case 'A':
                    // ASCII
                    break;
                case 'H':
                    // Hex "nibble" encoding
                    break;
                case 'E':
                    // EBCDIC
                    break;
                case 'X':
                    // Encrypted
                    break;
                case 'Q':
                    // Huffman encoding
                    break;
                default:
                    // Others, ignore
                    break;
                }
                break;

            case '+':
                // Disposition
                if (DEBUG) {
                    System.err.printf("   disposition: %c\n", (char) data[i]);
                }

                switch (data[i]) {
                case 'R':
                    // RESEND option
                    if (DEBUG) {
                        System.err.println("       RESEND");
                    }
                    doResend = true;
                    break;
                case 'M':
                    // Send as Mail to user
                    break;
                case 'O':
                    // Send as lOng terminal message
                    break;
                case 'S':
                    // Submit as batch job
                    break;
                case 'P':
                    // Print on system printer
                    break;
                case 'T':
                    // Type the file on screen
                    break;
                case 'L':
                    // Load into memory at given address
                    break;
                case 'X':
                    // Load into memory at given address and eXecute
                    break;
                case 'A':
                    // Archive the file
                    break;
                default:
                    // Others, ignore
                    break;
                }
                break;

            case ',':
                // Protection in receiver format
                // It will be in octal
                fileProtection = 0;
                for (int j = 0; i < buffer.length(); j++) {
                    fileProtection *= 8;
                    fileProtection += (buffer.charAt(j) - '0');
                }
                if (DEBUG) {
                    System.err.printf("   protection %d %o\n",
                        fileProtection, fileProtection);
                }
                break;

            case '-':
                // Protection in Kermit format
                kermitProtection = unChar(data[i]);
                if (DEBUG) {
                    System.err.printf("   protection (kermit format) %02x\n",
                        kermitProtection);
                }
                break;

            case '.':
                // Machine and OS of origin - skip
                break;
            case '/':
                // Format of data within file - skip
                break;
            case 'O':
                // System-dependant parameters for storing file - skip
                break;

            case '1':
                // File size in bytes
                fileSize = Long.parseLong(buffer);
                if (DEBUG) {
                    System.err.printf("   file size (bytes) %d\n", fileSize);
                }
                break;

            case '2':
                // Reserved - discard
                break;
            case '3':
                // Reserved - discard
                break;
            case '4':
                // Reserved - discard
                break;
            case '5':
                // Reserved - discard
                break;
            case '6':
                // Reserved - discard
                break;
            case '7':
                // Reserved - discard
                break;
            case '8':
                // Reserved - discard
                break;
            case '9':
                // Reserved - discard
                break;
            case ':':
                // Reserved - discard
                break;
            case ';':
                // Reserved - discard
                break;
            case '<':
                // Reserved - discard
                break;
            case '=':
                // Reserved - discard
                break;
            case '>':
                // Reserved - discard
                break;
            case '?':
                // Reserved - discard
                break;
            case '@':
                // Reserved - discard
                break;
            default:
                // Reserved - discard
                break;
            }
            i += length;
        }

        if ((data.length - i) != 0) {
            if (DEBUG) {
                System.err.println("   ERROR LONG ATTRIBUTE PACKET");
            }
            throw new KermitProtocolException("Invalid Attributes packet " +
                "(too long)");
        }

        // Use kermit_protection if file_protection wasn't specified
        if ((fileProtection == 0xFFFF) && (kermitProtection != 0)) {
            // Start with rw-------
            fileProtection = 0600;
            if ((kermitProtection & 0x01) != 0) {
                if (DEBUG) {
                    System.err.println("     kermitProtection: world read");
                }
                // Add r--r--r--
                fileProtection |= 044;
            }
            if ((kermitProtection & 0x02) != 0) {
                if (DEBUG) {
                    System.err.println("     kermitProtection: world write");
                }
                // Add -w--w--w-
                fileProtection |= 022;
            }
            if ((kermitProtection & 0x01) != 0) {
                if (DEBUG) {
                    System.err.println("     kermitProtection: world execute");
                }
                /* Add --x--x--x */
                fileProtection |= 0111;
            }
            if (DEBUG) {
                System.err.printf("translated from kermitProtection: %d %o\n",
                    fileProtection, fileProtection);
            }
        }
    }

    /**
     * Encode object fields into data bytes.
     */
    @Override
    protected void writeToData() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Text vs binary field
        output.write('\"');
        if (textMode == true) {
            if (DEBUG) {
                System.err.println("writeToData() - ASCII FILE");
            }
            // File type AMJ
            output.write(toChar((byte) 1));
            output.write('A');
        } else {
            if (DEBUG) {
                System.err.println("writeToData() - BINARY FILE");
            }
            // File type B8
            output.write(toChar((byte) 2));
            output.write('B');
            output.write('8');
        }

        // File size in bytes
        byte [] sizeBytes = null;
        try {
            sizeBytes = Long.toString(fileSize).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (DEBUG) {
            System.err.printf("writeToData() - file size %d\n", fileSize);
        }
        output.write('1');
        output.write(toChar((byte) sizeBytes.length));
        try {
            output.write(sizeBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // File modification time
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fileModTime);

        String timeString = String.format("%1$tC%1$ty%1$tm%1$td " +
            "%1$tH%1$tM%1$tS", cal);
        byte [] timeBytes = null;
        try {
            timeBytes = timeString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (DEBUG) {
            System.err.printf("writeToData() - file time %s\n", timeString);
        }
        output.write('#');
        output.write(toChar((byte) timeBytes.length));
        try {
            output.write(timeBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Protection - native, only include the bottom 9 bits
        // TODO: windows support
        String modeString = String.format("%o", (fileProtection & 0x1FF));
        byte [] modeBytes = null;
        try {
            modeBytes = modeString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (DEBUG) {
            System.err.printf("writeToData() - protection %s\n", modeString);
        }
        output.write(',');
        output.write(toChar((byte) modeBytes.length));
        try {
            output.write(modeBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Protection - kermit, only look at bottom 3 bits
        // TODO: windows support
        byte kermitProtection = 0;
        if ((fileProtection & 0x01) != 0) {
            kermitProtection |= 0x04;
        }
        if ((fileProtection & 0x02) != 0) {
            kermitProtection |= 0x02;
        }
        if ((fileProtection & 0x04) != 0) {
            kermitProtection |= 0x01;
        }
        if (DEBUG) {
            System.err.printf("writeToData() - kermitProtection %c %02x\n",
                (char) kermitProtection, kermitProtection);
        }
        output.write('-');
        output.write(toChar((byte) 1));
        output.write(toChar(kermitProtection));

        // Resend capability
        if (doResend == true) {
            if (DEBUG) {
                System.err.println("writeToData() - RESEND\n");
            }
            output.write('+');
            output.write(toChar((byte) 1));
            output.write('R');
        }
        data = output.toByteArray();
    }

    // ------------------------------------------------------------------------
    // FileAttributesPacket ---------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Sets my variables to another FileAttributePacket's values.
     *
     * @param other another FileAttributesPacket instance
     */
    public void setTo(final FileAttributesPacket other) {
        this.filename           = other.filename;
        this.fileSize           = other.fileSize;
        this.fileSizeK          = other.fileSizeK;
        this.fileModTime        = other.fileModTime;
        this.filePosition       = other.filePosition;
        this.fileProtection     = other.fileProtection;
        this.kermitProtection   = other.kermitProtection;
        this.fileAccess         = other.fileAccess;
        this.textMode           = other.textMode;
        this.doResend           = other.doResend;
    }

    /**
     * Parses the Kermit Attributes packet "creation time" field.  The time
     * format looks like [yy]yymmdd[ hh:mm[:ss]].  2-digit years are assumed
     * to be between 1900 and 2000.
     *
     * @param time "ISO standard date format" string
     * @return UTC time in millis
     */
    private long kermitTime(String time) {
        String [] tokens = time.split(" ");
        short year = 0;
        short month = 0;
        short day = 0;
        short hour = 0;
        short minute = 0;
        short second = 0;

        if (tokens.length > 0) {
            // Pull date field
            String token = tokens[0];
            if (token.length() == 6) {
                // YYMMDD
                year += Short.parseShort(token.substring(0, 2));
                month = Short.parseShort(token.substring(2, 4));
                day = Short.parseShort(token.substring(4, 6));
            } else if (token.length() == 8) {
                // YYYYMMDD
                year = Short.parseShort(token.substring(0, 4));
                month = Short.parseShort(token.substring(4, 6));
                day = Short.parseShort(token.substring(6, 8));
            } else {
                // Unknown format, bail out
                return System.currentTimeMillis();
            }
        }

        if (tokens.length > 1) {
            // Pull the time field
            String token = tokens[1];
            if (token.length() == 5) {
                // hh:mm
                hour = Short.parseShort(token.substring(0, 2));
                minute = Short.parseShort(token.substring(3, 5));
            } else if (token.length() == 8) {
                // hh:mm:ss
                hour = Short.parseShort(token.substring(0, 2));
                minute = Short.parseShort(token.substring(3, 5));
                second = Short.parseShort(token.substring(6, 8));
            } else {
                // Unknown format, bail out
                return System.currentTimeMillis();
            }
        }

        // Build the time object
        return (new GregorianCalendar(year, month - 1, day,
                hour, minute, second)).getTimeInMillis();
    }
}
