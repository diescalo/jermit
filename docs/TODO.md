Jermit TODO List
================


XmodemReceiver:
  1K-G - looks like sx is hanging.  Did Qodem see that?
  Error handling:
    Can't open file
    File fails to write
    File fails to truncate
    Streaming error on -G

  cancelTransfer():
    - send CAN
    - keepPartial

  test on multiple threads

XmodemSender:
  Error handling:
    Can't open file
    File fails to read

  cancelTransfer():
    - send CAN


bin/jermit
  - how does one package a nicer script for Windows?
  - Pick UI style


Ymodem:
  YmodemReceiver
  YmodemSender


Zmodem:
  ZmodemSession
  ZmodemReceiver
  ZmodemSender


Kermit:
  KermitSession
  KermitReceiver
  KermitSender
  KermitServerSocket
  KermitServer


SerialURLConnection
  XmodemURLConnection
  YmodemURLConnection
  ZmodemURLConnection
  KermitURLConnection


URL schemes:

  "kermit://..."
    Note that "kermit" port 1649 is IKS.  So KermitURLConnection needs
    to be able to handle both IKS and normal kermit receivers.

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
  jermit.kermit.robust_filenames: default false
  jermit.kermit.long_packets: default true
  jermit.kermit.download.resend: default true
  jermit.kermit.download.force_binary: default true
  jermit.kermit.upload.force_binary: default true


Properties to control IKS:
  jermit.kermit.server.port
  jermit.kermit.server.downloadPath
  jermit.kermit.server.uploadPath
  jermit.kermit.server.cryptoType (= ssl, none (default))
  jermit.kermit.server.sslCertificate


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

