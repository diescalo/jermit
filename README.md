Jermit
======

Jermit is a Java implementation of several serial file transfer
protocols.


WARNING!!  THIS PROJECT IS JUST BEGINNING.  NOT MUCH IS WORKING YET!!
Right now the only thing working is basic Xmodem uploads and downloads
between System.in/out and a local file, using a Swing frame that
resembles the Qodem file transfer dialog.


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

Programmers seeking to use the serial file transfer protocols in their
applications have very few good choices that are Open Source or Free
Software, especially if they write in languages other than C.  For
those that do write in C: the original public domain rzsz code is a
complete mess; lrzsz is better but is licensed GPL and cannot be
directly incorporated into proprietary application; the various kermit
implementations from the official Kermit Project are under encumbered
licenses and/or make liberal of use of transport-level operating
system calls and legacy C language features (needed to support as many
systems as it does); and most other codebases are designed to run as
standalone programs speaking to stdin/stdout.

Jermit is intended to provided a good baseline implementation of these
protocols, in a modern environment with easy-to-read source, and
liberally licensed for any use.  The protocols are designed to be
flexible building blocks: the remote side can be any InputStream +
OutputStream, and the local "file" can be file, byte buffer, or a
custom interface that provides a few file primitives.  These can thus
be easily incorporated into other APIs.  For example one can easily
use a KermitURLConnection to download from a C-Kermit Internet Kermit
Server using an address like "kermit://the.server.name/filename" .

These protocols are very loosely based on those developed for the
Qodem terminal emulator, taking advantage of the testing performed for
that project.  Qodem (licensed Public Domain) is available at
http://qodem.sourceforge.net .  Qodem's other offshoot is a
VT100/xterm-like Java terminal in the Jexer jexer.tterminal.ECMA48
class: combining Jermit and Jexer one could rather quickly put
together a terminal emulator that could both pass vttest and transfer
files.  Jexer (licensed MIT) is available at
https://github.com/klamonte/jexer .


License
-------

This program is licensed under the MIT License.  See the file LICENSE
for the full license text.


Usage
-----

The library is currently under initial development, usage patterns are
still being worked on.
