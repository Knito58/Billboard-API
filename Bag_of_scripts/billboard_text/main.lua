-- Billboard text settings and runoff: several messages, each one runs across the wall once.
-- Needs: a Billboard Engine on the network with a wall marked by the wand.
local MESSAGES = { "WELCOME", "SHOP OPEN 9-17", "SALE: DIAMONDS" }

engine.claim()
engine.setPixel("minecraft:gold_block")   -- the block the letters are made of
engine.setSize(1)                         -- letter size 1 to 9
engine.setSpeed(6)                        -- block columns per second, 1 to 10

while true do
  for _, text in ipairs(MESSAGES) do
    engine.setText(text)
    local ok, reason = engine.runOff()    -- comes in from the right, crosses once, wall is restored
    if not ok then error("runOff failed: " .. tostring(reason), 0) end
    while engine.isRunningOff() do sleep(0.5) end
  end
end
