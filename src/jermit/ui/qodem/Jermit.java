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
package jermit.ui.qodem;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import jermit.io.ThrottledInputStream;
import jermit.io.ThrottledOutputStream;
import jermit.protocol.FileInfo;
import jermit.protocol.Protocol;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.kermit.KermitReceiver;
import jermit.protocol.kermit.KermitSender;
import jermit.protocol.xmodem.XmodemReceiver;
import jermit.protocol.xmodem.XmodemSender;
import jermit.protocol.xmodem.XmodemSession;
import jermit.protocol.ymodem.YmodemReceiver;
import jermit.protocol.ymodem.YmodemSender;
import jermit.protocol.ymodem.YmodemSession;
import jermit.ui.posix.Stty;

/**
 * This class provides a main driver that produces a progress bar screen
 * similar to Qodem.  The file transfer is processed on System.in. and
 * System.out, while the progress bar screen is a separate Swing window.
 */
public class Jermit {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The protocol to select.  Most people want Zmodem, so default to that.
     * Even though Kermit is a lot better.
     */
    private Protocol protocol = Protocol.ZMODEM;

    /**
     * Default block size for transfer.  For Xmodem, this is 128 bytes.
     */
    private int blockSize = 128;

    /**
     * If true, this is a download.  If false, this is an upload.  Default is
     * upload.
     */
    private boolean download = false;

    /**
     * The path to download files to.
     */
    private String downloadPath = System.getProperty("user.dir");

    /**
     * If true, use a 16-bit CRC.  For Xmodem, this means support Xmodem-CRC
     * and Xmodem-1K, which are upgrades from vanilla.
     */
    private boolean crc16 = false;

    /**
     * If true, allow downloads to overwrite files.
     */
    private boolean overwrite = false;

    /**
     * The list of filenames read from the command line.
     */
    private LinkedList<String> fileArgs;

    /**
     * For debugging purposes, permit a bandwidth limiter.
     */
    private int bps = -1;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Construct a new instance.  Made private so that the only way to get
     * here is via the static main() method.
     *
     * @param args the command line args.
     */
    private Jermit(final String [] args) {
        fileArgs = new LinkedList<String>();

        // Iterate the list of command line arguments, extracting filenames
        // and handling the options.
        boolean noMoreArgs = false;
        for (int i = 0; i < args.length; i++) {
            if (noMoreArgs == false) {
                if (args[i].equals("--")) {
                    // "--" means treat everything afterwards like a
                    // filename.
                    noMoreArgs = true;
                    continue;
                }
                if (args[i].startsWith("-")) {
                    // This is an argument, process it.
                    if (processArg(args[i]) == false) {
                        if (i < args.length - 1) {
                            processTwoArgs(args[i], args[i + 1]);
                            i++;
                        } else {
                            System.err.println("Warning: command line option " +
                                args[i] + " expects an argument");
                        }
                    }
                } else {
                    // This is a filename, it does not start with "-".
                    fileArgs.add(args[i]);
                }
            } else {
                // "--" was seen, so this is a filename.
                fileArgs.add(args[i]);
            }
        } // for (int i = 0; i < args.length; i++)

        if ((fileArgs.size() == 0) && (protocol == Protocol.XMODEM)) {
            System.err.println("jermit: xmodem needs filename to receive to");
            System.err.println("Try jermit --help for more information.");
            System.exit(2);
        }

        if ((fileArgs.size() == 0) && (download == false)) {
            System.err.println("jermit: send needs at least one file to transfer");
            System.err.println("Try jermit --help for more information.");
            System.exit(2);
        }
    }

    // ------------------------------------------------------------------------
    // Jermit -----------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Execute the file transfer, whatever that is.
     */
    private void run() {
        SerialFileTransferSession session = null;
        Thread transferThread = null;
        Thread uiThread = null;
        XmodemReceiver rx = null;
        XmodemSender sx = null;
        YmodemReceiver rb = null;
        YmodemSender sb = null;
        KermitReceiver rk = null;
        KermitSender sk = null;

        InputStream in = System.in;
        OutputStream out = System.out;

        if (bps > 0) {
            in = new ThrottledInputStream(in, bps);
            out = new ThrottledOutputStream(out, bps);
        }

        switch (protocol) {

        case XMODEM:
            if (download == true) {
                // Download: default to vanilla unless the command line
                // arguments ask for more.
                XmodemSession.Flavor flavor = XmodemSession.Flavor.VANILLA;
                if (blockSize == 1024) {
                    flavor = XmodemSession.Flavor.X_1K;
                } else if (crc16 == true) {
                    flavor = XmodemSession.Flavor.CRC;
                }

                rx = new XmodemReceiver(flavor, in, out, fileArgs.get(0),
                    overwrite);
                session = rx.getSession();
                transferThread = new Thread(rx);

            } else {
                // Upload: default to CRC.  This will automatically fallback
                // to vanilla if the receiver initiates with NAK instead of
                // 'C'.  We can't default to 1K because we can't guarantee
                // that the receiver will know how to handle it.
                XmodemSession.Flavor flavor = XmodemSession.Flavor.CRC;
                if (blockSize == 1024) {
                    // Permit 1K.  This will fallback to vanilla if they use
                    // NAK.
                    flavor = XmodemSession.Flavor.X_1K;
                }

                sx = new XmodemSender(flavor, in, out, fileArgs.get(0));
                session = sx.getSession();
                transferThread = new Thread(sx);
            }
            break;

        case YMODEM:
            if (download == true) {
                // Use vanilla only.  rb does not have a way to specify -G.
                YmodemSession.YFlavor yFlavor = YmodemSession.YFlavor.VANILLA;

                rb = new YmodemReceiver(yFlavor, in, out, downloadPath,
                    overwrite);
                session = rb.getSession();
                transferThread = new Thread(rb);

            } else {
                // Allow -G.  This will automatically fallback to vanilla if
                // the receiver initiates with 'C' instead of 'G'.
                YmodemSession.YFlavor yFlavor = YmodemSession.YFlavor.Y_G;

                sb = new YmodemSender(yFlavor, in, out, fileArgs);
                session = sb.getSession();
                transferThread = new Thread(sb);
            }
            break;

        case ZMODEM:
            // TODO
            System.err.println("Zmodem not yet supported.");
            System.exit(10);
            break;

        case KERMIT:
            if (download == true) {
                rk = new KermitReceiver(in, out, downloadPath);
                session = rk.getSession();
                transferThread = new Thread(rk);
            } else {
                sk = new KermitSender(in, out, fileArgs);
                session = sk.getSession();
                transferThread = new Thread(sk);
            }
            break;
        }

        // We need System.in/out to behave like a dumb file.

        // DEBUG: comment out the following line to be able to exit with ^C.
        Stty.setRaw();

        // Now spin up the UI thread and transfer thread and wait for them
        // both to end.
        QodemUI ui = new QodemUI(session);
        ui.xmodemReceiver = rx;
        ui.xmodemSender = sx;
        ui.ymodemReceiver = rb;
        ui.ymodemSender = sb;
        ui.kermitReceiver = rk;
        ui.kermitSender = sk;
        uiThread = new Thread(ui);
        uiThread.start();
        transferThread.start();
        for (;;) {
            try {
                transferThread.join(25);
                uiThread.join(25);
            } catch (InterruptedException e) {
                // SQUASH
            }
            if (!transferThread.isAlive() && !uiThread.isAlive()) {
                // Both threads have completed (or died), bail out.
                break;
            }
        }

        // Blindly restore System.in/out.
        Stty.setCooked();

        /*
         * All done, now choose an exit value:
         *
         *   0: ALL of the files transferred successfully.
         *   1: SOME of the files transferred successfully.
         *   5: NO files transferred successfully.
         *
         * Note that a skipped file counts as a successful transfer.
         */
        boolean allComplete = true;
        int rc = 5;
        for (FileInfo file: session.getFiles()) {
            if (file.isComplete()) {
                rc = 1;
            } else {
                allComplete = false;
            }
        }
        if (allComplete == true) {
            System.exit(0);
        }
        System.exit(rc);
    }

    /**
     * Show the usage screen.
     */
    private void showUsage() {
        System.out.println("jermit version " + jermit.Version.VERSION);
        System.out.println("Usage: jermit [options] file ...");
        System.out.println("Options:");
        System.out.println("      --1k                   Use 1K blocks (Xmodem)");
        System.out.println("      --bps N                Throttle to N bits per second");
        System.out.println("      --crc16                Use 16-bit CRC (Xmodem)");
        System.out.println("  -h, --help                 Display help text");
        System.out.println("  -k, --kermit               Use Kermit protocol");
        System.out.println("  -o, --overwrite            Permit download to overwrite file");
        System.out.println("  -r, --receive              Receive file");
        System.out.println("  -s, --send                 (DEFAULT) Send file");
        System.out.println("      --version              Display version info");
        System.out.println("  -x, --xmodem               Use Xmodem protocol");
        System.out.println("  -y, --ymodem               Use Ymodem protocol");
        System.out.println("  -z, --zmodem               (DEFAULT) Use Zmodem protocol");
    }

    /**
     * Process a two-argument option.
     *
     * @param arg the argument to process
     * @param value the argument's option
     */
    private void processTwoArgs(final String arg, final String value) {
        if (arg.equals("--bps")) {
            bps = Integer.parseInt(value);
        }
    }

    /**
     * Process a one-argument option.
     *
     * @param arg the argument to process
     * @return true if this argument has been processed, false if it should
     * be passed to processTwoArgs().
     */
    private boolean processArg(final String arg) {
        // Two-argument options that will be honored
        if (arg.equals("--bps")) {
            return false;
        }

        if (arg.equals("--version")) {
            System.out.println("jermit " + jermit.Version.VERSION);
            System.out.println();
            System.out.println(jermit.Version.COPYRIGHT);
            System.out.println("Written by " + jermit.Version.AUTHOR);
            System.exit(0);
        } else if (arg.equals("-h") || arg.equals("--help")) {
            // Display help and exit.
            showUsage();
            System.exit(0);
        } else if (arg.equals("--crc16")) {
            crc16 = true;
        } else if (arg.equals("--1k")) {
            blockSize = 1024;
        } else if (arg.equals("-k") || arg.equals("--kermit")) {
            protocol = Protocol.KERMIT;
        } else if (arg.equals("-o") || arg.equals("--overwrite")) {
            overwrite = true;
        } else if (arg.equals("-r") || arg.equals("--receive")) {
            download = true;
        } else if (arg.equals("-s") || arg.equals("--send")) {
            download = false;
        } else if (arg.equals("-x") || arg.equals("--xmodem")) {
            protocol = Protocol.XMODEM;
        } else if (arg.equals("-y") || arg.equals("--ymodem")) {
            protocol = Protocol.YMODEM;
        } else if (arg.equals("-z") || arg.equals("--zmodem")) {
            protocol = Protocol.ZMODEM;
        } else {
            // This is an unknown option.
            System.err.println("jermit: invalid option -- '" + arg + "'");
            System.err.println("Try jermit --help for more information.");
            System.exit(2);
        }

        // This argument was consumed.
        return true;
    }

    /**
     * Main entry point.
     *
     * @param args Command line arguments
     */
    public static void main(final String [] args) {
        try {
            Jermit program = new Jermit(args);
            // Arguments are all handled, now start the program.
            program.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
