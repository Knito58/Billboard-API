## Command

`/luabox <x y z> restart|stop|status` restarts or stops the Lua Box at that position, or shows its state, folder and
output. It needs operator rights (level 2), so it also works in a command block.

## Settings

`config/billboard_api.properties` is written at the first start and read at game or server start. On a server it is the
server's file that counts.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `boxes_enabled` | true | Switches Lua Boxes off. |
| `boxes_ops_only` | false | Only operators may give boxes files, remove files, restart or stop them. Good for public servers. |
| `box_ms_per_tick` | 2 | Lua time per box and tick in milliseconds; then the program pauses until the next tick. |
| `box_max_busy_seconds` | 5 | A program that runs this many seconds without `sleep()` is stopped. |
| `box_too_busy_seconds` | 30 | A program that uses its whole time for this many seconds, with no `sleep()` longer than one tick, is stopped as too busy. |
| `box_max_lua_kb` | 64 | Largest `.lua` file. |
| `box_max_files` | 8 | Files a box may hold besides `main.lua`. |
| `box_max_file_kb` | 2048 | Largest other file. |
| `box_file_types` | png, jpg, jpeg, gif, bmp, txt, json, csv, lua | File types a box may hold besides `main.lua`. |
| `network_max_pipes` | 512 | Pipes in one network. |
| `network_max_clients` | 64 | Machines in one network. |
| `network_max_boxes` | 8 | Lua Boxes in one network. |
| `network_owner_check` | false | `true`: a box drives only machines placed by its own owner. `false`: any box drives any machine it reaches. |

A file written by version 0.1.0 has `network_owner_check=true` until you change it.
