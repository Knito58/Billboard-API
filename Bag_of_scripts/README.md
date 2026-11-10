# Bag of scripts

Small Lua programs for the Lua Box of Billboard API. Each folder is one program: `main.lua` is the file the box runs.
Drop the file onto the box, or open the box's folder with the Folder button and copy it there.

| Folder | Does | Needs |
| --- | --- | --- |
| `clock_real` | Clock with the real time of the server | Billboard Engine |
| `clock_game` | Clock with the Minecraft time of day | Billboard Engine |
| `billboard_text` | Messages with pixel block, size and speed settings; each one runs off the wall | Billboard Engine |
| `countdown_fireworks` | Countdown from 10, then a redstone dot walks to the other end and sets off a contraption | Billboard Engine, a contraption behind the wall |
| `item_mover` | Moves iron nuggets from one inventory to another | two Inventory Ports with inventories next to them |
| `typewriter_picture` | Has the Typestones Typewriter turn one image into picture stones, with a cost check and progress | Typestones, a Typewriter with an output chest, smooth stone and RGB dye, your image file on the box |
| `typewriter_gallery` | Several images one after the other | as above |
| `typewriter_cost_check` | Prints what pictures of several sizes cost and what the Typewriter holds; changes nothing | Typestones |

The Billboard Engine scripts use the `engine` table of the box; the Engine must touch the box's network and have a
wall marked with the Selector Wand ([Billboard Engine wiki](https://github.com/Knito58/billboard/wiki/Examples)).
The item mover is explained in the [Inventory Port](https://github.com/Knito58/Billboard-API/wiki/Inventory-Port) page.

A ready-made `Bag_of_scripts.zip` is attached to every GitHub release.

Status: the clocks, the text and the countdown are the examples from the Billboard Engine wiki (the engine's own
tests ran them as inline programs). The Typewriter scripts were written against the Typestones source (`getCost`, `drawImage`, `getProgress` and the rest of its Lua functions) and have never run in game. The item mover follows the wiki example, extended with a loop. None of the files
was run in game exactly as it is stored here; if one fails, please report it.
