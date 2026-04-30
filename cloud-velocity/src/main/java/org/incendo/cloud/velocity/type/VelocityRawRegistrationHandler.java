//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package org.incendo.cloud.velocity.type;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command;
import org.incendo.cloud.brigadier.CloudBrigadierCommand;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.Suggestions;
import org.incendo.cloud.util.StringUtils;
import org.incendo.cloud.velocity.VelocityCommandManager;

public final class VelocityRawRegistrationHandler<C> {
    private static final String RAW_ARGUMENTS_NODE_NAME = "arguments";

    private VelocityCommandManager<C> manager;
    private ProxyServer proxyServer;
    private CloudBrigadierManager<C, CommandSource> brigadierManager;

    /**
     * Initialize the registration handler.
     *
     * @param velocityCommandManager command manager
     * @param proxyServer proxy server
     * @param cloudBrigadierManager brigadier manager
     */
    public void initialize(
            final @NonNull VelocityCommandManager<C> velocityCommandManager,
            final @NonNull ProxyServer proxyServer,
            final @NonNull CloudBrigadierManager<C, CommandSource> cloudBrigadierManager
    ) {
        this.manager = velocityCommandManager;
        this.proxyServer = proxyServer;
        this.brigadierManager = cloudBrigadierManager;
    }

    /**
     * Register a merged same-root command tree that validates through Brigadier while
     * still delegating execution and suggestions through the raw Cloud path.
     *
     * @param command command to register
     * @return {@code true}
     */
    public boolean register(final @NonNull Command<C> command) {
        final CommandComponent<C> component = command.rootComponent();
        final List<Command<C>> rootCommands = this.manager.commands().stream()
                .filter(registered -> registered.rootComponent().name().equals(component.name()))
                .collect(Collectors.toList());
        if (rootCommands.stream().noneMatch(registered -> registered == command)) {
            rootCommands.add(command);
        }

        final Collection<String> aliases = rootCommands.stream()
                .flatMap(registered -> registered.rootComponent().alternativeAliases().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final LiteralCommandNode<CommandSource> mergedRoot = this.mergeRoot(component.name(), rootCommands);
        final BrigadierCommand brigadierCommand = new BrigadierCommand(this.createRawShell(mergedRoot));
        final CommandMeta commandMeta = this.proxyServer.getCommandManager()
                .metaBuilder(brigadierCommand)
                .aliases(aliases.toArray(new String[0]))
                .build();
        this.proxyServer.getCommandManager().unregister(component.name());
        aliases.forEach(this.proxyServer.getCommandManager()::unregister);
        this.proxyServer.getCommandManager().register(commandMeta, brigadierCommand);
        return true;
    }

    private @NonNull LiteralCommandNode<CommandSource> mergeRoot(
            final @NonNull String rootName,
            final @NonNull List<org.incendo.cloud.Command<C>> commands
    ) {
        LiteralCommandNode<CommandSource> mergedRoot = null;
        for (final org.incendo.cloud.Command<C> registered : commands) {
            final LiteralCommandNode<CommandSource> rawRoot = this.brigadierManager.literalBrigadierNodeFactory()
                    .createNode(
                            rootName,
                            registered,
                            new CloudBrigadierCommand<>(this.manager, this.brigadierManager)
                    );
            if (mergedRoot == null) {
                mergedRoot = rawRoot;
                continue;
            }
            for (final CommandNode<CommandSource> child : rawRoot.getChildren()) {
                mergedRoot.addChild(child);
            }
        }
        if (mergedRoot == null) {
            throw new IllegalStateException("No commands registered for root " + rootName);
        }
        return mergedRoot;
    }

    private @NonNull LiteralCommandNode<CommandSource> createRawShell(final @NonNull LiteralCommandNode<CommandSource> mergedRoot) {
        final LiteralArgumentBuilder<CommandSource> builder = mergedRoot.createBuilder();
        builder.executes(this.rawExecutor());
        this.copyLiteralChildren(builder, mergedRoot);
        return builder.build();
    }

    private void copyLiteralChildren(
            final @NonNull LiteralArgumentBuilder<CommandSource> parentBuilder,
            final @NonNull CommandNode<CommandSource> source
    ) {
        boolean addedArguments = false;
        for (final CommandNode<CommandSource> child : source.getChildren()) {
            if (child instanceof LiteralCommandNode) {
                parentBuilder.then(this.copyLiteralNode((LiteralCommandNode<CommandSource>) child));
                continue;
            }
            if (!addedArguments) {
                parentBuilder.then(this.rawArgumentsNode(child));
                addedArguments = true;
            }
        }
    }

    private @NonNull LiteralArgumentBuilder<CommandSource> copyLiteralNode(final @NonNull LiteralCommandNode<CommandSource> source) {
        final LiteralArgumentBuilder<CommandSource> builder = source.createBuilder();
        if (source.getCommand() != null || source.getChildren().stream().anyMatch(child -> !(child instanceof LiteralCommandNode))) {
            builder.executes(this.rawExecutor());
        }
        this.copyLiteralChildren(builder, source);
        return builder;
    }

    private @NonNull RequiredArgumentBuilder<CommandSource, String> rawArgumentsNode(final @NonNull CommandNode<CommandSource> source) {
        return BrigadierCommand.requiredArgumentBuilder(RAW_ARGUMENTS_NODE_NAME, StringArgumentType.greedyString())
                .requires(source.getRequirement())
                .suggests((context, builder) -> {
                    final C sender = this.manager.senderMapper().map(context.getSource());
                    for (final String suggestion : this.rawSuggestions(sender, context.getInput())) {
                        builder.suggest(suggestion);
                    }
                    return builder.buildFuture();
                })
                .executes(this.rawExecutor());
    }

    private com.mojang.brigadier.@NonNull Command<CommandSource> rawExecutor() {
        return context -> {
            final C sender = this.manager.senderMapper().map(context.getSource());
            this.manager.commandExecutor().executeCommand(sender, context.getInput());
            return 1;
        };
    }

    private @NonNull List<String> rawSuggestions(final @NonNull C sender, final @NonNull String input) {
        final Suggestions<C, ?> base = this.manager.suggestionFactory().suggestImmediately(sender, input);
        final Suggestions<C, ?> result;
        if (input.trim().isEmpty() || input.endsWith(" ")) {
            result = base;
        } else {
            final Suggestions<C, ?> withTrailingSpace = this.manager.suggestionFactory()
                    .suggestImmediately(sender, input + " ");
            result = withTrailingSpace.list().isEmpty() ? base : withTrailingSpace;
        }
        return result.list().stream()
                .map(Suggestion::suggestion)
                .map(suggestion -> StringUtils.trimBeforeLastSpace(suggestion, result.commandInput()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
