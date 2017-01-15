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
package jermit.ui.qodem;

import java.util.LinkedList;

import jermit.protocol.FileInfo;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.XmodemSender;
import jermit.protocol.XmodemSession;
import jermit.ui.posix.Stty;

/**
 * This class provides a main driver that produces a progress bar screen
 * similar to Qodem.  The file transfer is processed on System.in. and
 * System.out, while the progress bar screen is a separate Swing window.
 */
public class Jermit {

    /**
     * The available protocols.
     */
    enum Protocol {
        XMODEM,
        YMODEM,
        ZMODEM,
        KERMIT
    }

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
     * The list of filenames read from the command line.
     */
    private LinkedList<String> fileArgs;

    /**
     * Process a two-argument option.
     *
     * @param arg the argument to process
     * @param value the argument's option
     */
    private void processTwoArgs(final String arg, final String value) {
        // TODO
    }

    /**
     * Execute the file transfer, whatever that is.
     */
    private void run() {
        SerialFileTransferSession session = null;
        Thread transferThread = null;
        Thread uiThread = null;

        switch (protocol) {
        case XMODEM:
            // Allow CRC.  This will automatically fallback to vanilla if the
            // receiver initiates with NAK instead of 'C'.  We can't default
            // to 1K because we can't guarantee that the receiver will know
            // how to handle it.
            XmodemSession.Flavor flavor = XmodemSession.Flavor.CRC;
            if (blockSize == 1024) {
                // Permit 1K.  This will fallback to vanilla if they use NAK.
                flavor = XmodemSession.Flavor.X_1K;
            }

            // Open only the first file and send it.
            XmodemSender sx = new XmodemSender(flavor, System.in, System.out,
                fileArgs.get(0));

            session = sx.getSession();
            QodemUI ui = new QodemUI(session);
            uiThread = new Thread(ui);
            transferThread = new Thread(sx);
            break;
        case YMODEM:
            // TODO
            System.err.println("Ymodem not yet supported.");
            System.exit(10);
        case ZMODEM:
            // TODO
            System.err.println("Zmodem not yet supported.");
            System.exit(10);
        case KERMIT:
            // TODO
            System.err.println("Kermit not yet supported.");
            System.exit(10);
        }

        // We need System.in/out to behave like a dumb file.
        // DEBUG: don't do this, be able to exit. with ^C.
        // Stty.setRaw();

        // Now spin up the session status thread and sending thread and wait
        // for them both to end.
        uiThread.start();
        transferThread.start();
        for (;;) {
            try {
                transferThread.join(10);
                uiThread.join(10);
            } catch (InterruptedException e) {
                // SQUASH
            }
            if ((!transferThread.isAlive()) &&
                (!uiThread.isAlive())
            ) {
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
    }

    /**
     * Process a one-argument option.
     *
     * @param arg the argument to process
     * @return true if this argument has been processed, false if it should
     * be passed to processTwoArgs().
     */
    private boolean processArg(final String arg) {
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
        } else if (arg.equals("--kermit")) {
            protocol = Protocol.KERMIT;
        } else if (arg.equals("--xmodem")) {
            protocol = Protocol.XMODEM;
        } else if (arg.equals("--ymodem")) {
            protocol = Protocol.YMODEM;
        } else if (arg.equals("--zmodem")) {
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
                        if (i < args.length - 2) {
                            processTwoArgs(args[i], args[i + 1]);
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

        if (fileArgs.size() == 0) {
            System.err.println("jermit: need at least one file to transfer");
            System.err.println("Try jermit --help for more information.");
            System.exit(2);
        }
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
