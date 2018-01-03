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
package jermit.ui.rzsz;

import jermit.protocol.FileInfo;
import jermit.protocol.Protocol;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.kermit.KermitReceiver;
import jermit.protocol.xmodem.XmodemReceiver;
import jermit.protocol.xmodem.XmodemSession;
import jermit.protocol.ymodem.YmodemReceiver;
import jermit.protocol.ymodem.YmodemSession;
import jermit.ui.posix.Stty;

/**
 * This class provides a main driver that behaves similarly to rzsz.  It is
 * intended to be a drop-in replacement for (l)rzsz for use by other
 * programs.
 */
public class Receive {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The protocol to select.  Most people want Zmodem, so default to that.
     * Even though Kermit is a lot better.
     */
    private Protocol protocol = Protocol.ZMODEM;

    /**
     * Whether or not to use CRC for Xmodem.  By default we will not.
     */
    private boolean crc = false;

    /**
     * The filename to download (Xmodem only).
     */
    private String xmodemFile = null;

    /**
     * The path to download files to.
     */
    private String downloadPath = System.getProperty("user.dir");

    /**
     * If true, permit overwrite of files.
     */
    private boolean overwrite = false;

    /**
     * How long to sleep in millis before starting a transfer.
     */
    private long sleepTime = -1;

    /**
     * If set, stop the transfer after this many millis.
     */
    private long stopTime = -1;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Construct a new instance.  Made private so that the only way to get
     * here is via the static main() method.
     *
     * @param args the command line args.
     */
    private Receive(final String [] args) {
        // Iterate the list of command line arguments, extracting options and
        // saving the last as a filename.
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
                    xmodemFile = args[i];
                }
            } else {
                // "--" was seen, so this is a filename.
                xmodemFile = args[i];
            }
        } // for (int i = 0; i < args.length; i++)

        if ((xmodemFile == null) && (protocol == Protocol.XMODEM)) {
            System.err.println("jermit: xmodem needs filename to receive to");
            System.err.println("Try jermit --help for more information.");
            System.exit(2);
        }
    }

    // ------------------------------------------------------------------------
    // Receive ----------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Execute the file transfer, whatever that is.
     */
    private void run() {
        SerialFileTransferSession session = null;
        Thread transferThread = null;
        Thread sessionStatusThread = null;
        SerialSessionLogger log = null;

        // Handle --delay-start option.  We emit nothing.
        if (sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                // SQUASH
            }
        }

        switch (protocol) {
        case XMODEM:
            // Default to vanilla.  If CRC is specified then switch to 1K.
            XmodemSession.Flavor flavor = XmodemSession.Flavor.VANILLA;
            if (crc == true) {
                // Permit 1K.
                flavor = XmodemSession.Flavor.X_1K;
            }

            // Open only the first file and receive data for it.
            XmodemReceiver rx = new XmodemReceiver(flavor, System.in,
                System.out, xmodemFile, overwrite);

            session = rx.getSession();
            log = new SerialSessionLogger(session);
            sessionStatusThread = new Thread(log);
            transferThread = new Thread(rx);
            break;
        case YMODEM:
            // Use vanilla only.  rb does not have a way to specify -G.
            YmodemSession.YFlavor yFlavor = YmodemSession.YFlavor.VANILLA;

            YmodemReceiver rb = new YmodemReceiver(yFlavor, System.in,
                System.out, downloadPath, false);

            session = rb.getSession();
            log = new SerialSessionLogger(session);
            sessionStatusThread = new Thread(log);
            transferThread = new Thread(rb);
            break;
        case ZMODEM:
            // TODO
            System.err.println("Zmodem not yet supported.");
            System.exit(10);
            break;
        case KERMIT:
            KermitReceiver rk = new KermitReceiver(System.in, System.out,
                downloadPath, false);
            session = rk.getSession();
            log = new SerialSessionLogger(session);
            sessionStatusThread = new Thread(log);
            transferThread = new Thread(rk);
            break;
        }

        // We need System.in/out to behave like a dumb file.
        Stty.setRaw();

        // Now spin up the session status thread and sending thread and wait
        // for them both to end.
        sessionStatusThread.start();
        transferThread.start();
        for (;;) {
            try {
                transferThread.join(10);
                sessionStatusThread.join(10);
            } catch (InterruptedException e) {
                // SQUASH
            }
            if (!transferThread.isAlive() && !sessionStatusThread.isAlive()) {
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
        System.out.println("Usage: {rz|rb|rx} [options] [filename.if.xmodem]");
        System.out.println("Receive files with ZMODEM/YMODEM/XMODEM/KERMIT protocol");
        System.out.println("    (X) = option applies to XMODEM only");
        System.out.println("    (Y) = option applies to YMODEM only");
        System.out.println("    (Z) = option applies to ZMODEM only");
        System.out.println("    (K) = option applies to KERMIT only");
        System.out.println("  -+, --append                append to existing destination file (Z/K)");
        System.out.println("  -a, --ascii                 IGNORED");
        System.out.println("  -b, --binary                IGNORED");
        System.out.println("  -B, --bufsize N             IGNORED");
        System.out.println("  -c, --with-crc              Use 16 bit CRC (X)");
        System.out.println("  -C, --allow-remote-commands IGNORED");
        System.out.println("  -D, --null                  IGNORED");
        System.out.println("      --delay-startup N       sleep N seconds before doing anything");
        System.out.println("  -e, --escape                escape control characters (Z)");
        System.out.println("  -E, --rename                rename any files already existing");
        System.out.println("      --errors                IGNORED");
        System.out.println("  -h, --help                  print this usage message");
        System.out.println("      --kermit                use KERMIT protocol");
        System.out.println("  -m, --min-bps N             IGNORED");
        System.out.println("  -M, --min-bps-time N        IGNORED");
        System.out.println("      --o-sync                IGNORED");
        System.out.println("  -O, --disable-timeouts      IGNORED");
        System.out.println("  -p, --protect               protect existing files (Z/K)");
        System.out.println("  -q, --quiet                 quiet, no progress reports");
        System.out.println("  -r, --resume                try to resume interrupted file transfer (Z/K)");
        System.out.println("  -R, --restricted            IGNORED");
        System.out.println("  -s, --stop-at {HH:MM|+N}    stop transmission at HH:MM or in N seconds");
        System.out.println("  -S, --timesync              IGNORED");
        System.out.println("      --syslog=off            IGNORED");
        System.out.println("      --tcp-server            IGNORED");
        System.out.println("      --tcp-client ADDR:PORT  IGNORED");
        System.out.println("  -u, --keep-uppercase        IGNORED");
        System.out.println("  -U, --unrestrict            IGNORED");
        System.out.println("  -v, --verbose               be verbose, provide debugging information");
        System.out.println("  -w, --windowsize N          Window is N bytes (Z)");
        System.out.println("  -X, --xmodem                use XMODEM protocol");
        System.out.println("  -y, --overwrite             Yes, clobber existing file if any");
        System.out.println("      --ymodem                use YMODEM protocol");
        System.out.println("  -Z, --zmodem                use ZMODEM protocol");
        System.out.println("");
        System.out.println("short options use the same arguments as the long ones");
    }

    /**
     * Process a one-argument option.
     *
     * @param arg the argument to process
     * @return true if this argument has been processed, false if it should
     * be passed to processTwoArgs().
     */
    private boolean processArg(final String arg) {
        /*
         * The following rz/rb/rx arguments will be recognized but ignored.
         *
         * These options are ignored for the following reasons:
         *
         *  1. They specify serial port behavior.
         *
         *  2. They specify POSIX C file flags.
         *
         *  3. They cause one side to execute a command.
         *
         *  3. They are unique to (l)rzsz (e.g. the tcp options,
         *     ascii/binary).
         *
         *   -a, --ascii
         *   -b, --binary
         *   -B NUMBER, --bufsize NUMBER
         *   -C, --allow-remote-commands
         *   -D, --null
         *   --errors
         *   -m N, --min-bps N
         *   -M N, --min-bps-time
         *   --o-sync
         *   -O, --disable-timeouts
         *   -R, --restricted
         *   -S, --timesync
         *   --syslog[=off]
         *   -u, --keep-uppercase
         *   -U, --unrestrict
         *   --tcp
         *   --tcp-client ADDRESS:PORT
         *   --tcp-server
         */

        // One-argument parameters ignored.
        if (arg.equals("-a") || arg.equals("--ascii")
            || arg.equals("-b") || arg.equals("--binary")
            || arg.equals("-C") || arg.equals("--allow-remote-commands")
            || arg.equals("-D") || arg.equals("--null")
            || arg.equals("--errors")
            || arg.equals("--o-sync")
            || arg.equals("-O") || arg.equals("--disable-timeouts")
            || arg.equals("-R") || arg.equals("--restricted")
            || arg.equals("-S") || arg.equals("--timesync")
            || arg.equals("--syslog") || arg.equals("--syslog=off")
            || arg.equals("-u") || arg.equals("--keep-uppercase")
            || arg.equals("-U") || arg.equals("--unrestrict")
            || arg.equals("-tcp")
            || arg.equals("-tcp-server")
        ) {
            return true;
        }

        // Two-argument parameters ignored.
        if (arg.equals("-B") || arg.equals("--bufsize")
            || arg.equals("-m") || arg.equals("--min-bps")
            || arg.equals("-M") || arg.equals("--min-bps-time")
            || arg.equals("--tcp-client")
        ) {
            return false;
        }

        // Two-agument parameters honored, but not here.  Ask for them to be
        // run through processTwoArgs().
        if (arg.equals("--delay-startup")
            || arg.equals("-l") || arg.equals("--framelen")
            || arg.equals("-L") || arg.equals("--packetlen")
            || arg.equals("-s") || arg.equals("--stop-at")
            || arg.equals("-t") || arg.equals("--timeout")
            || arg.equals("-w") || arg.equals("--windowsize")
        ) {
            return false;
        }

        // One-agument parameters honored.
        if (arg.equals("--version")) {
            System.out.println("jermit " + jermit.Version.VERSION);
            System.exit(0);
        } else if (arg.equals("-+") || arg.equals("--append")) {
            // Append transmitted data to an existing file.

            // TODO
        } else if (arg.equals("-c") || arg.equals("--with-crc")) {
            // Use 16-bit CRC for Xmodem.
            crc = true;
        } else if (arg.equals("-e") || arg.equals("--escape")) {
            // Escape all control characters; normally XON, XOFF, DLE,
            // CR-@-CR, and Ctrl-X are escaped.

            // TODO
        } else if (arg.equals("-E") || arg.equals("--rename")) {
            // Rename the new file if a file with the same name already
            // exists.

            // TODO
        } else if (arg.equals("-h") || arg.equals("--help")) {
            // Display help and exit.
            showUsage();
            System.exit(0);
        } else if (arg.equals("--kermit")) {
            // use KERMIT protocol.
            protocol = Protocol.KERMIT;
        } else if (arg.equals("-p") || arg.equals("--protect")) {
            // Protect existing destination files by skipping transfer if the
            // destination file exists.

            // TODO
        } else if (arg.equals("-q") || arg.equals("--quiet")) {
            // Quiet suppresses verbosity.

            // TODO
        } else if (arg.equals("-r") || arg.equals("--resume")) {
            // (ZMODEM) Resume interrupted file transfer.  If the source file
            // is longer than the destination file, the transfer commences at
            // the offset in the source file that equals the length of the
            // destination file.

            // TODO
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            // Verbose output to stderr.  More v's generate more output.

            // TODO
        } else if (arg.equals("-X") || arg.equals("--xmodem")) {
            // use XMODEM protocol.
            protocol = Protocol.XMODEM;
        } else if (arg.equals("-y") || arg.equals("--overwrite")) {
            // Overwrite any existing file with the same name.
            overwrite = true;
        } else if (arg.equals("--ymodem")) {
            // use YMODEM protocol.
            protocol = Protocol.YMODEM;
        } else if (arg.equals("-Z") || arg.equals("--zmodem")) {
            // use ZMODEM protocol.
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
     * Process a two-argument option.
     *
     * @param arg the argument to process
     * @param value the argument's option
     */
    private void processTwoArgs(final String arg, final String value) {

        if (arg.equals("--delay-startup")) {
            // Wait N seconds before doing anything.
            sleepTime = 1000 * Integer.parseInt(value);
        } else if (arg.equals("-s") || arg.equals("--stop-at")) {
            // Stop transmission at HH hours, MM minutes. Another variant,
            // using +N instead of HH:MM, stops transmission in N seconds.

            // TODO
        } else if (arg.equals("-t") || arg.equals("--timeout")) {
            // Change timeout to TIM tenths of seconds.

            // TODO
        } else if (arg.equals("-w") || arg.equals("--windowsize")) {
            // Limit the transmit window size to N bytes (ZMODEM).

            // TODO
        }

    }

    /**
     * Main entry point.
     *
     * @param args Command line arguments
     */
    public static void main(final String [] args) {
        try {
            Receive program = new Receive(args);
            // Arguments are all handled, now start the program.
            program.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
