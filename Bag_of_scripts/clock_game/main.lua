-- Game time clock: Minecraft's time of day.
-- Needs: a Billboard Engine on the network with a wall marked by the wand.
-- os.time() gives hours with a fraction (14.5 = 14:30). "%02d" keeps two digits, so the
-- clock does not change its width before ten o'clock ("09:05", not "9:05").
engine.claim()
while true do
  local t = os.time()
  local h = math.floor(t)
  local m = math.floor((t - h) * 60)
  engine.show(string.format("%02d:%02d", h, m))
  sleep(0.5)             -- a Minecraft minute is shorter than a real second
end
