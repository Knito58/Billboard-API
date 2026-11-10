The **Inventory Port** connects Lua to the inventories around it. A program lists what a chest, barrel or furnace
holds and moves items between two inventories, without hoppers or item pipes.

It works with everything that offers its items through Fabric's **Transfer API**, which is the common way for mods to
do it. That includes modded storage:

- **Colossal Chests**: a Chest Interface of a finished chest is the whole chest. An interface of a chest that is not
  complete has nothing to offer, so `hasInventory` says `false`.
- **Extended Drawers**: a drawer, a compacting drawer, a shadow drawer and an Access Point (which is every drawer of
  its network at once).

Both mods register their storage that way (checked in their source); the port needs nothing from them. A mod that
does not use the Transfer API cannot be reached, and the port says so with `hasInventory`.

The port looks like a Data Pipe block and joins the network on all six sides, like the [Redstone Port](Redstone-Port),
so it can sit inside a run of pipes. It holds no items itself.

## In a program

Sides are `"north"`, `"south"`, `"east"`, `"west"`, `"up"` and `"down"`: the inventory *next to that side of the port*.

```lua
local from = network.findByName("inventory_port_1")   -- a chest on its west side
local to   = network.findByName("inventory_port_2")   -- a drawer on its east side
from.claim(); to.claim()

for _, item in ipairs(from.list("west")) do
  print(item.slot, item.name, item.count)
end

-- everything that is cobblestone, at most 1000 of it
print(from.move("west", to.getName(), "east", "minecraft:cobblestone", 1000))
```

| Function | Does | Returns |
| --- | --- | --- |
| `claim()`, `release()`, `isClaimed()` | Like on the other machines. | `true`, or `false` + who holds it, and so on |
| `hasInventory(side)` | Whether there is an inventory next to that side. | `true` or `false` |
| `size(side)` | The number of slots. | a number |
| `list(side)` | The slots that hold something. | `{ {slot = 1, name = "minecraft:stone", count = 12}, ... }` |
| `getItem(side, slot)` | One slot, counted from 1 in the container's own order. | `{slot, name, count}`, or `nil` if empty |
| `move(from, to, filter, limit)` | Moves items from one side of this port to another side of it. | the number moved |
| `move(from, port, to, filter, limit)` | Moves items from a side of this port to a side of **another inventory port**, given by its name. | the number moved |

`size`, `list`, `getItem` and `hasInventory` need no claim; `size`, `list` and `getItem` are an error where nothing is
next to the side.

### Moving

- `filter` is `nil` (everything), an item name such as `"minecraft:cobblestone"` (a name without a colon is taken as a
  vanilla item), or a tag with a `#`, such as `"#minecraft:logs"`. A name matches every variant of the item: an
  enchanted pickaxe is a `minecraft:diamond_pickaxe` too.
- `limit` is the most items one call moves. Without it a call moves **64**. Pass a big number to empty a chest.
- A move is one transaction. It takes what fits and stops: a full target is not an error, it just moves less. The result
  is how many items really moved, which may be 0.
- A call moves at most what the two inventories agree on; it does not wait or repeat. A program that wants to keep
  a chest empty calls `move` in its loop with a `sleep` between.
- Moving needs the **claim of every port it goes through**: both ports, for a move between two. Without it the call
  is an error. Claims end by themselves when the program ends, as everywhere else.
- Taking from and putting into the same inventory is an error, so a program cannot spin on one chest by mistake.

## Good to know

- The port never loads a chunk. An inventory in an unloaded chunk next to a port at the edge of a loaded one is an error
  ("in an unloaded chunk").
- Everything runs on the server thread, inside the box's time slice, like all machines. A move touches each slot of
  the source at most once, so even a big call is cheap; what costs is calling `list` on a huge inventory every tick.
  Ask `list` rarely, and use `getItem` or a `filter` when you know what you look for.
- Items of the same kind in different slots are moved together by one call; a `slot` argument is not needed.
- Furnaces, brewing stands and the like have different inputs and outputs on different sides, as for a hopper. The
  port is on one side of them, so it sees what that side offers.
- Fluids and energy go through the same Transfer API but are not part of the port.

Recipe: [Getting started](Getting-started#recipes).
