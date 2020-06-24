# QR-Zoom
Launch Zoom meeting from QR code of Zoom web address

Rationale: In my company we use Zoom extensively (at the time of writing) and our internal scheduler tool pops up a QR code a few minutes ahead of a Zoom meeting. Said QR code points to the web address of the meeting, which is great if you use a phone (if a bit long-winded), but if you use the same computer you not only have to type in the meeting number (you can't copy and paste across VDI border), but also put up with the sadness caused by the wasted effort that went into that QR code.

Solution: This tool will (attempt to) launch the system screenshot tool for me to snap a rectangle around the QR code; then read it and launch Zoom locally, directly to the meeting in question.
