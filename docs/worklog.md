January 16, 2017

Xmodem send and receive are working, with a UI that looks pretty darn
close to Qodem's dialog boxes.  This is very nice indeed: having the
Qodem UI on a new Swing frame (based on Jexer's Swing backend) made it
very short work to get the UI fields updated.  And turns out
ThrottledInputStream was so useful that I had to keep it as a
permanent option.

Cleaning up the TODO's into a real roadmap.  Next up is Ymodem, and
then a 0.0.1 tag.

January 13, 2017

IT'S FRIDAY THE 13TH!!!  DUH DUH DUUUUH!!

I cleaned up the error handling so that in theory we will have the
right messages for network I/O error vs file I/O error.  Also stubbed
in rzsz.Main.  Once I get a basic transfer going it will be much
faster to add protocols.

Question to ponder: should jermit.rzsz support Kermit?  My initial
thought is no, that is what jermit.ui.ckermit.CKermit can do.  But
then again --kermit would be really easy to add, and most of the
Zmodem options have Kermit analogs.  Hmm, why not?  If people get
Kermit for "free" when they wanted Zmodem, better off for them.

January 12, 2017

Xmodem send is working now, and refactored to look reasonably nice.
Broke the functions up so that we don't have nested
try/while/try/while type loops.  Really enjoying the tests being ready
to just crank out and go with.

Let's commit this bit here.

Next up is either Ymodem or getting one of the UI's functional.  I
need to see if there is an easier way to test an external protocol's
UI than running minicom across a null modem cable.

January 8, 2017

Refactoring already.  :-) I added a ThrottledInputStream and
ThrottledOutputStream to somewhat better simulate analog speeds.  Next
up is NoisyInput/OutputStream to test bad blocks, and then refactoring
out the timeout logic to a new TimeoutInputStream.  (Which I think
might have exposed a bug in Qodem: I suspect at 300 baud Xmodem-1k and
Ymodem could timeout while waiting for enough bytes to come in for a
full block.  Need to check on that later someday.)  Filled in more of
the README, along with the big fat disclaimer that nothing is working
yet.

I will have to admit that Java has come a very long way since 1999.
ProcessBuilder made developing basic tests against rzsz easy-peasy:
I've already got several Xmodem ASCII and binary transfer test cases
in the bag.  Now I can refactor things and in seconds see what broke.
This is SOOOO much faster than the original cycle with Qodem.

The other nice thing is that the new design is much more generalizable
than Qodem's.  The protocols execute against streams, not buffers, so
no big state machines, chunking, "send data and now enter wait state",
or timeout checking everywhere.  Run something that looks more
procedural, and for async just toss it onto a different Thread.

Object-oriented code is making this a much easier codebase to handle
than Qodem's, wow.  I made a TimeoutInputStream and EOFInputStream to
wrap the input, which makes timeouts and unexpected EOFs simply catch
blocks outside the loop looking for a good packet.  Now the error
handling is just the protocol errors: bad sequence byte, bad checksum,
etc.  Man this is getting so much cleaner...by the time it is done I
will wonder why I didn't do it this way in the first place.  Heh.
(Answer: Java was dog slow in 2003.)

Let's pack in what this is so far and get a commit online.

January 6, 2017

Xmodem receive is working.  Wow, this was so much faster than the
first time.

January 5, 2017

Stubs for general protocols, tests, and Xmodem are coming along
nicely.  I'm thinking that this project will be quite a bit smaller
than Qodem's implementation, thanks in large part to Java's large
standard library making a lot of C things easier, but also to me
knowing much better what I need to do here.

January 3, 2017

New project beginning.  This one is the serial file transfer protocols
XYZmodem and Kermit.  Rather than call this one "DSZ" with "Kermit" as
an add-on, I'm going to make it "Kermit" with a "DSZ" fallback.
Therefore its name is Jermit and not JSZ.

The first significant issue is object naming.  The current style is to
name objects after nouns and methods as verbs.  So what IS the right
word for "a function to transfer a file using the XMODEM protocol"?
Apache commons-net2 uses naming like FTP and FTPClient.  Jexer uses
TelnetInputStream and TelnetSocket.

I could just cheap out and use XmodemHandler, and then subclass for
XmodemSender and XmodemReceiver.  Then I could eventually have
XmodemURLConnection and "xmodem://path/to/resource" support.
