Jermit
======

Jermit is a Java implementation of several serial file transfer
protocols.  General summary:

  * Xmodem and Ymodem are fully supported.  Flavors include vanilla,
    relaxed, CRC-16, 1K, 1K/G, and G.

  * Kermit is in progress.  Receive works, send is coming soon.

  * Zmodem is estimated to be in around 3Q 2018.

  * Uploads and downloads occur between System.in/out and a local
    file.

  * Two interfaces are provided so far:

    1. Command-line utilities that can be drop-in replacements for
       rx/sx/rb/sb.

    2. A new 'jermit' command that uses a Swing frame that resembles
       the Qodem file transfer dialog.


Why?
----

Serial file transfer protocols -- primarily Xmodem, Ymodem, Zmodem,
and Kermit -- are still useful today:

  * Communicating with old serial or dialup systems such as bulletin
    board systems (BBS).

  * Uploading new firmware to switches, routers, and other embedded
    devices.

  * Transferring files over an existing interactive ssh session.

  * Transferring files across noisy and unreliable links, for example
    3-wire RS-232 to an embedded system.

  * Transferring files over UDP to avoid TCP connection overhead.

Programmers seeking to use the serial file transfer protocols in their
applications have very few good choices that are Open Source or Free
Software, especially if they are writing in languages other than C.
For those that do write in C: the original public domain rzsz code is
a complete mess; lrzsz is better but is licensed GPL and cannot be
directly incorporated into a proprietary application; the various
kermit implementations from the official Kermit Project were under
encumbered licenses for a long time (but now are BSD), and make
liberal of use of transport-level operating system calls and legacy C
language features (needed to support as many systems as it does); and
most other codebases are designed to run as standalone programs
speaking to stdin/stdout.

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

These protocols are very loosely based on those developed for the
Qodem terminal emulator, taking advantage of the testing performed for
that project.  Qodem (licensed Public Domain) is available at
http://qodem.sourceforge.net .  Qodem's other offshoot is a
VT100/xterm-like Java terminal in the Jexer jexer.tterminal.ECMA48
class: combining Jermit and Jexer one could rather quickly put
together a terminal emulator that could both pass vttest and transfer
files.  Jexer (licensed MIT) is available at
https://github.com/klamonte/jexer .  A minimal working subset of the
Jexer Swing backend code is reproduced here for use Jermit's "Qodem"
UI (jermit.ui.qodem.Jermit).


License
-------

This program is licensed under the MIT License.  See the file LICENSE
for the full license text.


Usage
-----

This library is still in development.  The eventual intent is to
invoke Jermit code in three general ways:

  * Create a Receiver or Sender class, and call its run() method on a
    new Thread.  See the jermit.tests.{protocol} classes for examples.

  * Use one of the command-line interfaces.  A GUI transfer window is
    already available, as are drop-in replacements for the (l)rzsz
    utilities.  A replacement for ckermit with readline-like support
    is also in plan.

  * It is plan to provide a heirarchy of SerialURLConnection's
    (XmodemURLConnection, KermitURLConnection, etc.) that can be
    treated like a HttpURLConnetion.
