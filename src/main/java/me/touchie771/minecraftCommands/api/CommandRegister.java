package me.touchie771.minecraftCommands.api;

import me.touchie771.minecraftCommands.api.annotations.Command;
import me.touchie771.minecraftCommands.api.annotations.Execute;
import me.touchie771.minecraftCommands.api.annotations.Permission;
import me.touchie771.minecraftCommands.api.annotations.TabComplete;
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
                Method tabCompleteMethod = null;

                // Find methods annotated with @Execute and @TabComplete
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Execute.class)) {
                        executeMethod = method;
                    }
                    if (method.isAnnotationPresent(TabComplete.class)) {
                        tabCompleteMethod = method;
                    }
                }

                if (executeMethod != null) {
                    registerCommand(name, description, usage, aliases, instance, executeMethod, tabCompleteMethod, clazz);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register command " + name + "!");
            }
        }
    }

    private void registerCommand(String name, String description, String usage, List<String> aliases, Object instance, Method executeMethod, Method tabCompleteMethod, Class<?> clazz) {
        org.bukkit.command.Command command = new org.bukkit.command.Command(name, description, usage, aliases) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
                try {
                    // Permission check
                    if (!checkPermission(sender, clazz) || !checkPermission(sender, executeMethod)) {
                        return true;
                    }

                    executeMethod.setAccessible(true);
                    Class<?>[] paramTypes = executeMethod.getParameterTypes();

                    // Case: (SenderType, String[])
                    if (paramTypes.length == 2 && 
                        CommandSender.class.isAssignableFrom(paramTypes[0]) && 
                        String[].class.isAssignableFrom(paramTypes[1])) {
                        
                        if (!paramTypes[0].isInstance(sender)) {
                            sender.sendMessage("§cThis command cannot be executed by " + sender.getName() + "!");
                            return true;
                        }
                        executeMethod.invoke(instance, sender, args);
                        return true;
                    } 
                    // Case: (SenderType)
                    else if (paramTypes.length == 1 && CommandSender.class.isAssignableFrom(paramTypes[0])) {
                        if (!paramTypes[0].isInstance(sender)) {
                            sender.sendMessage("§cThis command cannot be executed by " + sender.getName() + "!");
                            return true;
                        }
                        executeMethod.invoke(instance, sender);
                        return true;
                    } 
                    // Case: ()
                    else if (paramTypes.length == 0) {
                        executeMethod.invoke(instance);
                        return true;
                    } else {
                        sender.sendMessage("§cError: Invalid command method signature.");
                        return false;
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to execute command " + name + "!");
                    plugin.getLogger().severe(e.getMessage());
                    return false;
                }
            }

            @NotNull
            @Override
            public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
                if (tabCompleteMethod != null) {
                    try {
                        tabCompleteMethod.setAccessible(true);
                        Class<?>[] paramTypes = tabCompleteMethod.getParameterTypes();

                        if (paramTypes.length == 2 && 
                            CommandSender.class.isAssignableFrom(paramTypes[0]) && 
                            String[].class.isAssignableFrom(paramTypes[1]) &&
                            List.class.isAssignableFrom(tabCompleteMethod.getReturnType())) {
                            
                            // If specific sender type is required but not provided, return empty list or handle gracefully
                            if (!paramTypes[0].isInstance(sender)) {
                                return new ArrayList<>();
                            }

                            @SuppressWarnings("unchecked")
                            List<String> completions = (List<String>) tabCompleteMethod.invoke(instance, sender, args);
                            return completions;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to tab complete command " + name + "!");
                        plugin.getLogger().warning(e.getMessage());
                    }
                }
                return super.tabComplete(sender, alias, args);
            }
        };

        if (commandMap != null) {
            // Register with the fallback prefix
            commandMap.register(name, plugin.getName(), command);
        }
    }
    
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkPermission(CommandSender sender, java.lang.reflect.AnnotatedElement element) {
        if (element.isAnnotationPresent(Permission.class)) {
            Permission permission = element.getAnnotation(Permission.class);
            if (!sender.hasPermission(permission.value())) {
                sender.sendMessage(permission.message());
                return false;
            }
        }
        return true;
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