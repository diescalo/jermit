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

import jermit.protocol.SerialFileTransferMessage;
import jermit.protocol.SerialFileTransferSession;

/**
 * SerialSessionLogger emits messages to System.err as a file transfer
 * progresses.
 */
public class SerialSessionLogger implements Runnable {

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The file transfer session.
     */
    private SerialFileTransferSession session;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor.
     *
     * @param session the file transfer session
     */
    public SerialSessionLogger(final SerialFileTransferSession session) {
        this.session = session;
    }

    // ------------------------------------------------------------------------
    // Runnable ---------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Monitor the file transfer and display messages as they are added to
     * the session.
     */
    public void run() {
        try {
            // Emit messages as they are recorded.
            int messageCount = 0;
            for (;;) {
                synchronized (session) {
                    SerialFileTransferSession.State state = session.getState();
                    if ((state == SerialFileTransferSession.State.ABORT) 
                        || (state == SerialFileTransferSession.State.END)
                    ) {
                        // All done, bail out.
                        break;
                    }
                }

                try {
                    // Wait for a notification on the session.
                    synchronized (session) {
                        session.wait(100);
                    }
                } catch (InterruptedException e) {
                    // SQUASH
                }

                synchronized (session) {
                    if (session.messageCount() > messageCount) {
                        for (int i = messageCount; i < session.messageCount();
                             i++) {
                            emitMessage(session.getMessage(i));
                            messageCount++;
                        }
                    }
                }

            } // for (;;)

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // SerialSessionLogger ----------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Emit a message to System.err.
     *
     * @param message the message to emit
     */
    private void emitMessage(final SerialFileTransferMessage message) {
        // TODO: timestamp, info/error, etc.
        // Make it match rzsz if possible.
        System.err.println(message.getMessage());
    }

}
