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

import java.util.List;

import jermit.protocol.FileInfo;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.XmodemSender;
import jermit.protocol.XmodemSession;

import jermit.ui.qodem.jexer.bits.Color;
import jermit.ui.qodem.jexer.bits.CellAttributes;
import jermit.ui.qodem.jexer.event.TInputEvent;
import jermit.ui.qodem.jexer.io.SwingScreen;
import jermit.ui.qodem.jexer.io.SwingTerminal;

/**
 * This class displays a progress bar screen similar to Qodem.
 */
public class QodemUI implements Runnable {

    /**
     * The file transfer session.
     */
    private SerialFileTransferSession session;

    /**
     * Input events are processed by this Terminal.
     */
    private SwingTerminal terminal;

    /**
     * Screen displays characters.
     */
    private SwingScreen screen;

    /**
     * Public constructor.
     *
     * @param session the file transfer session
     */
    public QodemUI(final SerialFileTransferSession session) {
        this.session = session;

        // Create a screen
        screen = new SwingScreen();

        // Create the Swing event listeners
        terminal = new SwingTerminal(this, screen);
    }

    /**
     * Get keyboard, mouse, and screen resize events.
     *
     * @param queue list to append new events to
     */
    public void getEvents(final List<TInputEvent> queue) {
        if (terminal.hasEvents()) {
            terminal.getEvents(queue);
        }
    }

    /**
     * Render the progress dialog.
     */
    private void drawScreen() {
        CellAttributes border = new CellAttributes();
        border.setForeColor(Color.BLUE);
        border.setBackColor(Color.BLACK);
        border.setBold(true);

        CellAttributes window = new CellAttributes();
        window.setForeColor(Color.BLACK);
        window.setBackColor(Color.BLUE);

        screen.drawBox(0, 0, screen.getWidth(), screen.getHeight(),
            border, window, 2, false);

        String title = "Upload Status";
        if (session.isDownload()) {
            title = "Download Status";
        }

        int titleX = screen.getWidth() - title.length() - 2;
        if (titleX < 0) {
            titleX = 0;
        } else {
            titleX /= 2;
        }
        screen.putStringXY(titleX, 0, " " + title + " ", border);
        
        

        

        screen.flushPhysical();
    }

    /**
     * Monitor the file transfer, updating the progress bar and other
     * statistics as they are modified by the session.
     */
    public void run() {
        try {
            for (;;) {

                drawScreen();

                synchronized (session) {
                    SerialFileTransferSession.State state = session.getState();
                    if ((state == SerialFileTransferSession.State.ABORT) ||
                        (state == SerialFileTransferSession.State.END)
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
            } // for (;;)

            screen.shutdown();

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
