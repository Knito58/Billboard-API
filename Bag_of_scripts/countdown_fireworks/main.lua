-- Countdown launch show: a countdown from 10 at the right end of the wall, then one redstone block walks
-- to the left end and sets off a contraption there (lamp, repeater line, fireworks dispenser ...).
-- Needs: a Billboard Engine on the network with a wall marked by the wand. Build the contraption
-- behind the left end of the wall, at the height of the text's middle row, with a redstone lamp or
-- comparator that picks up the passing dot.
local START, SIZE = 10, 1
local width = engine.getWallSize()
if not width then error("The engine has no wall marked (wand missing?)", 0) end
local function rightColumn(text) return math.max(0, width - (6 * #text - 1) * SIZE) end

engine.claim()
engine.setPixel("minecraft:gold_block")
engine.setSize(SIZE)
engine.setSpeed(1)                           -- slow, so the text does not move between go() and stop()
for n = START, 0, -1 do
  engine.setText(tostring(n))
  engine.go(rightColumn(tostring(n)))
  engine.stop()
  sleep(1)
end

engine.setSpeed(8)                           -- the 0 walks off to the left
engine.runOff()
while engine.isRunningOff() do sleep(0.2) end

engine.setPixel("minecraft:redstone_block")  -- one dot carries the signal to the left end
engine.setText("\183")                       -- the middle dot, exactly one pixel
engine.setSpeed(4)
engine.runOff()
while engine.isRunningOff() do sleep(0.2) end
print("Launched.")
