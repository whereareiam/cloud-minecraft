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

import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityRawRegistrationHandlerTest {

    @Mock
    private ProxyServer proxyServer;
    @Mock
    private com.velocitypowered.api.command.CommandManager velocityCommandManager;
    @Mock
    private EventManager eventManager;
    @Mock
    private PluginContainer pluginContainer;
    @Mock
    private CommandMeta.Builder commandMetaBuilder;
    @Mock
    private CommandMeta commandMeta;
    @Mock
    private CommandSource commandSource;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        this.executorService.shutdownNow();
    }

    @Test
    void rawSuggestionsIncludeLiteralsWhenArgumentsAreEmpty() {
        // Arrange
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder("test")).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);
        final List<CommandNode<CommandSource>> hints = new ArrayList<>();
        lenient().doAnswer(invocationOnMock -> {
            hints.add(invocationOnMock.getArgument(0));
            return this.commandMetaBuilder;
        }).when(this.commandMetaBuilder).hint(any());
        when(this.commandMetaBuilder.build()).thenReturn(this.commandMeta);
        when(this.commandMeta.getHints()).thenReturn(hints);
        final ArgumentCaptor<CommandMeta> metaCaptor = ArgumentCaptor.forClass(CommandMeta.class);
        final ArgumentCaptor<com.velocitypowered.api.command.Command> commandCaptor =
                ArgumentCaptor.forClass(com.velocitypowered.api.command.Command.class);
        doNothing().when(this.velocityCommandManager).register(metaCaptor.capture(), commandCaptor.capture());

        final VelocityCommandManager<CommandSource> manager = new VelocityCommandManager<>(
                this.pluginContainer,
                this.proxyServer,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity(),
                VelocityCommandManager.RegistrationMode.RAW
        );
        manager.command(manager.commandBuilder("test").literal("status"));
        manager.command(manager.commandBuilder("test").literal("enroll"));
        manager.command(manager.commandBuilder("test").literal("confirm"));

        final RawCommand rawCommand = (RawCommand) commandCaptor.getValue();
        final RawCommand.Invocation invocation = mock(RawCommand.Invocation.class);
        when(invocation.source()).thenReturn(this.commandSource);

        when(invocation.arguments()).thenReturn("");
        final List<String> emptySuggestions = rawCommand.suggest(invocation);

        when(invocation.arguments()).thenReturn(" ");
        final List<String> whitespaceSuggestions = rawCommand.suggest(invocation);

        // Assert
        assertThat(metaCaptor.getValue().getHints()).isNotEmpty();
        assertThat(emptySuggestions).containsExactly("status", "enroll", "confirm");
        assertThat(whitespaceSuggestions).containsExactly("status", "enroll", "confirm");
    }

    @Test
    @DisplayName("Raw command metadata keeps subcommand hints even when the bare root is registered last")
    void rawRegistrationAggregatesHintsAcrossSameRoot() {
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder("premium")).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);

        final List<CommandNode<CommandSource>> hints = new ArrayList<>();
        lenient().doAnswer(invocationOnMock -> {
            hints.add(invocationOnMock.getArgument(0));
            return this.commandMetaBuilder;
        }).when(this.commandMetaBuilder).hint(any());
        when(this.commandMetaBuilder.build()).thenReturn(this.commandMeta);
        when(this.commandMeta.getHints()).thenReturn(hints);

        final AtomicReference<CommandMeta> lastMeta = new AtomicReference<>();
        lenient().doAnswer(invocationOnMock -> {
            lastMeta.set(invocationOnMock.getArgument(0));
            return null;
        }).when(this.velocityCommandManager).register(any(CommandMeta.class), any(com.velocitypowered.api.command.Command.class));

        final VelocityCommandManager<CommandSource> manager = new VelocityCommandManager<>(
                this.pluginContainer,
                this.proxyServer,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity(),
                VelocityCommandManager.RegistrationMode.RAW
        );
        manager.command(manager.commandBuilder("premium").literal("confirm"));
        manager.command(manager.commandBuilder("premium").literal("cancel"));
        manager.command(manager.commandBuilder("premium"));

        assertThat(lastMeta.get()).isNotNull();
        assertThat(lastMeta.get().getHints().stream().map(CommandNode::getName))
                .containsAtLeast("confirm", "cancel");
    }

    @Test
    @DisplayName("Raw suggestions prefer next-argument completions when the current token is already complete")
    void rawSuggestionsPreferTrailingArgumentSuggestions() {
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder("identica")).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);
        lenient().when(this.commandMetaBuilder.hint(any())).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.build()).thenReturn(this.commandMeta);

        final ArgumentCaptor<com.velocitypowered.api.command.Command> commandCaptor =
                ArgumentCaptor.forClass(com.velocitypowered.api.command.Command.class);
        doNothing().when(this.velocityCommandManager).register(any(CommandMeta.class), commandCaptor.capture());

        final VelocityCommandManager<CommandSource> manager = new VelocityCommandManager<>(
                this.pluginContainer,
                this.proxyServer,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity(),
                VelocityCommandManager.RegistrationMode.RAW
        );
        manager.command(manager.commandBuilder("identica")
                .literal("admin"));
        manager.command(manager.commandBuilder("identica")
                .literal("enroll")
                .required("eligibility", StringParser.stringParser(),
                        SuggestionProvider.suggestingStrings("premium", "cracked")));

        final RawCommand rawCommand = (RawCommand) commandCaptor.getValue();
        final RawCommand.Invocation invocation = mock(RawCommand.Invocation.class);
        when(invocation.source()).thenReturn(this.commandSource);
        when(invocation.arguments()).thenReturn("enroll");

        final List<String> suggestions = rawCommand.suggest(invocation);

        assertThat(suggestions).containsExactly("premium", "cracked");
    }
}
