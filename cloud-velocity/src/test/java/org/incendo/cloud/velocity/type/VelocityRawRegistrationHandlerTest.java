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
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
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
    @DisplayName("Raw mode registers a merged Brigadier command")
    void rawModeRegistersMergedBrigadierCommand() {
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder(any(BrigadierCommand.class))).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);
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
        manager.command(manager.commandBuilder("premium").literal("confirm"));
        manager.command(manager.commandBuilder("premium").literal("cancel"));
        manager.command(manager.commandBuilder("premium"));

        assertThat(commandCaptor.getValue()).isInstanceOf(BrigadierCommand.class);
        final LiteralCommandNode<CommandSource> root = ((BrigadierCommand) commandCaptor.getValue()).getNode();
        assertThat(root.getChildren().stream().map(CommandNode::getName))
                .containsAtLeast("confirm", "cancel");
        assertThat(root.getChild("confirm").getCommand()).isNotNull();
        assertThat(root.getChild("cancel").getCommand()).isNotNull();
    }

    @Test
    @DisplayName("Raw mode keeps argument nodes for literal subcommands")
    void rawModeKeepsArgumentNodes() {
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder(any(BrigadierCommand.class))).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);
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
        manager.command(manager.commandBuilder("identica").literal("admin"));
        manager.command(manager.commandBuilder("identica")
                .literal("enroll")
                .required("eligibility", StringParser.stringParser(),
                        SuggestionProvider.suggestingStrings("premium", "cracked")));
        manager.command(manager.commandBuilder("identica"));

        final LiteralCommandNode<CommandSource> root = ((BrigadierCommand) commandCaptor.getValue()).getNode();
        final CommandNode<CommandSource> enroll = root.getChild("enroll");

        assertThat(enroll).isNotNull();
        assertThat(enroll.getChild("arguments")).isNotNull();
    }

    @Test
    @DisplayName("Raw mode keeps literal branches and adds a single raw arguments branch")
    void rawModeKeepsLiteralBranches() {
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder(any(BrigadierCommand.class))).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);
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
                .literal("admin")
                .literal("clear")
                .literal("confirm"));
        manager.command(manager.commandBuilder("identica")
                .literal("admin")
                .literal("clear")
                .required("target", StringParser.stringParser()));

        final LiteralCommandNode<CommandSource> root = ((BrigadierCommand) commandCaptor.getValue()).getNode();
        final CommandNode<CommandSource> clear = root.getChild("admin").getChild("clear");

        assertThat(clear.getChildren().stream().map(CommandNode::getName).collect(Collectors.toList()))
                .containsAtLeast("confirm", "arguments");
    }

    @Test
    @DisplayName("Raw mode preserves permission predicates on literal branches")
    void rawModePreservesPermissions() {
        when(this.proxyServer.getCommandManager()).thenReturn(this.velocityCommandManager);
        when(this.proxyServer.getEventManager()).thenReturn(this.eventManager);
        lenient().when(this.pluginContainer.getExecutorService()).thenReturn(this.executorService);
        when(this.velocityCommandManager.metaBuilder(any(BrigadierCommand.class))).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.aliases(any())).thenReturn(this.commandMetaBuilder);
        when(this.commandMetaBuilder.build()).thenReturn(this.commandMeta);
        when(this.commandSource.hasPermission("identica.admin")).thenReturn(false);

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
                .literal("cracked")
                .permission("identica.admin"));

        final LiteralCommandNode<CommandSource> root = ((BrigadierCommand) commandCaptor.getValue()).getNode();
        assertThat(root.getChild("cracked").canUse(this.commandSource)).isFalse();
    }
}
