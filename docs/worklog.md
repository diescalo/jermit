January 2, 2018

Prepping for the 0.0.3 release.  I have decided that Kermit streaming
will be enabled by default: nearly everyone will be using it over TCP
anyway, so may as well make it fast out of the box.  If Kermit ever
does make a comeback I sincerely hope it doesn't suffer a second round
of negging due to having defaults set for reliable rather than fast.

January 1, 2018

We are a lot more reliable over noisy links now.  Receiver detects
duplicated data packets and acks accordingly, sender now ignores
packets with error so it doesn't resend spuriously, and I think I have
detected maybe another true ckermit bug: it sent the last data packet
(once) with a len of 90, which exceeds my MAXL of 80 but was within
its MAXL of 94.  I detect and warn on that case now rather than NAK
immediately as I should.

...well, Jermit has justified itself.  I found three things broken in
Qodem: 1) Kermit send did not listen for receiver's resend file
position in the Attributes ack; 2) Kermit send did not listen for
receiver's request to skip in the Data ack; 3) Kermit send would hang
indefinitely at Attributes when NOT windowing or streaming.  Glad to
have another codebase now to test it against to find these things.

December 29, 2017

Covering more of the edge cases.  Most of the system properties to
control options are working OK.  Noisy transfers though are turning
out to be a real pain.  I might end up having to backport some stuff
to Qodem afterall, as these tests are a lot noisier than those were.
The packet type byte can get corrupted, that had to be checked.  NAKs
were pulling the wrong checkType (transliteration error from the D
code).  But it also looks like ckermit bails out too quickly for these
tests with "Too many retries".

December 28, 2017

Send is working.  Looks like I might have found a real bug in ckermit
finally, maybe: a small ASCII transfer (with 'kermit -V -r') doesn't
seem to save the file to disk.  gkermit works with the same file fine,
and ckermit does also if I set it to binary in the Attributes packet.
Streaming is also OK on both send and receive.

I'm going to punt windowing until the IKS release.  More tests to
cover the basics (resend, Ctrl-C cancel, noisy lines, etc.) and this
will be a nice Kermit to pause on.

December 27, 2017

Kermit receive is working now.  It is a bit glitchy, but it is there.
My first bit of Java code to speak Kermit, ever! :-)

This is really a nice day overall.  I have been looking forward to a
modern Kermit implementation for a number of years, and now it is
here.  Currently clocking about 5400 LOC, I think it will only be
about 8000 LOC in total to hit full duplex sliding windows, IKS, and
bidirectional support.  Hopefully all of that will be small enough to
be accessible for any who want to transliterate it to
JS/Go/Python/etc.

Send will be rather straightforward now, but I will have to get to
that this weekend sometime.

December 23, 2017

Kermit can read and write packets now, and can start to negotiate for
downloading files.

December 20, 2017

Stubs for Kermit continue along.  The encode side compiles, the decode
side is getting started.  I have also decided that the server will
support UDP (which Kermit is perfect for) and the SSL port will be
1650 (one plus kermit port).

I've been remembering the Kermit designs I had with the other
codebase, and one thing I wanted was true bidirectionality.  The
solution I had put together before would have broken the protocol, but
I am wondering if I can get there without real changes.  The IKS
breaks stuff, as the receiver and sender can both send ACKs
(presumably NAKs too) and they can also send non-ACKs (e.g. R, K, C
packets).  But thanks to a more careful reading of the doc:

    6.2. Terminator

    Any line terminator that is required by the system may be appended
    to the packet; this is carriage return (ASCII 15) by default. Line
    terminators are not considered part of the packet, and are not
    included in the count or check- sum.  Terminators are not
    necessary to the protocol, and are invisible to it, as are any
    characters that may appear between packets.

This means that I can put a channel number ahead of MARK and use that
to indicate the direction of transfer.  OR maybe better yet: each
channel has an independent MARK byte.  YES, this is the way forward: a
SelectableInputStream that wraps N InputStreams, looking for different
MARK bytes and switching the read() based on that.  This would make
bidirectional Kermit rather trivial: use different MARKs, a single
OutputStream smushing it all together, and a SelectableInputStream
that breaks up the incoming into the N channels.  Receiver and Sender
have no code changes, all the work setting it up happens in the UI
wrapper.  There it is: a backwards-compatible bidirectional Kermit!

So the remaining trick is figuring out how to get two Kermit nodes to
agree to use different MARKs when setting up the connection, and
before all of the other Kermit protocol happens.  I'm thinking a new
packet type can do this.  The Kermit web site has a file called
"packet-type-registry.txt", last updated in 1998, that has a lot of
defined packet and pseudopacket types.  One that remains free is 'b',
so I could create a new "bidirectional negotiation" packet that would
be the first packet sent by each Kermit side (before Send-Init or
ServInit).  'b' packets are intended to answer the following
questions:

  * Does my side request bidirectional?  If yes, try to negotiate
    bidir, and if successful do that; if unsuccessful either error out
    or proceed to do sends followed by receives or vice versa.  If
    bidir is not needed, say so.

  * Do both sides request bidir?  If yes, agree on which node will be
    called A and which will be called B, and then select the
    handedness of the transfer.  (Handedness: right-handed means that
    node A will use ^A as MARK for its SENDs to node B.  left-handed
    means node A will use ^B as MARK for its SENDs to node B.)

  * If neither side requests bidir, fallback to normal Kermit.

One simple method to determine handedness could be: each side creates
a random number, sends it to the other in the 'b' packet, and the
lowest number (unsigned) is node A.  And then we standardize on
right-handed transfers, and we are basically done.  This has the nice
effect of mirroring SendInit: one packet from each side and it is
done.  The major difference is that there is no ACK for this packet,
so failure to receive one before a ServInit or SendInit would lead to
downgrade.  Final ingredient: send this on the B channel, so that
non-bidir Kermits see nothing.

The rest of the 'b' packet could be used for additional information
around the channel, or for providing a true versioning scheme for
other protocol extensions.

December 17, 2017

I have some time before the end of the year to give this project a
little bit of work.  I'm aiming to get a basic Kermit implementation
going before the end of the year.  So let's see where we are at...

Missing a lot of package-info's, that's fixed.

Reformatting class structure to make PMD happier, as done for Jexer.

Hmm, I'm seeing a lot of functions in Ymodem that are direct calls to
super.  Do I need those?  Let's see what happens if I pull them out.
Nope, that right I need them to provide ymodem package access to those
xmodem package functions.  OK, so far so good.

Starting to stub in Kermit receive now.  This one will have to evolve
much like Xmodem did, one packet type at a time.  I had some D code
that was a rough semi-OOPish transliteration of the Qodem code, but
looking back at it now it is a baroque mess.  So this code will be a
mostly new offshoot closer to the original Qodem code in spirit.

June 21, 2017

With Qodem 1.0.0 released, I now have some time to put towards
Jermit.  It would be nice to get this wrapped up in 2017, so I can
then focus on Jexer for the next year or so.

I've got the status line up now, and it requires two CANs to cancel.
A couple of glitches fixed for the Qodem UI also.  Ready to tag 0.0.2
and begin stubbing in Kermit.

January 27, 2017

Ymodem send/receive is working, though the Qodem UI screen is
incomplete and fragile.  But the base protocols are there and I am
ready to pack it in for a short while.  Tagging this release 0.0.1.

January 21, 2017

Ymodem send is now working.  The code changes were rather small, just
adding a method to send the next block 0 and minor refactoring of
uploadFile().  Now all that's left is the UI updates to use it.

Comparing raw line counts of the Java Xmodem base and the Qodem base,
we are pretty close.  However, the Java code is split between
package-writeable and public-read-only methods, factored reasonably
nicely so as to have much less duplicated blocks (especially error
handling), and no gorped up select() and chunking stuff.  I think when
we get to Zmodem and Kermit it will be significantly smaller in the
end.

January 20, 2017

Ymodem receive is working fine now.  Nicely enough -G mode worked with
zero code changes: I had to actually add prints just to see that it
hadn't fell back to vanilla.  (And now I know for sure that sx cannot
do Xmodem-1K/G.)  It was more work figuring out how to keep the
package private set fields working across packages (between
XmodemSession and YmodemSession) than doing the actual protocol: my
YmodemReceiver had its own YmodemSession variable that was hiding
XmodemReceiver's XmodemSession variable.  Oops.  But now we have
download tests for one file in vanilla vs G and four files at G.

YmodemSender might come together this weekend.  If so, then there are
just the UI's to catch up to, and then I can tag the first release.
We will see, I might also need this weekend to vacation a bit for
sanity.

January 18, 2017

Xmodem as a basic protocol is complete, as far as I know.  The design
has settled into a reasonable place:

  * SerialFileTransferSession contains the values that will be exposed
    to a UI: blocks transferred, bytes total, list of files, current
    file, etc.

  * XmodemSession contains the pieces of the protocol itself: reading
    and writing blocks to the wire and file, negotiating the flavor,
    and also holding the main cancel flag.

  * XmodemReceiver/XmodemSender contain the main procedural loop:
    negotiate flavor, open file, send blocks, done.  They only carry a
    debug boolean and reference to the session.

This same pattern will follow cleanly for the other protocols.  Ymodem
will simply be Xmodem with flavor 1K or 1K/G, and an outer loop that
handles the 0 block.  Zmodem and Kermit will obviously have a lot more
code in the session object than the sender/receiver, but still the
sender/receiver can become simple state machines.

Now I am separating out Xmodem from SerialFileTransfer, which will
provide some hooks for Zmodem/Kermit to handle multi-file state
transitions later.

January 16, 2017

Xmodem send and receive are working, with a UI that looks pretty darn
close to Qodem's dialog boxes.  This is very nice indeed: having the
Qodem UI on a new Swing frame (based on Jexer's Swing backend) made it
very short work to get the UI fields updated.  And turns out
ThrottledInputStream was so useful that I had to keep it as a
permanent option.

Cleaning up the TODO's into a real roadmap.  Next up is Ymodem, and
then a 0.0.1 tag.

[ ...later... ]

Well, it's behaving a little better: you can cancel from the UI and it
will actually end the transfer a few seconds later.

I should add the status line to it too, so that people can see the
ESC/` message and skip option for Kermit.

So now we have a nice-looking mostly-behaving-OK Xmodem program.
About 13,000 lines of code between the combined Jermit and Jexer
codebases.

Jermit is getting clunky pretty fast too.  Making it "do what I mean"
is putting lots of corner case code into the exception handlers and
error blocks.  They really aren't that much smaller than the nasty
Qodem state machine.  I will do some refactoring to make XmodemSender
smaller, but it will always be more complicated with the
"send-listen-send" pattern going on there, where every listen needs to
have a timeout-and-resend option.  But better to do it now before
Ymodem comes along to make it even nastier.

Now on to bed.

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
