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

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final String ARGUMENTS_NODE_NAME = "arguments";

    private VelocityCommandManager<C> manager;
    private ProxyServer proxyServer;
    private CloudBrigadierManager<C, CommandSource> brigadierManager;

    public void initialize(
            final @NonNull VelocityCommandManager<C> velocityCommandManager,
            final @NonNull ProxyServer proxyServer,
            final @NonNull CloudBrigadierManager<C, CommandSource> cloudBrigadierManager
    ) {
        this.manager = velocityCommandManager;
        this.proxyServer = proxyServer;
        this.brigadierManager = cloudBrigadierManager;
    }

    public boolean register(final @NonNull Command<C> command) {
        final CommandComponent<C> component = command.rootComponent();
        final List<Command<C>> rootCommands = this.manager.commands().stream()
                .filter(registered -> registered.rootComponent().name().equals(component.name()))
                .collect(Collectors.toList());
        final Collection<String> aliases = rootCommands.stream()
                .flatMap(registered -> registered.rootComponent().alternativeAliases().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final RawCommand rawCommand = new RawVelocityCommand<>(this.manager, component);
        final CommandMeta.Builder commandMetaBuilder = this.proxyServer.getCommandManager()
                .metaBuilder(component.name())
                .aliases(aliases.toArray(new String[0]));
        this.collectRawHints(component.name(), rootCommands)
                .forEach(commandMetaBuilder::hint);
        final CommandMeta commandMeta = commandMetaBuilder.build();
        this.proxyServer.getCommandManager().unregister(component.name());
        aliases.forEach(this.proxyServer.getCommandManager()::unregister);
        this.proxyServer.getCommandManager().register(commandMeta, rawCommand);
        return true;
    }

    private @NonNull Collection<CommandNode<CommandSource>> collectRawHints(
            final @NonNull String rootName,
            final @NonNull List<Command<C>> commands
    ) {
        final Map<String, CommandNode<CommandSource>> mergedHints = new LinkedHashMap<>();
        for (final Command<C> registered : commands) {
            this.brigadierManager.literalBrigadierNodeFactory()
                    .createNode(
                            rootName,
                            registered,
                            new CloudBrigadierCommand<>(this.manager, this.brigadierManager)
                    )
                    .getChildren()
                    .stream()
                    .filter(child -> !ARGUMENTS_NODE_NAME.equals(child.getName()))
                    .forEach(child -> mergeHintNode(mergedHints, copyForHinting(child)));
        }

        return mergedHints.values();
    }

    private static void mergeHintNode(
            final @NonNull Map<String, CommandNode<CommandSource>> mergedHints,
            final @NonNull CommandNode<CommandSource> incoming
    ) {
        final CommandNode<CommandSource> existing = mergedHints.get(incoming.getName());
        if (existing == null) {
            mergedHints.put(incoming.getName(), incoming);
            return;
        }

        mergeHintChildren(existing, incoming);
    }

    private static void mergeHintChildren(
            final @NonNull CommandNode<CommandSource> target,
            final @NonNull CommandNode<CommandSource> incoming
    ) {
        for (final CommandNode<CommandSource> child : incoming.getChildren()) {
            final CommandNode<CommandSource> existingChild = target.getChild(child.getName());
            if (existingChild == null) {
                target.addChild(copyForHinting(child));
                continue;
            }
            mergeHintChildren(existingChild, child);
        }
    }

    private static <S> @NonNull CommandNode<S> copyForHinting(final @NonNull CommandNode<S> hint) {
        final ArgumentBuilder<S, ?> builder = hint.createBuilder();
        builder.executes(null);
        hint.getChildren().stream()
                .filter(child -> !ARGUMENTS_NODE_NAME.equals(child.getName()))
                .forEach(child -> builder.then(copyForHinting(child)));
        return builder.build();
    }

    private static final class RawVelocityCommand<C> implements RawCommand {

        private final VelocityCommandManager<C> manager;
        private final CommandComponent<C> component;

        private RawVelocityCommand(
                final @NonNull VelocityCommandManager<C> manager,
                final @NonNull CommandComponent<C> component
        ) {
            this.manager = manager;
            this.component = component;
        }

        @Override
        public void execute(final @NonNull Invocation invocation) {
            final C sender = this.manager.senderMapper().map(invocation.source());
            this.manager.commandExecutor().executeCommand(sender, this.executionInput(invocation.arguments()));
        }

        @Override
        public @NonNull List<@NonNull String> suggest(final @NonNull Invocation invocation) {
            if (invocation.arguments().trim().isEmpty()) {
                return Collections.emptyList();
            }

            final C sender = this.manager.senderMapper().map(invocation.source());
            final Suggestions<C, ?> result = this.preferredSuggestions(sender, invocation.arguments());
            return result.list().stream()
                    .map(Suggestion::suggestion)
                    .map(suggestion -> StringUtils.trimBeforeLastSpace(suggestion, result.commandInput()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        private @NonNull Suggestions<C, ?> preferredSuggestions(
                final @NonNull C sender,
                final @NonNull String arguments
        ) {
            final Suggestions<C, ?> base = this.manager.suggestionFactory()
                    .suggestImmediately(sender, this.suggestionInput(arguments));
            if (arguments.trim().isEmpty() || arguments.endsWith(" ")) {
                return base;
            }

            final Suggestions<C, ?> withTrailingSpace = this.manager.suggestionFactory()
                    .suggestImmediately(sender, this.prefixInput(arguments + " "));
            return withTrailingSpace.list().isEmpty() ? base : withTrailingSpace;
        }

        private @NonNull String executionInput(final @NonNull String arguments) {
            if (arguments.isEmpty()) {
                return this.component.name();
            }
            if (this.blank(arguments)) {
                return this.component.name();
            }
            return this.prefixInput(arguments);
        }

        private @NonNull String suggestionInput(final @NonNull String arguments) {
            if (this.blank(arguments)) {
                return this.component.name() + " ";
            }
            return this.prefixInput(arguments);
        }

        private @NonNull String prefixInput(final @NonNull String arguments) {
            if (arguments.startsWith(" ")) {
                return this.component.name() + arguments;
            }
            return this.component.name() + " " + arguments;
        }

        private boolean blank(final @NonNull String arguments) {
            return arguments.trim().isEmpty();
        }
    }
}
