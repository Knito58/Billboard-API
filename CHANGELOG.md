# Changelog

## 0.1.0 (not released)

- Project set up: Fabric mod for Minecraft 26.3 with Cobalt, the Lua VM, packed in under its own package name.
- Lua Box, taken over from Billboard Engine's Script Box: the program is `main.lua`, plus up to 8 more files of the
  types the server allows (images, `.txt`, `.json`, `.csv`, `.lua`). Other `.lua` files are modules for `require`.
  Programs read the box's files with `files.list()`, `files.read(name)` and `files.lines(name)`.
- Everybody may press Restart and Stop; only the owner and operators may change the files.
- Settings in `config/billboard_api.properties`; on the first start the Script Box limits are taken over from
  Billboard Engine's `billboard.properties`.
- `/luabox <pos> restart|stop|status` for operators and command blocks.
- Worlds from Billboard Engine 1.x: Script Boxes become Lua Boxes (block, item and block entity alias from
  `billboard:script_box`). Each keeps its files, which move to `billboard_api/boxes/`, and its one program is
  renamed to `main.lua`. Not while Billboard Engine 1.x itself is installed.
- Lua clients: mods register machines with `BillboardApi.CLIENTS` (a Fabric block API lookup). Programs get
  `network.list()`, `network.findByName(name)`, `network.find(type)`, `network.version()` and a handle per client
  with `getName()`, `setName(name)`, `getType()` and the client's own functions. A client may answer later with a
  `CompletableFuture`; the program waits for it.
- A box reaches clients that touch it, a Lua Box or a pipe joined to it. Names are kept by the library on the machine;
  a name clash gets the newcomer renamed, and every box tells. Settings `network_max_clients=64`,
  `network_max_boxes=8`, `network_owner_check=true`: a box drives only machines its owner placed; machines from
  before go to the owner of the first box that reaches them.
- `engine` still works for Script Box programs: the one Billboard Engine of the network.
- Vanilla signs and hanging signs are Lua clients, `minecraft:sign`.
- Data Pipe (`billboard_api:data_pipe`, 6 from copper ingot, redstone, copper ingot): joins Lua Boxes and Lua clients
  into one network. Sneak-click an arm with an empty hand to switch that side off. `network_max_pipes=512`.
- Box screen: a Network button swaps the file list for the machines the box reaches, with their type; click one to
  rename it. Machines of other owners are shown greyed out.
- `LuaArgs.caller()`: the calling program (`LuaCaller`: `describe()`, `isActive()`, `file(name)`), so a machine can
  let one program claim it, and show files of the calling box.
