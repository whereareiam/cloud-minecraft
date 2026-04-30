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

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Collection;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command;
import org.incendo.cloud.brigadier.CloudBrigadierCommand;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.velocity.VelocityCommandManager;

public final class VelocityBrigadierRegistrationHandler<C> {

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
     * Register the command using Velocity's Brigadier integration.
     *
     * @param command command to register
     * @return {@code true}
     */
    public boolean register(final @NonNull Command<C> command) {
        final CommandComponent<C> component = command.rootComponent();
        final Collection<String> aliases = component.alternativeAliases();
        final BrigadierCommand brigadierCommand = new BrigadierCommand(
                this.brigadierManager.literalBrigadierNodeFactory().createNode(
                        command.rootComponent().name(),
                        command,
                        new CloudBrigadierCommand<>(this.manager, this.brigadierManager)
                )
        );
        final CommandMeta commandMeta = this.proxyServer.getCommandManager()
                .metaBuilder(brigadierCommand)
                .aliases(aliases.toArray(new String[0])).build();
        aliases.forEach(this.proxyServer.getCommandManager()::unregister);
        this.proxyServer.getCommandManager().register(commandMeta, brigadierCommand);
        return true;
    }
}
