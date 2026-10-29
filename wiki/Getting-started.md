## Install

1. Install Fabric Loader 0.19.5 or newer for Minecraft 26.3, and [Fabric API](https://modrinth.com/mod/fabric-api). Java 25 is needed.
2. Put the Billboard API jar into the `mods` folder.
3. On a server, install it on the server and on every client.

## Recipes

| Block | Recipe | Gives |
| --- | --- | --- |
| **Lua Box** | 4 iron ingots in the corners, 4 redstone in the sides, 1 paper in the middle | 1 |
| **Data Pipe** | planks, paper, planks in a row | 8 |
| **Redstone Port** | 1 Data Pipe and 1 redstone dust, in any order | 1 |

All three break fastest with an axe.

## A first program

1. Place a Lua Box, and a sign right next to it.
2. Write a file called `main.lua` in any text editor:

   ```lua
   local sign = network.find("sign")[1]
   sign.setLine(1, "Hello")
   while true do
     sign.setLine(2, textutils.formatTime(os.time("ingame"), true))
     sleep(1)
   end
   ```

3. Right-click the box and drag `main.lua` from your file manager onto its screen. The program starts at once.

The sign shows the time of the game. To change the program, change the file, drop it again and press **Restart**.
More in [Lua Box](Lua-Box).
