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
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
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
class VelocityRawCommandSuggestionTest {

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
                VelocityCommandRegistrationMode.RAW
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
}
