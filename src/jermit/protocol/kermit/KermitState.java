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
package jermit.protocol.kermit;

/**
 * The states of the Kermit transfer protocol are documented in "Kermit
 * Protocol Manual, 6th Edition", 1986.  This state machine is a superset
 * of those states.
 */
enum KermitState {

    /**
     * Before the first byte is sent.
     */
    INIT,

    /**
     * Transfer(s) complete.
     */
    COMPLETE,

    /**
     * Transfer was aborted due to excessive timeouts, user abort, or
     * other error.
     */
    ABORT,

    /*
     * These states are analogous to those outlined in the Kermit
     * Protocol book.
     */

    KM_S,                   // Send Send-Init packet
    KM_SW,                  // Waiting for Ack to Send-Init

    KM_SF,                  // Send File-Header packet
    KM_SFW,                 // Waiting for Ack to File-Header

    KM_SA,                  // Send Attributes packet
    KM_SAW,                 // Waiting for Ack to Attributes

    KM_SDW,                 // Send File-Data packet (windowing)

    KM_SZ,                  // Send EOF packet
    KM_SZW,                 // Waiting for Ack to EOF

    KM_SB,                  // Send Break (EOT) packet
    KM_SBW,                 // Waiting for Ack to EOT

    KM_R,                   // Send initial NAK(0) to kickoff Send-Init
    KM_RW,                  // Wait for Send-Init packet

    KM_RF,                  // Wait for File-Header packet

    KM_RA,                  // Wait for Attributes packet

    KM_RDW                  // Wait for File-Data (windowing)
}

