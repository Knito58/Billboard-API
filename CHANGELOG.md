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
