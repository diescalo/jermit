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
package jermit.ui.rzsz;

import java.util.LinkedList;

import jermit.protocol.FileInfo;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.XmodemSender;
import jermit.protocol.XmodemSession;
import jermit.ui.posix.Stty;

/**
 * This class provides a main driver that behaves similarly to rzsz.  It is
 * intended to be a drop-in replacement for (l)rzsz for use by other
 * programs.
 */
public class Send {

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
        Thread sessionStatusThread = null;

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
            SerialSessionLogger log = new SerialSessionLogger(session);
            sessionStatusThread = new Thread(log);
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
            if ((!transferThread.isAlive()) &&
                (!sessionStatusThread.isAlive())
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
        System.out.println("Usage: {sz|sb|sx} [options] file ...");
        System.out.println("Send file(s) with ZMODEM/YMODEM/XMODEM/KERMIT protocol");
        System.out.println("    (X) = option applies to XMODEM only");
        System.out.println("    (Y) = option applies to YMODEM only");
        System.out.println("    (Z) = option applies to ZMODEM only");
        System.out.println("    (K) = option applies to KERMIT only");
        System.out.println("  -+, --append                append to existing destination file (Z/K)");
        System.out.println("  -2, --twostop               IGNORED");
        System.out.println("  -4, --try-4k                IGNORED");
        System.out.println("      --start-4k              IGNORED");
        System.out.println("  -8, --try-8k                IGNORED");
        System.out.println("      --start-8k              IGNORED");
        System.out.println("  -a, --ascii                 IGNORED");
        System.out.println("  -b, --binary                IGNORED");
        System.out.println("  -B, --bufsize N             IGNORED");
        System.out.println("  -c, --command COMMAND       IGNORED");
        System.out.println("  -C, --command-tries N       IGNORED");
        System.out.println("  -d, --dot-to-slash          IGNORED");
        System.out.println("      --delay-startup N       sleep N seconds before doing anything");
        System.out.println("  -e, --escape                escape all control characters (Z)");
        System.out.println("  -E, --rename                force receiver to rename files it already has");
        System.out.println("  -f, --full-path             IGNORED");
        System.out.println("  -i, --immediate-command CMD IGNORED");
        System.out.println("  -h, --help                  print this usage message");
        System.out.println("  -k, --1k                    send 1024 byte packets (X)");
        System.out.println("      --kermit                use KERMIT protocol");
        System.out.println("  -L, --packetlen N           limit subpacket length to N bytes (Z)");
        System.out.println("  -l, --framelen N            limit frame length to N bytes (l>=L) (Z)");
        System.out.println("  -m, --min-bps N             IGNORED");
        System.out.println("  -M, --min-bps-time N        IGNORED");
        System.out.println("  -n, --newer                 send file if source newer (Z/K)");
        System.out.println("  -N, --newer-or-longer       send file if source newer or longer (Z/K)");
        System.out.println("  -o, --16-bit-crc            use 16 bit CRC instead of 32 bit CRC (Z)");
        System.out.println("  -O, --disable-timeouts      IGNORED");
        System.out.println("  -p, --protect               protect existing destination file (Z/K)");
        System.out.println("  -r, --resume                resume interrupted file transfer (Z/K)");
        System.out.println("  -R, --restricted            IGNORED");
        System.out.println("  -q, --quiet                 quiet (no progress reports)");
        System.out.println("  -s, --stop-at {HH:MM|+N}    stop transmission at HH:MM or in N seconds");
        System.out.println("      --tcp-server            IGNORED");
        System.out.println("      --tcp-client ADDR:PORT  IGNORED");
        System.out.println("  -u, --unlink                unlink file after transmission");
        System.out.println("  -U, --unrestrict            IGNORED");
        System.out.println("  -v, --verbose               be verbose, provide debugging information");
        System.out.println("  -w, --windowsize N          Window is N bytes (Z)");
        System.out.println("  -X, --xmodem                use XMODEM protocol");
        System.out.println("  -y, --overwrite             overwrite existing files");
        System.out.println("  -Y, --overwrite-or-skip     overwrite existing files, else skip");
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
         * The following sz/sb/sx arguments will be recognized but ignored.
         *
         * These options are ignored for the following reasons:
         *
         *  1. They specify serial port behavior.
         *
         *  2. They cause one side to execute a command.
         *
         *  3. They are unique to (l)rzsz (e.g. the tcp options,
         *     ascii/binary).
         *
         *   -2, --twostop
         *   -4, --try-4k
         *   --start-4k
         *   -8, --try-8k
         *   --start-8k
         *   -a, --ascii
         *   -b, --binary
         *   -B NUMBER, --bufsize NUMBER
         *   -c COMMAND, --command COMMAND
         *   -C N, --command-tries N
         *   -d, --dot-to-slash
         *   -f, --full-path
         *   -i COMMAND, --immediate-command COMMAND
         *   -m N, --min-bps N
         *   -M N, --min-bps-time
         *   -O, --disable-timeouts
         *   -R, --restricted
         *   -S, --timesync
         *   --syslog[=off]
         *   -T, --turbo
         *   -U, --unrestrict
         *   --tcp
         *   --tcp-client ADDRESS:PORT
         *   --tcp-server
         *   -Y, --overwrite-or-skip
         */

        // One-argument parameters ignored.
        if (arg.equals("-2") || arg.equals("--twostop") ||
            arg.equals("-4") || arg.equals("--try-4k") ||
            arg.equals("--start-4k") ||
            arg.equals("-8") || arg.equals("--try-8k") ||
            arg.equals("--start-8k") ||
            arg.equals("-a") || arg.equals("--ascii") ||
            arg.equals("-b") || arg.equals("--binary") ||
            arg.equals("-d") || arg.equals("--dot-to-slash") ||
            arg.equals("-f") || arg.equals("--full-path") ||
            arg.equals("-O") || arg.equals("--disable-timeouts") ||
            arg.equals("-R") || arg.equals("--restricted") ||
            arg.equals("-S") || arg.equals("--timesync") ||
            arg.equals("--syslog") || arg.equals("--syslog=off") ||
            arg.equals("-T") || arg.equals("--turbo") ||
            arg.equals("-U") || arg.equals("--unrestrict") ||
            arg.equals("-tcp") ||
            arg.equals("-tcp-server") ||
            arg.equals("-TT")
        ) {
            return true;
        }

        // Two-argument parameters ignored.
        if (arg.equals("-B") || arg.equals("--bufsize") ||
            arg.equals("-c") || arg.equals("--command") ||
            arg.equals("-C") || arg.equals("--command-retries") ||
            arg.equals("-i") || arg.equals("--immediate-command") ||
            arg.equals("-m") || arg.equals("--min-bps") ||
            arg.equals("-M") || arg.equals("--min-bps-time") ||
            arg.equals("--tcp-client")
        ) {
            return false;
        }

        // Two-agument parameters honored, but not here.  Ask for them to be
        // run through processTwoArgs().
        if (arg.equals("--delay-startup") ||
            arg.equals("-l") || arg.equals("--framelen") ||
            arg.equals("-L") || arg.equals("--packetlen") ||
            arg.equals("-s") || arg.equals("--stop-at") ||
            arg.equals("-t") || arg.equals("--timeout") ||
            arg.equals("-w") || arg.equals("--windowsize")
        ) {
            return false;
        }

        // One-agument parameters honored.
        if (arg.equals("--version")) {
            System.out.println("jermit " + jermit.Version.VERSION);
            System.exit(0);
        } else if (arg.equals("-+") || arg.equals("--append")) {
            // Instruct the receiver to append transmitted data to an
            // existing file (ZMODEM only).

            // TODO
        } else if (arg.equals("-e") || arg.equals("--escape")) {
            // Escape all control characters; normally XON, XOFF, DLE,
            // CR-@-CR, and Ctrl-X are escaped.

            // TODO
        } else if (arg.equals("-E") || arg.equals("--rename")) {
            // Force the sender to rename the new file if a file with the
            // same name already exists.

            // TODO
        } else if (arg.equals("-h") || arg.equals("--help")) {
            // Display help and exit.
            showUsage();
            System.exit(0);
        } else if (arg.equals("-k") || arg.equals("--1k")) {
            // (XMODEM/YMODEM) Send files using 1024 byte blocks rather than
            // the default 128 byte blocks.  1024 byte packets speed file
            // transfers at high bit rates.
            blockSize = 1024;
        } else if (arg.equals("--kermit")) {
            // use KERMIT protocol.
            protocol = Protocol.KERMIT;
        } else if (arg.equals("-n") || arg.equals("--newer")) {
            // (ZMODEM) Send each file if destination file does not exist.
            // Overwrite destination file if source file is newer than the
            // destination file.

            // TODO
        } else if (arg.equals("-N") || arg.equals("--newer-or-longer")) {
            // (ZMODEM) Send each file if destination file does not exist.
            // Overwrite destination file if source file is newer or longer
            // than the destination file.

            // TODO
        } else if (arg.equals("-o") || arg.equals("--16-bit-crc")) {
            //  (ZMODEM) Disable automatic selection of 32 bit CRC.

            // TODO
        } else if (arg.equals("-p") || arg.equals("--protect")) {
            // (ZMODEM) Protect existing destination files by skipping
            // transfer if the destination file exists.

            // TODO
        } else if (arg.equals("-q") || arg.equals("--quiet")) {
            // Quiet suppresses verbosity.

            // TODO
        } else if (arg.equals("-r") || arg.equals("--resume")) {
            // (ZMODEM)  Resume  interrupted  file transfer.   If  the
            // source file  is longer  than the destination  file, the
            // transfer  commences at  the offset  in the  source file
            // that equals the length of the destination file.

            // TODO
        } else if (arg.equals("-u")) {
            // Unlink the file after successful transmission.

            // TODO
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            // Verbose output to stderr.  More v's generate more output.

            // TODO
        } else if (arg.equals("-X") || arg.equals("--xmodem")) {
            // use XMODEM protocol.
            protocol = Protocol.XMODEM;
        } else if (arg.equals("-y") || arg.equals("--overwrite")) {
            // Instruct a ZMODEM receiving program to overwrite any existing
            // file with the same name.

            // TODO
        } else if (arg.equals("-Y") || arg.equals("--overwrite-or-skip")) {
            // Instruct a ZMODEM receiving program to overwrite any existing
            // file with the same name, and to skip any source files that do
            // not have a file with the same pathname on the destination
            // system.

            // TODO
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
     * Construct a new instance.  Made private so that the only way to get
     * here is via the static main() method.
     *
     * @param args the command line args.
     */
    private Send(final String [] args) {
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
            System.err.println("jermit: need at least one file to send");
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
            Send program = new Send(args);
            // Arguments are all handled, now start the program.
            program.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
