Jermit Protocols
================

Xmodem
------

Jermit supports the following variants of Xmodem:

* Vanilla.  This is the "standard" Xmodem: 128 byte blocks, 10 second
  timeout, and a simple checksum.

* Relaxed.  This is vanilla Xmodem with a 100 second timeout.  This
  was originally developed sometime before Qmodem(tm) release 2.2
  (August 24, 1986) for users who had trouble with Xmodem transfers on
  CompuServe.

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
being known the receiver will either strip these CANs (and thus may
strip one or more that are supposed to be there) or leave them (and
thus make the file bigger than it should be).

In the days of BBSes, one would use Xmodem only to download a program
capable of running Ymodem or Zmodem.

Ymodem
------

Jermit supports the following variants of Ymodem:

* Vanilla.  This is the "standard" Ymodem: 1024 byte blocks, 10 second
  timeout, a 16-bit CRC function, with file size and file modification
  time.

* Ymodem/G.  This is the same as vanilla but it does not wait on
  acknowledgements between blocks, just streaming the blocks.  Any
  error will terminate the transfer.

Ymodem/G is the fastest of the old serial protocols, having the least
overhead (4 bytes overhead per 1024 bytes data).  However, it is the
most fragile: it requires an 8-bit clean channel with error
correction.  (Also, the Omen Tech rzsz 'rb' Ymodem receiver cannot
specify streaming behavior, so it is only good for downloads.)
