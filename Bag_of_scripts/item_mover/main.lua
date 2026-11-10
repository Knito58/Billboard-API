-- Item mover: iron nuggets from inventory A to inventory B.
-- Needs: two Inventory Ports on the network, named inventory_port_1 and inventory_port_2.
-- A chest (or any Transfer API storage) on the west side of port 1, a drawer or chest on the east side of port 2.
-- Rename the ports, sides or the item below to suit your build.
local FROM_PORT, FROM_SIDE = "inventory_port_1", "west"
local TO_PORT,   TO_SIDE   = "inventory_port_2", "east"
local ITEM  = "minecraft:iron_nugget"   -- nil moves everything, "#minecraft:logs" is a tag
local LIMIT = 64                        -- most items per call

local from = network.findByName(FROM_PORT)
local to   = network.findByName(TO_PORT)
if not from or not to then error("inventory ports not found, check network.list()", 0) end

-- moving needs the claim of every port it goes through
for _, port in ipairs({ from, to }) do
  local ok, who = port.claim()
  if not ok then error(port.getName() .. " is claimed by " .. tostring(who), 0) end
end

-- keeps emptying A into B; a full target just moves 0
while true do
  local ok, moved = pcall(from.move, FROM_SIDE, to.getName(), TO_SIDE, ITEM, LIMIT)
  if not ok then
    print("failed: " .. moved)          -- here moved holds the error message
    break
  end
  if moved > 0 then print("moved " .. moved) end
  sleep(1)
end

from.release(); to.release()
