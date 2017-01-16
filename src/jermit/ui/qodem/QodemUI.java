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
import java.util.LinkedList;

import jermit.protocol.FileInfo;
import jermit.protocol.Protocol;
import jermit.protocol.SerialFileTransferMessage;
import jermit.protocol.SerialFileTransferSession;
import jermit.protocol.XmodemSender;
import jermit.protocol.XmodemSession;

import jermit.ui.qodem.jexer.bits.Color;
import jermit.ui.qodem.jexer.bits.CellAttributes;
import jermit.ui.qodem.jexer.bits.GraphicsChars;
import jermit.ui.qodem.jexer.event.TInputEvent;
import jermit.ui.qodem.jexer.event.TKeypressEvent;
import jermit.ui.qodem.jexer.event.TMouseEvent;
import jermit.ui.qodem.jexer.io.SwingScreen;
import jermit.ui.qodem.jexer.io.SwingTerminal;
import static jermit.ui.qodem.jexer.TKeypress.*;

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

    // Drawing colors used by drawScreen().
    CellAttributes border;
    CellAttributes window;
    CellAttributes label;
    CellAttributes text;

    /**
     * Public constructor.
     *
     * @param session the file transfer session
     */
    public QodemUI(final SerialFileTransferSession session) {
        this.session = session;

        // Set the drawing colors
        border = new CellAttributes();
        border.setForeColor(Color.BLUE);
        border.setBackColor(Color.BLACK);
        border.setBold(true);
        window = new CellAttributes();
        window.setForeColor(Color.BLACK);
        window.setBackColor(Color.BLUE);
        label = new CellAttributes();
        label.setForeColor(Color.WHITE);
        label.setBackColor(Color.BLUE);
        text = new CellAttributes();
        text.setForeColor(Color.YELLOW);
        text.setBackColor(Color.BLUE);
        text.setBold(true);

        // Create a screen
        screen = new SwingScreen();

        // Create the Swing event listeners
        terminal = new SwingTerminal(this, screen);
    }

    /**
     * If true, a mouse motion event has occurred.
     */
    private boolean mouseHasMoved = false;

    /**
     * Actual mouse coordinate X.
     */
    private int mouseX;

    /**
     * Actual mouse coordinate Y.
     */
    private int mouseY;

    /**
     * Old version of mouse coordinate X.
     */
    private int oldMouseX;

    /**
     * Old version mouse coordinate Y.
     */
    private int oldMouseY;

    /**
     * Get keyboard, mouse, and screen resize events.
     *
     * @param queue list to append new events to
     */
    private void getEvents() {
        List<TInputEvent> queue = new LinkedList<TInputEvent>();

        if (terminal.hasEvents()) {
            terminal.getEvents(queue);
        }

        for (TInputEvent event: queue) {
            if (event instanceof TMouseEvent) {
                TMouseEvent mouse = (TMouseEvent) event;
                synchronized (screen) {
                    if ((mouseX != mouse.getX()) || (mouseY != mouse.getY())) {
                        mouseHasMoved = true;
                        oldMouseX = mouseX;
                        oldMouseY = mouseY;
                        mouseX = mouse.getX();
                        mouseY = mouse.getY();
                    }
                }
            }

            if (event instanceof TKeypressEvent) {
                TKeypressEvent keypress = (TKeypressEvent) event;
                if (keypress.equals(kbCtrlC) || keypress.equals(kbEsc)) {
                    session.cancelTransfer(true);
                }
            }
        }
    }

    /**
     * Invert the cell color at a position.  This is used to track the mouse.
     *
     * @param x column position
     * @param y row position
     */
    private void invertCell(final int x, final int y) {
        synchronized (screen) {
            CellAttributes attr = screen.getAttrXY(x, y);
            attr.setForeColor(attr.getForeColor().invert());
            attr.setBackColor(attr.getBackColor().invert());
            screen.putAttrXY(x, y, attr, false);
        }
    }

    /**
     * Render the progress dialog.
     */
    private void drawScreen() {

        screen.drawBox(0, 0, screen.getWidth(), screen.getHeight(),
            border, window, 3, false);

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

        // Compute time values and generate strings
        FileInfo file = session.getCurrentFile();
        long now = System.currentTimeMillis();
        long transferTime = 0;
        long remainingTime = 0;
        int hours = 0;
        int minutes = 0;
        int seconds = 0;
        String timeElapsedString = "00:00:00";
        String remainingTimeString = "00:00:00";

        /*
         * Protocol name, filename, pathname
         */
        screen.putStringXY(2, 3, "File ", label);
        String filename = "";
        if (file != null) {
            filename = file.getLocalName();
        }
        screen.putStringXY(7, 3, filename, text);
        screen.putStringXY(27, 1, "Protocol ", label);
        screen.putStringXY(36, 1, session.getProtocolName(), text);

        screen.putStringXY(2, 4, "Path ", label);
        screen.putStringXY(7, 4, session.getTransferDirectory(), text);

        /*
         * Bytes and blocks total fields
         */
        screen.putStringXY(2, 6, "Bytes Total ", label);
        screen.putStringXY(14, 6, (file == null ? "0" :
                "" + file.getBytesTotal()), text);
        screen.putStringXY(27, 6, "Blocks Total ", label);
        screen.putStringXY(40, 6, (file == null ? "0" :
                "" +file.getBlocksTotal()), text);


        /*
         * Time fields
         */
        if (file != null) {
            if ((session.getState() == SerialFileTransferSession.State.END) ||
                (session.getState() == SerialFileTransferSession.State.ABORT)
            ) {
                transferTime = file.getEndTime() - file.getStartTime();
            } else {
                transferTime = now - file.getStartTime();
            }
            transferTime = transferTime / 1000;

            hours   = (int)  (transferTime / 3600);
            minutes = (int) ((transferTime % 3600) / 60);
            seconds = (int)  (transferTime % 60);
            timeElapsedString = String.format("%02d:%02d:%02d",
                hours, minutes, seconds);

            if ((session.getState() == SerialFileTransferSession.State.END) ||
                (session.getState() == SerialFileTransferSession.State.ABORT)
            ) {
                remainingTime = 0;
            }
            else if (file.getBytesTransferred() > 0) {
                remainingTime = (file.getBytesTotal() -
                    file.getBytesTransferred()) * transferTime /
                file.getBytesTransferred();
            }
            hours   = (int)  (remainingTime / 3600);
            minutes = (int) ((remainingTime % 3600) / 60);
            seconds = (int)  (remainingTime % 60);
            remainingTimeString = String.format("%02d:%02d:%02d",
                hours, minutes, seconds);

        }

        screen.putStringXY(51, 6, "Time Elapsed ", label);
        screen.putStringXY(64, 6, timeElapsedString, text);
        screen.putStringXY(51, 7, "++ Remaining ", label);
        screen.putStringXY(64, 7, remainingTimeString, text);

        /*
         * Bytes and blocks transferred fields
         */
        if (session.isDownload()) {
            screen.putStringXY(2, 7, "Bytes Rcvd  ", label);
            screen.putStringXY(27, 7, "Blocks Rcvd  ", label);
        } else {
            screen.putStringXY(2, 7, "Bytes Sent  ", label);
            screen.putStringXY(27, 7, "Blocks Sent  ", label);
        }
        screen.putStringXY(14, 7, (file == null ? "0" :
                "" + file.getBytesTransferred()), text);
        screen.putStringXY(40, 7, (file == null ? "0" :
                "" + file.getBlocksTransferred()), text);

        /*
         * Block size, error count, and efficiency
         */
        screen.putStringXY(2, 8, "Error Count ", label);
        screen.putStringXY(14, 8, (file == null ? "0" :
                "" + file.getErrorCount()), text);
        screen.putStringXY(27, 8, "Block Size   ", label);
        screen.putStringXY(40, 8, (file == null ? "0" :
                "" + file.getBlockSize()), text);

        /*
         * CPS and efficiency
         */
        long cps = (long) session.getTransferRate();
        screen.putStringXY(51, 9, "Chars/second ", label);
        screen.putStringXY(64, 9, "" + cps, text);

        screen.putStringXY(51, 8, "Efficiency   ", label);
        // TODO: see if the I/O is Throttled, and if so use bitsPerByte.
        screen.putStringXY(64, 8, "N/A", text);

        /*
         * Last message
         */
        screen.putStringXY(2, 10, "Status Msgs ", label);
        screen.putStringXY(14, 10, session.getCurrentStatus(), text);

        /*
         * Percent complete and progress bar
         */
        screen.putStringXY(2, 11, "Completion  ", label);
        int percentComplete = (file == null ? 0 :
            (int) session.getPercentComplete());
        screen.putStringXY(14, 11, percentComplete + " %", text);
        int i = 0;
        for (i = 0; i < (percentComplete * 50) / 100; i++) {
            screen.putCharXY(21 + i, 11, GraphicsChars.HATCH, text);
        }
        for (; i < 50; i++) {
            screen.putCharXY(21 + i, 11, GraphicsChars.BOX, text);
        }

        // Draw the mouse pointer - but only if it has ever moved.
        if (mouseHasMoved == true) {
            invertCell(mouseX, mouseY);
        }

        // Push to the user
        screen.flushPhysical();
    }

    /**
     * Monitor the file transfer, updating the progress bar and other
     * statistics as they are modified by the session.
     */
    public void run() {

        try {

            // At the end of the transfer, we can wait up to 3 seconds.
            long waitStart = 0;

            for (;;) {

                getEvents();

                synchronized (screen) {
                    drawScreen();
                }

                synchronized (session) {
                    SerialFileTransferSession.State state = session.getState();
                    if ((state == SerialFileTransferSession.State.ABORT) ||
                        (state == SerialFileTransferSession.State.END)
                    ) {
                        if (waitStart == 0) {
                            waitStart = System.currentTimeMillis();
                        } else {
                            // Wait up to 3 seconds before stopping.
                            if (System.currentTimeMillis() - waitStart > 3000) {
                                // All done, bail out.
                                break;
                            }
                        }
                    }
                }

                try {
                    // Wait for a notification on the session.
                    synchronized (session) {
                        session.wait(50);
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
