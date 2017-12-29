Jermit
======

Jermit is a Java implementation of several serial file transfer
protocols.  General summary:

  * Xmodem and Ymodem are fully supported.  Flavors include vanilla,
    relaxed, CRC-16, 1K, 1K/G, and G.

  * Kermit is supported in both vanilla and streaming modes.  (Full
    duplex sliding windows are estimated to be in around 1Q 2018.)

  * Zmodem is estimated to be in around 3Q 2018.

  * Uploads and downloads occur between System.in/out and a local
    file.

  * Two interfaces are provided so far:

    1. Command-line utilities that can be drop-in replacements for
       rx/sx/rb/sb.

    2. A new 'jermit' command that transfers across System.in/out with
       a Swing-based transfer file dialog window that resembles Qodem.


Why?
----

Serial file transfer protocols -- primarily Xmodem, Ymodem, Zmodem,
and Kermit -- are still useful today:

  * Communicating with existing serial and dialup systems such as
    bulletin board systems (BBS), embedded devices, and PLCs.

  * Uploading new firmware to switches, routers, and other embedded
    devices.

  * Transferring files over an existing interactive ssh session.

  * Transferring files across noisy and unreliable links, for example
    3-wire RS-232 to an embedded system.

  * Transferring files over UDP to avoid TCP connection overhead.

Programmers seeking to use the serial file transfer protocols in their
applications have few good choices that are Open Source / Free
Software, especially if they are writing in languages other than C.
(And for those that do write in C, most of the existing
implementations (rzsz, lrzsz, ckermit, gkermit) were written in the
era of "C as portable assembly language" rather than "C as
object-oriented modules like the Linux kernel".)

Jermit is intended to provided a good baseline implementation of these
protocols, in a modern environment with easy-to-read source, and
liberally licensed for any use.  The protocols are designed to be
flexible building blocks: the remote side can be any InputStream +
OutputStream, and the local "file" can be file, byte buffer, or a
custom interface that provides a few file primitives.  These can thus
be easily incorporated into other APIs.  For example one could use a
KermitURLConnection to download from a C-Kermit Internet Kermit Server
using an address like "kermit://the.server.name/filename" .  It is
hoped that this code is very obvious in what it does, such that it
would be very straightforward to transliterate these protocols into
other languages (C++, C#, Go, Rust, Pascal, etc.).


License
-------

This program is licensed under the MIT License.  See the file LICENSE
for the full license text.


Usage
-----

This library is still in development.  The eventual intent is to
invoke Jermit code in one of these general ways:

  * Create a Receiver or Sender class, and call its run() method on a
    new Thread.  See the jermit.tests.{protocol} classes for examples.

  * Use one of the command-line interfaces.  A GUI transfer window is
    already available, as are drop-in replacements for the (l)rzsz
    utilities.  A replacement for ckermit with readline-like support
    is also in plan.

  * It is plan to provide a heirarchy of SerialURLConnection's
    (XmodemURLConnection, KermitURLConnection, etc.) that can be
    treated like a HttpURLConnetion.  The connections will support
    both UDP and TCP transports.


System Properties
-----------------

The following properties control features of Jermit:

  jermit.kermit.streaming
  -----------------------

  If true, use streaming mode (do not send ACKs during file data
  packets transfer).  Default: false.  Note that if streaming is
  enabled, full duplex sliding windows will be disabled.

  jermit.kermit.robustFilenames
  -----------------------------

  If true, when sending files convert filenames to Kermit's "common
  form": only numbers and letters, only one '.' in the filename, '.'
  cannot be the first or last character, and all uppercase.  Default:
  false.

  jermit.kermit.resend
  --------------------

  If true, support the RESEND option to resume file transfers.
  Default: true.

  jermit.kermit.longPackets
  -------------------------

  If true, support long packets (up to 9k).  Default: true.

  jermit.kermit.download.forceBinary
  ----------------------------------

  If true, treat all downloaded files as though they are binary (do
  not convert line endings).  Default: true.

  jermit.kermit.upload.forceBinary
  --------------------------------

  If true, treat all uploaded files as though they are binary (do not
  advertise as ASCII in the Attributes packet).  Default: true.


Known Issues / Arbitrary Decisions
----------------------------------

Some arbitrary design decisions had to be made when either the
obviously expected behavior did not happen or when a specification was
ambiguous.  This section describes such issues.

  - See docs/protocols.md for general discussion of supported
    protocols.


Roadmap
-------

Many tasks remain before calling this version 1.0.  See docs/TODO.md
for the complete list of tasks.


See Also
--------

These protocols are very loosely based on those developed for the
Qodem terminal emulator, taking advantage of the testing performed for
that project.  Qodem's other offshoot is a VT100/xterm-like Java
terminal in the Jexer jexer.tterminal.ECMA48 class: combining Jermit
and Jexer one could rather quickly put together a terminal emulator
that could both pass vttest and transfer files.

  * Qodem (licensed Public Domain) is available at
    http://qodem.sourceforge.net .

  * Jexer (licensed MIT) is available at
    https://github.com/klamonte/jexer .  A minimal working subset of
    the Jexer Swing backend code is reproduced here for use Jermit's
    "Qodem" UI (jermit.ui.qodem.Jermit).
