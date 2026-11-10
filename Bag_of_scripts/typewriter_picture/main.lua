-- Typestones: have the Typewriter turn one image into picture stones.
-- Needs: Typestones, a Typewriter on the network with an inventory (chest) at its back for the output,
-- smooth stone and RGB dye in the Typewriter's inputs, and the image file dropped onto this box.
-- Cost: one smooth stone and one RGB dye per picture stone.
local IMAGE = "picture.png"      -- PNG, JPEG or GIF (first frame), up to 4 MB
local OPTIONS = {
  width  = 4,                    -- size in stones (the server's setting limits it, 8 x 8 by default)
  height = 4,
  fit    = "fit",                -- "fit" whole image, "fill" covers all, "stretch" pulls to the shape
  facing = "wall",               -- "wall", "floor" or "ceiling"
}

local tw = network.find("typewriter")[1]
if not tw then error("no Typewriter on the network, check network.list()", 0) end

local ok, who = tw.claim()
if not ok then error("the Typewriter is claimed by " .. tostring(who), 0) end

local cost = tw.getCost(OPTIONS)
print(cost.stones .. " stones, " .. cost.rgbDye .. " RGB dye needed")
if not cost.ok then print("not enough yet: " .. tostring(cost.missing) .. " (it will wait for items)") end

local started, reason = tw.drawImage(IMAGE, OPTIONS)
if not started then
  tw.release()
  error("drawImage failed: " .. tostring(reason), 0)    -- e.g. not_found, busy, too_big, no_output
end
if reason then print("started, but waiting: " .. reason) end   -- e.g. needs_rgb_dye

while tw.isRunning() do
  local made, total = tw.getProgress()
  print(made .. " / " .. total .. "  " .. tw.getStatusText())
  sleep(5)
end
print("done: " .. tw.getStatusText())
tw.release()
