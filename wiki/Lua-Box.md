A **Lua Box** runs one Lua program. The program is the file `main.lua`; it starts when the box is loaded and stops
when it is unloaded, and it keeps nothing between runs.

## The screen

Right-click the box.

- **Files**: `main.lua` and up to 8 more files. Drop files from your file manager onto the screen.
  In single player the **Folder** button opens the box's folder, and a click on a file opens it in your editor.
- **Output**: what the program prints, and its errors.
- **Restart** and **Stop** for the program. Everybody may press them, unless the server allows only operators.
- **Network** swaps the file list for the machines the box reaches, see [Network and pipes](Network-and-pipes).

Only the owner of a box (the player who placed it) and operators may add or remove files.

## What a program can use

Standard Lua, plus `print`, `sleep`, `require`, `files`, `network`, and the clock functions of `os`.
All of them are in the [Lua reference](Lua-reference).

- Files other than `main.lua` are data (`.txt`, `.json`, `.csv`, images) or modules: `require("name")` runs `name.lua`
  from the box once and gives back what it returned. Programs cannot write files.
- A program that never calls `sleep()` is stopped after 5 seconds; a program that keeps the box busy is stopped as too busy.
  The limits are in [Settings and commands](Settings-and-commands).

## Where the files are

`<world>/billboard_api/boxes/<id>/`, one folder per box. In single player the **Folder** button opens it.

## Worlds from Billboard Engine 1.x

Script Boxes become Lua Boxes (not while Billboard Engine 1.x itself is installed). Each keeps its files, which move to
`billboard_api/boxes/`, and its one program is renamed to `main.lua`. Programs written for the engine still find it as
`engine`.
