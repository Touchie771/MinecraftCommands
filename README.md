# MinecraftCommands

A lightweight, annotation-based command framework for Spigot/Bukkit plugins.

## Features
- **Annotation-driven**: Define commands and execution logic with `@Command` and `@Execute`.
- **Flexible Parameters**: Supports various method signatures for command execution.
- **Automatic Registration**: Easily register all command classes.
- **Permission Handling**: Use `@Permission` to enforce command permissions.
- **Tab Completion**: Use `@TabComplete` to provide tab completions.
- **Type Safety**: Automatically checks if the sender (e.g., Player vs Console) matches the method signature.

## Usage

### 1. Create a Command Class
Annotate your class with `@Command` and your method with `@Execute`.

```java
@Command(
    name = "heal",
    description = "Heals the player",
    usage = "/heal",
    aliases = {"h", "health"}
)
@Permission("myplugin.heal")
public class HealCommand {

    @Execute
    public void onCommand(Player player, String[] args) {
        player.setHealth(20);
        player.sendMessage("§aYou have been healed!");
    }
}
```

### 2. Register Commands
In your main plugin class (e.g., `onEnable`), use the `CommandRegister` builder.

```java
@Override
public void onEnable() {
    new CommandRegister.RegisterBuilder()
        .setPlugin(this)
        .addCommand(HealCommand.class)
        .build()
        .register();
}
```

## Annotations

### `@Command` (Type)
- `name`: The name of the command (required).
- `description`: Description of the command.
- `usage`: Usage message.
- `aliases`: Array of aliases.

### `@Execute` (Method)
Marks the method to be invoked when the command is run. Supported signatures:
- `void method(SenderType sender, String[] args)`
- `void method(SenderType sender)`
- `void method()`

Where `SenderType` can be `CommandSender`, `Player`, `ConsoleCommandSender`, etc.

### `@Permission` (Type or Method)
- `value`: The permission node string.
- `message`: Custom no-permission message (optional).

## Installation
Copy the `me.touchie771.minecraftCommands.api` package into your project.