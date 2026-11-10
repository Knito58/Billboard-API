-- Typestones: several images, one after the other, on one Typewriter.
-- Needs: Typestones, a Typewriter with an output inventory at its back, smooth stone and RGB dye in its inputs,
-- and the image files dropped onto this box (a box holds main.lua and up to 8 more files).
local IMAGES = { "one.png", "two.png", "three.png" }
local OPTIONS = { width = 4, height = 4, fit = "fit", facing = "wall" }

local tw = network.find("typewriter")[1]
if not tw then error("no Typewriter on the network, check network.list()", 0) end

local ok, who = tw.claim()
if not ok then error("the Typewriter is claimed by " .. tostring(who), 0) end

for _, name in ipairs(IMAGES) do
  local started, reason = tw.drawImage(name, OPTIONS)
  if not started then
    print(name .. ": failed (" .. tostring(reason) .. "), skipped")
  else
    print(name .. ": started" .. (reason and (", waiting: " .. reason) or ""))
    while tw.isRunning() do sleep(5) end     -- the next picture only starts when this one is finished
    print(name .. ": done")
  end
end
tw.release()
