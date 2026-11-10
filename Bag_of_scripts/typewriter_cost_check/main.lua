-- Typestones: what would a picture cost, and is it in the Typewriter? Changes nothing, needs no claim.
-- Needs: Typestones and a Typewriter on the network.
local SIZES = { { 2, 2 }, { 4, 4 }, { 8, 8 } }     -- width, height in stones

local tw = network.find("typewriter")[1]
if not tw then error("no Typewriter on the network, check network.list()", 0) end

for _, size in ipairs(SIZES) do
  local c = tw.getCost({ width = size[1], height = size[2] })
  print(string.format("%d x %d: %d stones (have %d), %d RGB dye (have %d)  %s",
    size[1], size[2], c.stones, c.haveStones, c.rgbDye, c.haveRgbDye,
    c.ok and "ok" or ("missing: " .. tostring(c.missing))))
end
