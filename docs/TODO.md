Jermit TODO List
================

0.0.3:

  Kermit tests:
    NPAD
    FileAttributes.NewFileAccessMode's
    Skip file - download
    Skip file - upload
    Resume

0.0.4:

  Zmodem:
    ZmodemSession
    ZmodemReceiver
    ZmodemSender

  Kermit:
    Windowing support
    KermitServerSocket
    KermitServer

0.1.0:

  SerialURLConnection
    XmodemURLConnection
    YmodemURLConnection
    ZmodemURLConnection
    KermitURLConnection

0.1.1:

  jermit.ui.kermit.Main
    - looks like C-Kermit
      - readline-like support (jLine2)
    - same command-line options

  Bidirectional Kermit:
    SelectableInputStream

0.2.0:

  Test under Eclipse

  Package for maven

  Windows:
    bin/jermit.bat

  Bug hunt

0.2.1:

  Final release to 1.0.0


General Notes
-------------

URL schemes:

  "kermit://..."
    Note that "kermit" port 1649 is IKS.  So KermitURLConnection needs
    to be able to handle both IKS and normal kermit receivers.

  "kermit-ssl://..."
    SSL/TLS kermit, on port 1650 (kermit port + 1).

  "kermit-udp://..."
    Same as kermit IKS, but using UDP packets.  Requires streaming
    feature to be disabled.  Similar -udp versions for all other
    protocols below.

  "xmodem://..."
  "xmodem-1k://..."
  "xmodem-crc://..."
  "xmodem-g://..."
  "ymodem://..."
  "ymodem-g://..."
  "zmodem://..."

  What to do with batch downloads?  Should I do something like a
  multi-part MIME message or jar/zip archive to capture them all?

Properties to control URLConnection behavior:
  jermit.kermit.streaming: default true
  jermit.kermit.windowing: default true - note that streaming
                           overrides windowing
  jermit.kermit.robustFilenames: default false
  jermit.kermit.longPackets: default true
  jermit.kermit.resend: default true
  jermit.kermit.download.forceBinary: default true
  jermit.kermit.upload.forceBinary: default true


Properties to control IKS:
  jermit.kermit.server.port
  jermit.kermit.server.downloadPath
  jermit.kermit.server.uploadPath
  jermit.kermit.server.cryptoType (= ssl, none (default))
  jermit.kermit.server.sslPort: default 1650
  jermit.kermit.server.sslCertificate
  jermit.kermit.server.disableUdp: default false


jermit.ui.rzsz.Main
  - command-line that resembles rz/sz
  - honors the same command-line options
  - produces same info/error messages
  - designed to directly replace rzsz used by minicom, konsole, etc.


jermit.ui.qodem.Main
  - looks like Qodem's download/upload screens, using colors and all


jermit.ui.kermit.Main
  - looks like C-Kermit
    - readline-like support
  - same command-line options


1.1.0 Wishlist
--------------

HSLink-like UI with dual chat screens


Regression Checklist
--------------------


Release Checklist √
-------------------

Eliminate all Eclipse warnings

Fix all marked TODOs in code

Eliminate DEBUG, System.err prints

Update written by date to current year:
    All code headers
    VERSION

Tag github

Upload to sonatype


Brainstorm Wishlist
-------------------



Bugs Noted In Other Programs
----------------------------

ckermit:

  - Sends last short packet with length exceeding receiver's MAXL but
    within sender's MAXL.

  - Receiver fails to write a small (< 10k) ASCII file to disk within
    a larger transfer of only binary files.
