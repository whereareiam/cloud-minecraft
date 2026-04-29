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
package org.incendo.cloud.velocity;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command;
import org.incendo.cloud.brigadier.CloudBrigadierCommand;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.Suggestions;
import org.incendo.cloud.util.StringUtils;

final class VelocityPluginRegistrationHandler<C> implements CommandRegistrationHandler<C> {

    private CloudBrigadierManager<C, CommandSource> brigadierManager;
    private VelocityCommandManager<C> manager;

    void initialize(final @NonNull VelocityCommandManager<C> velocityCommandManager) {
        this.manager = velocityCommandManager;
        this.brigadierManager = new CloudBrigadierManager<>(
                velocityCommandManager,
                velocityCommandManager.senderMapper()
        );
    }

    @Override
    public boolean registerCommand(final @NonNull Command<C> command) {
        if (this.manager.registrationMode() == VelocityCommandRegistrationMode.RAW) {
            return this.registerRawCommand(command);
        }
        return this.registerBrigadierCommand(command);
    }

    private boolean registerBrigadierCommand(final @NonNull Command<C> command) {
        final CommandComponent<C> component = command.rootComponent();
        final Collection<String> aliases = component.alternativeAliases();
        final BrigadierCommand brigadierCommand = new BrigadierCommand(
                this.brigadierManager.literalBrigadierNodeFactory().createNode(
                        command.rootComponent().name(),
                        command,
                        new CloudBrigadierCommand<>(this.manager, this.brigadierManager)
                )
        );
        final CommandMeta commandMeta = this.manager.proxyServer().getCommandManager()
                .metaBuilder(brigadierCommand)
                .aliases(aliases.toArray(new String[0])).build();
        aliases.forEach(this.manager.proxyServer().getCommandManager()::unregister);
        this.manager.proxyServer().getCommandManager().register(commandMeta, brigadierCommand);
        return true;
    }

    private boolean registerRawCommand(final @NonNull Command<C> command) {
        final CommandComponent<C> component = command.rootComponent();
        final Collection<String> aliases = component.alternativeAliases();
        final RawCommand rawCommand = new RawVelocityCommand<>(this.manager, component);
        final CommandMeta.Builder commandMetaBuilder = this.manager.proxyServer().getCommandManager()
                .metaBuilder(component.name())
                .aliases(aliases.toArray(new String[0]));
        this.brigadierManager.literalBrigadierNodeFactory()
                .createNode(
                        component.name(),
                        command,
                        new CloudBrigadierCommand<>(this.manager, this.brigadierManager)
                )
                .getChildren()
                .forEach(child -> commandMetaBuilder.hint(copyForHinting(child)));
        final CommandMeta commandMeta = commandMetaBuilder.build();
        this.manager.proxyServer().getCommandManager().unregister(component.name());
        aliases.forEach(this.manager.proxyServer().getCommandManager()::unregister);
        this.manager.proxyServer().getCommandManager().register(commandMeta, rawCommand);
        return true;
    }

    @NonNull CloudBrigadierManager<C, CommandSource> brigadierManager() {
        return this.brigadierManager;
    }

    private static <S> @NonNull CommandNode<S> copyForHinting(final @NonNull CommandNode<S> hint) {
        final ArgumentBuilder<S, ?> builder = hint.createBuilder();
        builder.executes(null);
        hint.getChildren().forEach(child -> builder.then(copyForHinting(child)));
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
            final C sender = this.manager.senderMapper().map(invocation.source());
            final Suggestions<C, ?> result = this.manager.suggestionFactory()
                    .suggestImmediately(sender, this.suggestionInput(invocation.arguments()));
            return result.list().stream()
                    .map(Suggestion::suggestion)
                    .map(suggestion -> StringUtils.trimBeforeLastSpace(suggestion, result.commandInput()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
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
