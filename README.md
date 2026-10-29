# Billboard API

Fabric library mod for Minecraft 26.3. It holds the Lua Box and its Lua runtime (Cobalt), and data
pipes that connect Lua clients from other mods (such as the Billboard Engine and the Typestones
Typewriter) to a Lua Box as peripherals.

Used by [Billboard Engine](https://github.com/Knito58/billboard) and, optionally,
[Typestones](https://github.com/Knito58/Typestones).

Work in progress; no release yet.

## For Lua programs

A Lua Box reaches every Lua client that touches it, or touches a Data Pipe or another Lua Box joined to
it. Pipes join by themselves; sneak-click an arm (or a side) with an empty hand to switch that side off,
so two networks can run side by side. Craft 8 cardboard pipes from planks, paper, planks in a row. A box drives any machine it reaches; there is no permission system. `network_owner_check=true` in
`config/billboard_api.properties` limits a box to machines placed by its own owner, for servers that want it.

A Redstone Port (a Data Pipe and redstone dust, shapeless) connects Lua to vanilla redstone. It passes the network
on like a pipe, so it can sit inside a run of pipes, and it is a client of its own. Sides are `"north"`, `"south"`,
`"east"`, `"west"`, `"up"` and `"down"`:

```lua
local port = network.findByName("redstone_port_1")
local ok, who = port.claim()           -- before driving it: true, or false and who holds it
port.setOutput("up", 15)               -- or true; a piston above it extends. 0 or false lets go
print(port.getInput("north"))          -- 0 to 15: a lever, a pressure plate, a tripwire hook ...
print(port.getBlock("up").properties.extended)   -- name and block state of the neighbour
```

Right-click a port with a block to make it look like that block (the item is not used up); sneak-click it with an
empty hand to take the look off. Only the look is copied.

A program claims a port with `claim()` before it sets a signal (`setOutput` is an error without it). As on the
other machines, `claim()` gives true, or false and who holds it; `release()` gives true, or false if the program did
not hold it; `isClaimed()` gives false, or true and who holds it. A port is also let go when its program ends.
Reading (`getInput`, `getBlock`) needs no claim.
A port gives nothing until a program says so, gives weak power like a block of redstone (it drives what touches
it, not what sits behind a solid block), and lets go of all its signals when the program that claimed it ends or is stopped. A
side that a program drives reads 0 from `getInput`.

```lua
print(table.concat(network.list(), ", "))    -- sign_1, typewriter_1
local sign = network.findByName("sign_1")    -- nil if there is none
sign.setName("door_sign")                     -- true, or nil and the reason
sign.setLine(1, "Welcome")                    -- tw:type("HI") works too
for _, s in ipairs(network.find("sign")) do s.clear() end
local v = network.version()                   -- grows on every change, poll it
```

Every client has `getName()`, `setName(name)` and `getType()`. A new client gets the name
`<type>_<n>`, the lowest free number. Names are 1 to 32 letters, digits, `_` or `-`, compared without
case. When two networks join and a name exists twice, the newcomer is renamed and every box says so in
red. A handle to a machine that was broken or cut off raises `client "sign_1" is not reachable` and
works again when the machine is back. `engine` is still there for Script Box programs: the one Billboard
Engine of the network.

Vanilla signs and hanging signs are clients (`minecraft:sign`): `getLines(back)`, `getLine(n, back)`,
`setLine(n, text, back)`, `setLines({...}, back)`, `clear(back)`; `back` is optional, default front.

## For mod authors

Depend on `billboard_api` and register your block entity type once:

```java
BillboardApi.CLIENTS.registerForBlockEntity((typewriter, side) -> new TypewriterClient(typewriter), TYPEWRITER);

final class TypewriterClient implements LuaClient {
    private final TypewriterBlockEntity typewriter;
    TypewriterClient(TypewriterBlockEntity typewriter) { this.typewriter = typewriter; }
    public String type() { return "typestones:typewriter"; }
    public void methods(LuaMethods m) {
        m.add("getStatus", args -> typewriter.status().name().toLowerCase())
         .add("type", args -> { typewriter.type(args.string(1), args.optInteger(2, 1)); return null; });
    }
}
```

Methods run on the server thread inside the box's time slice, so take the order and do the work in your
own tick. Arguments and results are plain Java values (`null`, `Boolean`, `Number`, `String`, `byte[]`,
`List`, `Map`); an `Object[]` returns several values; a `CompletableFuture` makes the program wait until
it is done. Throw `LuaException` for a Lua error. `args.caller()` tells which program calls: stable for one run,
`isActive()` false after it ends, and `file(name)` gives the box's files (for a machine that shows an
image the program names). Only the package `de.knito.billboard.api` is API.
