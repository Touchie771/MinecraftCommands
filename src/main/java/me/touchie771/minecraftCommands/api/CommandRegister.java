package me.touchie771.minecraftCommands.api;

import me.touchie771.minecraftCommands.api.annotations.Command;
import me.touchie771.minecraftCommands.api.annotations.Execute;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CommandRegister {

    private final List<Class<?>> commandClasses;
    private final CommandMap commandMap;
    private final JavaPlugin plugin;

    private CommandRegister(List<Class<?>> commandClasses, JavaPlugin plugin) {
        this.commandClasses = commandClasses;
        this.commandMap = getCommandMap();
        this.plugin = plugin;
    }

    public void register() {
        for (Class<?> clazz : commandClasses) {
            if (!clazz.isAnnotationPresent(Command.class)) continue;

            Command commandAnnotation = clazz.getAnnotation(Command.class);
            String name = commandAnnotation.name();
            String description = commandAnnotation.description();
            String usage = commandAnnotation.usage();
            List<String> aliases = Arrays.asList(commandAnnotation.aliases());

            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                Method executeMethod = null;

                // Find the method annotated with @Execute
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Execute.class)) {
                        executeMethod = method;
                        break;
                    }
                }

                if (executeMethod != null) {
                    registerCommand(name, description, usage, aliases, instance, executeMethod);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register command " + name + "!");
            }
        }
    }

    private void registerCommand(String name, String description, String usage, List<String> aliases, Object instance, Method method) {
        org.bukkit.command.Command command = new org.bukkit.command.Command(name, description, usage, aliases) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
                try {
                    method.setAccessible(true);
                    // Determine parameters and invoke
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length == 2 && 
                        CommandSender.class.isAssignableFrom(paramTypes[0]) && 
                        String[].class.isAssignableFrom(paramTypes[1])) {
                        method.invoke(instance, sender, args);
                    } else if (paramTypes.length == 1 && CommandSender.class.isAssignableFrom(paramTypes[0])) {
                        method.invoke(instance, sender);
                    } else if (paramTypes.length == 0) {
                        method.invoke(instance);
                    } else {
                        sender.sendMessage("§cError: Invalid command method signature.");
                        return false;
                    }
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to execute command " + name + "!");
                    return false;
                }
            }
        };

        if (commandMap != null) {
            // Register with the fallback prefix
            commandMap.register(name, plugin.getName(), command);
        }
    }

    private CommandMap getCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            Objects.requireNonNull(plugin).getLogger().severe("Failed to get command map!");
            return null;
        }
    }

    public static class RegisterBuilder {
        private final List<Class<?>> classes = new ArrayList<>();
        private JavaPlugin plugin;

        public RegisterBuilder addCommand(Class<?> clazz) {
            this.classes.add(clazz);
            return this;
        }

        public RegisterBuilder setPlugin(JavaPlugin plugin) {
            this.plugin = plugin;
            return this;
        }

        public CommandRegister build() {
            return new CommandRegister(classes, plugin);
        }
    }
}