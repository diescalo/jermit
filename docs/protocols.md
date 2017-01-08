Jermit Protocols
================

Xmodem
------

Jermit supports the following variants of Xmodem:

* Vanilla.  This is the "standard" Xmodem: 128 byte blocks, 10 second
  timeout, and a simple checksum.

* Relaxed.  This is vanilla Xmodem with a 100 second timeout.  This
  was developed for Qmodem(tm) users who had trouble with Xmodem
  transfers on CompuServe.

* CRC.  This variant uses 128 byte blocks, 10 second timeout, and a
  16-bit CRC function.

* 1K.  This variant has 1024 and 128 byte blocks, 10 second timeout,
  and a 16-bit CRC function.

* 1K/G.  This is the same as 1K but it does not wait on
  acknowledgements between blocks, just streaming the blocks.  Any
  error will terminate the transfer.

Xmodem has the unfortunate capability of corrupting files that
terminate in the ASCII CAN byte (0x18).  The final block of an Xmodem
transfer pads the file with CANs, and without the true file length
being known the receiver that is expected to strip these CANs may
strip one or more that are supposed to be there.

In the old days of BBSes, one would use Xmodem only to download a
program capable of running Ymodem or Zmodem.
