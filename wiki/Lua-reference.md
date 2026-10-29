What a Lua Box program sees besides standard Lua.

## Functions of the box

| Function | Does |
| --- | --- |
| `print(...)` | Writes a line into the box's output. |
| `sleep(seconds)` | Pauses the program. A program that never sleeps is stopped, see [Settings and commands](Settings-and-commands). |
| `require(name)` | Runs `name.lua` from the box once and returns what it returned. |
| `files.list()` | The names of all files of the box, `main.lua` included, sorted. |
| `files.read(name)` | The whole file as a string. An error if the box has no such file. |
| `files.lines(name)` | An iterator over the lines of the file, without line ends. |
| `os.time(kind)`, `os.date(format, time)`, `os.clock()`, `os.day(kind)`, `os.epoch(kind)` | Clock functions as in CC: Tweaked. `kind` is `"ingame"` (default), `"utc"` or `"local"`. |
| `textutils.formatTime(time, twentyFour)` | A time of day as text. |

Programs cannot write files.

## The network

| Function | Does | Returns |
| --- | --- | --- |
| `network.list()` | | the names of all machines the box reaches |
| `network.findByName(name)` | | the machine's handle, or `nil` |
| `network.find(type)` | `type` is a full id such as `"minecraft:sign"`, or just `"sign"` | a list of handles |
| `network.version()` | | a number that grows on every change; poll it to notice new machines |
| `engine` | The one Billboard Engine of the network, if the Billboard Engine mod is installed. | |

```lua
print(table.concat(network.list(), ", "))    -- sign_1, redstone_port_1
local sign = network.findByName("sign_1")    -- nil if there is none
for _, s in ipairs(network.find("sign")) do s.clear() end
```

## Every machine

Each handle has:

| Function | Does | Returns |
| --- | --- | --- |
| `getName()` | | the name |
| `setName(name)` | 1 to 32 letters, digits, `_` or `-` | `true`, or `nil` + the reason |
| `getType()` | | the type, such as `"minecraft:sign"` |

and the machine's own functions. A handle to a machine that was broken or cut off raises
`client "sign_1" is not reachable`, and works again when the machine is back. Some machines answer later; the program
then waits for them.

## Claiming

Machines that one program should drive at a time offer `claim()`, `release()` and `isClaimed()`:

- `claim()` returns `true`, or `false` plus who holds it. It never waits.
- `release()` returns `true`, or `false` if this program did not hold it.
- `isClaimed()` returns `false`, or `true` plus who holds it.
- The claim ends by itself when the program ends or is stopped, or its box is gone.

The [Redstone Port](Redstone-Port) works this way; so do the Billboard Engine and the Typewriter.

## Signs

Vanilla signs and hanging signs are machines of the type `minecraft:sign`. `back` is optional and means the back
of the sign; the default is the front.

| Function | Does |
| --- | --- |
| `getLines(back)` | The four lines. |
| `getLine(n, back)` | Line 1 to 4. |
| `setLine(n, text, back)` | Sets one line, up to 80 characters. |
| `setLines({...}, back)` | Sets all lines. |
| `clear(back)` | Empties the sign. |

## Redstone Port

`claim()`, `release()`, `isClaimed()`, `setOutput(side, level)`, `getOutput(side)`, `getInput(side)` and
`getBlock(side)`: see [Redstone Port](Redstone-Port).
