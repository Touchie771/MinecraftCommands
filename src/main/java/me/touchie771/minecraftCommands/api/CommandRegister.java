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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                Method defaultExecuteMethod = null;
                Map<String, Method> subCommands = new HashMap<>();
                Method tabCompleteMethod = null;

                // Find methods annotated with @Execute and @TabComplete
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Execute.class)) {
                        Execute execute = method.getAnnotation(Execute.class);
                        if (execute.name().isEmpty()) {
                            defaultExecuteMethod = method;
                        } else {
                            subCommands.put(execute.name().toLowerCase(), method);
                        }
                    }
                    if (method.isAnnotationPresent(TabComplete.class)) {
                        tabCompleteMethod = method;
                    }
                }

                if (defaultExecuteMethod != null || !subCommands.isEmpty()) {
                    registerCommand(name, description, usage, aliases, instance, defaultExecuteMethod, subCommands, tabCompleteMethod, clazz);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register command " + name + "!");
            }
        }
    }

    private void registerCommand(String name, String description, String usage, List<String> aliases, Object instance, Method defaultExecuteMethod, Map<String, Method> subCommands, Method tabCompleteMethod, Class<?> clazz) {
        org.bukkit.command.Command command = new org.bukkit.command.Command(name, description, usage, aliases) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
                try {
                    // Check main command permission first
                    if (!checkPermission(sender, clazz)) {
                        return true;
                    }

                    Method methodToExecute = defaultExecuteMethod;
                    String[] argsToPass = args;

                    // Check for subcommands
                    if (args.length > 0 && subCommands.containsKey(args[0].toLowerCase())) {
                        methodToExecute = subCommands.get(args[0].toLowerCase());
                        // Shift arguments for subcommand
                        argsToPass = Arrays.copyOfRange(args, 1, args.length);
                    }

                    if (methodToExecute == null) {
                        // No default executor and no matching subcommand
                        return false;
                    }

                    // Check method-specific permission
                    if (!checkPermission(sender, methodToExecute)) {
                        return true;
                    }

                    methodToExecute.setAccessible(true);
                    Class<?>[] paramTypes = methodToExecute.getParameterTypes();

                    // Case: (SenderType, String[])
                    if (paramTypes.length == 2 && 
                        CommandSender.class.isAssignableFrom(paramTypes[0]) && 
                        String[].class.isAssignableFrom(paramTypes[1])) {
                        
                        if (!paramTypes[0].isInstance(sender)) {
                            sender.sendMessage("§cThis command cannot be executed by " + sender.getName() + "!");
                            return true;
                        }
                        methodToExecute.invoke(instance, sender, argsToPass);
                        return true;
                    } 
                    // Case: (SenderType)
                    else if (paramTypes.length == 1 && CommandSender.class.isAssignableFrom(paramTypes[0])) {
                        if (!paramTypes[0].isInstance(sender)) {
                            sender.sendMessage("§cThis command cannot be executed by " + sender.getName() + "!");
                            return true;
                        }
                        methodToExecute.invoke(instance, sender);
                        return true;
                    } 
                    // Case: ()
                    else if (paramTypes.length == 0) {
                        methodToExecute.invoke(instance);
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
                } else if (args.length == 1) {
                    // Default tab completion for subcommands
                    List<String> completions = new ArrayList<>();
                    String currentArg = args[0].toLowerCase();
                    for (String subCommand : subCommands.keySet()) {
                        if (subCommand.startsWith(currentArg)) {
                            Method method = subCommands.get(subCommand);
                            // Check permission for subcommand before showing in tab complete
                            if (checkPermission(sender, method, true)) {
                                completions.add(subCommand);
                            }
                        }
                    }
                    return completions;
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
        return checkPermission(sender, element, false);
    }

    private boolean checkPermission(CommandSender sender, java.lang.reflect.AnnotatedElement element, boolean silent) {
        if (element.isAnnotationPresent(Permission.class)) {
            Permission permission = element.getAnnotation(Permission.class);
            if (!sender.hasPermission(permission.value())) {
                if (!silent) {
                    sender.sendMessage(permission.message());
                }
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