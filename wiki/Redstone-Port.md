The **Redstone Port** connects Lua to vanilla redstone. A program sets the signal on each of the port's six sides, so
a piston, door, lamp or note block next to it follows the program. It also reads what arrives: a lever, a pressure plate,
a tripwire hook, a button or a comparator.

The port looks like a Data Pipe block and joins the network on all six sides, like a pipe, so it can sit inside a run
of pipes. A machine touching it joins the network too.

## In a program

Sides are `"north"`, `"south"`, `"east"`, `"west"`, `"up"` and `"down"`, as the world sees them.

```lua
local port = network.findByName("redstone_port_1")
local ok, who = port.claim()               -- true, or false and who holds it
port.setOutput("up", 15)                   -- or true; a piston above the port extends
print(port.getInput("north"))              -- 0 to 15
print(port.getBlock("up").properties.extended)
```

| Function | Does | Returns |
| --- | --- | --- |
| `claim()` | Makes this program the only one that may set signals. Never waits. | `true`, or `false` + who holds it |
| `release()` | Gives the port back and switches all signals off. | `true`, or `false` if this program did not hold it |
| `isClaimed()` | | `false`, or `true` + who holds it |
| `setOutput(side, level)` | Sets the signal given on that side: 0 to 15, or `true` for 15, `false` for 0. An error without a claim. | |
| `getOutput(side)` | | the signal set on that side |
| `getInput(side)` | The signal arriving on that side. Needs no claim. | 0 to 15 |
| `getBlock(side)` | The block next to that side. Needs no claim. | `{ name = "minecraft:piston", properties = { extended = true, facing = "up" } }` |

## Rules

- A program must `claim()` the port before it sets a signal. Another program that tries gets `false` and who holds it.
  Reading needs no claim.
- Nothing is saved. A port gives nothing until a program says so. When the program that claimed it ends, is stopped or
  loses its box, the port lets go and all its signals go to 0, so a piston never stays out after its program.
- A port gives **weak power**, like a block of redstone: it drives what touches it, not what sits behind a solid block.
- A side the program drives reads 0 from `getInput`, because what comes back there is the port's own signal.
- A piston gives no signal. Read whether it is out with `getBlock(side).properties.extended`. Right after a signal
  changes, the piston may still be on its way, so wait a moment before reading it.
- A very short pulse (an observer's two ticks) can be missed if the program waits longer than that between reads.
  Levers, plates and buttons hold their state long enough.

## Camouflage

Right-click a port with a block in your hand and it looks like that block. The item is not used up. Sneak-click with an
empty hand to take the look off. Sneak-click with a block still places the block.

Only the look is copied: the port keeps its own hardness, sound and signals. Any block with an ordinary model works,
glass and slabs too, but the port stays a full block, so the result may look odd. Logs show their bark on the sides.
The look is saved with the port.

Recipe: [Getting started](Getting-started#recipes).
