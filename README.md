# MinecraftCommands

A lightweight, annotation-based command framework for Spigot/Bukkit plugins.

## Features
- **Annotation-driven**: Define commands and execution logic with `@Command` and `@Execute`.
- **Flexible Parameters**: Supports various method signatures for command execution.
- **Automatic Registration**: Easily register all command classes.
- **Permission Handling**: Use `@Permission` to enforce command permissions.
- **Subcommands**: Easily handle subcommands by specifying a name in `@Execute`.
- **Tab Completion**: Use `@TabComplete` to provide tab completions, including support for subcommands.
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

### 3. Subcommands
You can define subcommands by adding a `name` to the `@Execute` annotation.

```java
@Command(name = "clan")
public class ClanCommand {

    @Execute // Default executor: /clan
    public void info(Player player) {
        player.sendMessage("Clan Info...");
    }

    @Execute(name = "create") // Subcommand: /clan create <name>
    public void create(Player player, String[] args) {
        if (args.length > 0) {
            player.sendMessage("Created clan: " + args[0]);
        }
    }
    
    @TabComplete(name = "create")
    public List<String> createTabComplete(CommandSender sender, String[] args) {
        return List.of("Name1", "Name2");
    }
}
```

## Annotations

### `@Command` (Type)
- `name`: The name of the command (required).
- `description`: Description of the command.
- `usage`: Usage message.
- `aliases`: Array of aliases.

### `@Execute` (Method)
Marks the method to be invoked when the command is run.
- `name`: The name of the subcommand (optional). If empty, it serves as the default executor.

Supported signatures:
- `void method(SenderType sender, String[] args)`
- `void method(SenderType sender)`
- `void method()`

Where `SenderType` can be `CommandSender`, `Player`, `ConsoleCommandSender`, etc.

### `@TabComplete` (Method)
Marks the method used for tab completion.
- `name`: The name of the subcommand to provide completions for (optional).

### `@Permission` (Type or Method)
- `value`: The permission node string.
- `message`: Custom no-permission message (optional).

## Installation

### GitHub Packages
The library is published to GitHub Packages. Add the repository to your `build.gradle`:

```gradle
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Touchie771/MinecraftCommands")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    implementation 'me.touchie771:minecraftcommands:1.0.1'
}
```

### Manual Installation
Alternatively, you can copy the `me.touchie771.minecraftCommands.api` package into your project.

## About
This library was originally created for personal use, but anyone is welcome to use it in their projects.