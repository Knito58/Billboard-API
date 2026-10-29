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

`BillboardApi.CLIENTS` is a Fabric block API lookup. Only block entities can be machines, because the library stores each
machine's name (and who placed it) on its block entity.

## The rules of a method

- Methods run on the server thread inside the box's time slice, so take the order and do the work in your own tick.
- Arguments and results are plain Java values: `null`, `Boolean`, `Number`, `String`, `byte[]`, `List`, `Map`. An
  `Object[]` returns several values (`false, "reason"`). A `CompletableFuture` makes the program wait until it is done.
- Throw `LuaException` for a Lua error. `LuaArgs` has getters (`string`, `integer`, `optNumber`, `list`, ...) that throw the
  right message for a wrong argument.
- The library adds `getName`, `setName` and `getType` to every machine. A machine that defines one of them gets an error
  in the log, and its own method is ignored.

## Which program calls

`args.caller()` is a `LuaCaller`: the same object for one run of one box, `isActive()` false after the program ends, is
stopped or its box is broken, `describe()` for messages, and `file(name)` for the box's files (for a machine that shows
an image the program names). Use it to let one program claim your machine: `claim()` returns `true`, or `false` plus
`caller.describe()` of the holder, and the claim is dropped when `isActive()` turns false.

Only the package `de.knito.billboard.api` is API; everything under `impl` may change in any release.
