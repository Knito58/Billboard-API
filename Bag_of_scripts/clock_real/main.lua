-- Real time clock: the time of the computer the world runs on (the server).
-- Needs: a Billboard Engine on the network with a wall marked by the wand.
-- show() does not redraw the same text, so calling it every second costs nothing.
engine.claim()
while true do
  engine.show(os.date("%H:%M"))
  sleep(1)
end
