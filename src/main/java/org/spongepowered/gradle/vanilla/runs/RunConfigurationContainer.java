/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.gradle.vanilla.runs;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.spongepowered.gradle.vanilla.Constants;
import org.spongepowered.gradle.vanilla.MinecraftExtension;
import org.spongepowered.gradle.vanilla.model.Version;
import org.spongepowered.gradle.vanilla.model.rule.RuleContext;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class RunConfigurationContainer implements NamedDomainObjectContainer<RunConfiguration> {
    private final NamedDomainObjectContainer<RunConfiguration> delegate;
    private final MinecraftExtension extension;

    @Inject
    public RunConfigurationContainer(
            final NamedDomainObjectContainer<RunConfiguration> delegate, final MinecraftExtension extension) {
        this.delegate = delegate;
        this.extension = extension;
    }

    /**
     * Create a run configuration pre-configured with standard settings to start the Minecraft client.
     *
     * Will return a reference if the client configuration already exists.
     *
     * @return a provider for the configuration
     */
    public NamedDomainObjectProvider<RunConfiguration> client() {
        return this.client(null);
    }

    /**
     * Create a run configuration pre-configured with standard settings to start the Minecraft client.
     *
     * Will return a reference if the client configuration already exists, while applying the configuration action.
     *
     * @return a provider for the configuration
     */
    public NamedDomainObjectProvider<RunConfiguration> client(final @Nullable Action<RunConfiguration> configureAction) {
        final NamedDomainObjectProvider<RunConfiguration> config;
        if (this.getNames().contains(Constants.RunConfiguration.CLIENT_CONFIG)) {
            config = this.named(Constants.RunConfiguration.CLIENT_CONFIG);
        } else {
            config = this.register(Constants.RunConfiguration.CLIENT_CONFIG, this.configureClientRun());
        }
        if (configureAction != null) {
            config.configure(configureAction);
        }
        return config;
    }

    private Action<RunConfiguration> configureClientRun() {
        return config -> {
            config.mainClass().set(this.extension.targetVersion().map(Version::mainClass));
            config.requiresAssetsAndNatives().set(true);
            final MapProperty<String, String> launcherTokens = config.launcherMetaTokens();
            launcherTokens.put(Constants.LauncherEnvironmentTokens.VERSION_NAME, this.extension.targetVersion().map(Version::id));
            launcherTokens.put(Constants.LauncherEnvironmentTokens.ASSETS_INDEX_NAME, this.extension.targetVersion().map(Version::assets));
            launcherTokens.put(Constants.LauncherEnvironmentTokens.AUTH_ACCESS_TOKEN, "0");
            launcherTokens.put(Constants.LauncherEnvironmentTokens.GAME_DIRECTORY, config.workingDirectory().map(x -> x.getAsFile().getAbsolutePath()));
            launcherTokens.put(Constants.LauncherEnvironmentTokens.USER_TYPE, "legacy"); // or mojang
            launcherTokens.put(Constants.LauncherEnvironmentTokens.VERSION_TYPE,
                    this.extension.targetVersion().map(v -> v.type().name().toLowerCase(Locale.ROOT)));

            final RuleContext context = RuleContext.create();
            config.allArguments().add(new ManifestDerivedArgumentProvider(
                    launcherTokens,
                    this.extension.targetVersion().map(v -> v.arguments().game()),
                    context
            ));
            config.allJvmArguments().add(new ManifestDerivedArgumentProvider(
                    launcherTokens,
                    this.extension.targetVersion().map(v -> v.arguments().jvm()),
                    context
            ));
        };
    }

    /**
     * Create a run configuration pre-configured with standard settings to start the Minecraft server.
     *
     * Will return a reference if the server configuration already exists.
     *
     * @return a provider for the configuration
     */
    public NamedDomainObjectProvider<RunConfiguration> server() {
        return this.server(null);
    }

    /**
     * Create a run configuration pre-configured with standard settings to start the Minecraft server.
     *
     * Will return a reference if the server configuration already exists, while applying the configuration action.
     *
     * @return a provider for the configuration
     */
    public NamedDomainObjectProvider<RunConfiguration> server(final @Nullable Action<RunConfiguration> configureAction) {
        final NamedDomainObjectProvider<RunConfiguration> config;
        if (this.getNames().contains(Constants.RunConfiguration.SERVER_CONFIG)) {
            config = this.named(Constants.RunConfiguration.SERVER_CONFIG);
        } else {
            config = this.register(Constants.RunConfiguration.SERVER_CONFIG, this.configureServerRun());
        }
        if (configureAction != null) {
            config.configure(configureAction);
        }
        return config;
    }

    private Action<RunConfiguration> configureServerRun() {
        return run -> {
            run.mainClass().set("net.minecraft.server.Main"); // TODO: This does vary from version to version
        };
    }

    // -- Delegating to actual implementation -- //

    @Override
    public RunConfiguration create(final String name) throws InvalidUserDataException {
        return this.delegate.create(name);
    }

    @Override
    public RunConfiguration maybeCreate(final String name) {
        return this.delegate.maybeCreate(name);
    }

    @Override
    public RunConfiguration create(final String name, final Closure configureClosure) throws InvalidUserDataException {
        return this.delegate.create(name, configureClosure);
    }

    @Override
    public RunConfiguration create(final String name, final Action<? super RunConfiguration> configureAction)
            throws InvalidUserDataException {
        return this.delegate.create(name, configureAction);
    }

    @Override
    public NamedDomainObjectContainer<RunConfiguration> configure(final Closure configureClosure) {
        return this.delegate.configure(configureClosure);
    }

    @Override
    public NamedDomainObjectProvider<RunConfiguration> register(
            final String name, final Action<? super RunConfiguration> configurationAction) throws InvalidUserDataException {
        return this.delegate.register(name, configurationAction);
    }

    @Override
    public NamedDomainObjectProvider<RunConfiguration> register(final String name) throws InvalidUserDataException {
        return this.delegate.register(name);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return this.delegate.contains(o);
    }

    @Override
    public Iterator<RunConfiguration> iterator() {
        return this.delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return this.delegate.toArray();
    }

    @Override public <T> T[] toArray(final T[] a) {
        return this.delegate.toArray(a);
    }

    @Override
    public boolean add(final RunConfiguration e) {
        return this.delegate.add(e);
    }

    @Override
    public boolean remove(final Object o) {
        return this.delegate.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return this.delegate.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends RunConfiguration> c) {
        return this.delegate.addAll(c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return this.delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return this.delegate.retainAll(c);
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @Override
    public Namer<RunConfiguration> getNamer() {
        return this.delegate.getNamer();
    }

    @Override
    public SortedMap<String, RunConfiguration> getAsMap() {
        return this.delegate.getAsMap();
    }

    @Override
    public SortedSet<String> getNames() {
        return this.delegate.getNames();
    }

    @Nullable
    @Override
    public RunConfiguration findByName(final String name) {
        return this.delegate.findByName(name);
    }

    @Override
    public RunConfiguration getByName(final String name) throws UnknownDomainObjectException {
        return this.delegate.getByName(name);
    }

    @Override
    public RunConfiguration getByName(final String name, final Closure configureClosure) throws UnknownDomainObjectException {
        return this.delegate.getByName(name, configureClosure);
    }

    @Override
    public RunConfiguration getByName(final String name, final Action<? super RunConfiguration> configureAction) throws UnknownDomainObjectException {
        return this.delegate.getByName(name, configureAction);
    }

    @Override
    public RunConfiguration getAt(final String name) throws UnknownDomainObjectException {
        return this.delegate.getAt(name);
    }

    @Override
    public Rule addRule(final Rule rule) {
        return this.delegate.addRule(rule);
    }

    @Override
    public Rule addRule(final String description, final Closure ruleAction) {
        return this.delegate.addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(final String description, final Action<String> ruleAction) {
        return this.delegate.addRule(description, ruleAction);
    }

    @Override
    public List<Rule> getRules() {
        return this.delegate.getRules();
    }

    @Override
    public void addLater(final Provider<? extends RunConfiguration> provider) {
        this.delegate.addLater(provider);
    }

    @Override
    public void addAllLater(
            final Provider<? extends Iterable<RunConfiguration>> provider) {
        this.delegate.addAllLater(provider);
    }

    @Override
    public <S extends RunConfiguration> NamedDomainObjectSet<S> withType(final Class<S> type) {
        return this.delegate.withType(type);
    }

    @Override
    public <S extends RunConfiguration> DomainObjectCollection<S> withType(final Class<S> type, final Action<? super S> configureAction) {
        return this.delegate.withType(type, configureAction);
    }

    @Override
    public <S extends RunConfiguration> DomainObjectCollection<S> withType(final Class<S> type, final Closure configureClosure) {
        return this.delegate.withType(type, configureClosure);
    }

    @Override
    public NamedDomainObjectSet<RunConfiguration> matching(final Spec<? super RunConfiguration> spec) {
        return this.delegate.matching(spec);
    }

    @Override
    public NamedDomainObjectSet<RunConfiguration> matching(final Closure spec) {
        return this.delegate.matching(spec);
    }

    @Override
    public Action<? super RunConfiguration> whenObjectAdded(final Action<? super RunConfiguration> action) {
        return this.delegate.whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(final Closure action) {
        this.delegate.whenObjectAdded(action);
    }

    @Override
    public Action<? super RunConfiguration> whenObjectRemoved(final Action<? super RunConfiguration> action) {
        return this.delegate.whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(final Closure action) {
        this.delegate.whenObjectRemoved(action);
    }

    @Override
    public void all(final Action<? super RunConfiguration> action) {
        this.delegate.all(action);
    }

    @Override
    public void all(final Closure action) {
        this.delegate.all(action);
    }

    @Override
    public void configureEach(final Action<? super RunConfiguration> action) {
        this.delegate.configureEach(action);
    }

    @Override
    public NamedDomainObjectProvider<RunConfiguration> named(final String name) throws UnknownDomainObjectException {
        return this.delegate.named(name);
    }

    @Override
    public NamedDomainObjectProvider<RunConfiguration> named(
            final String name,
            final Action<? super RunConfiguration> configurationAction
    ) throws UnknownDomainObjectException {
        return this.delegate.named(name, configurationAction);
    }

    @Override
    public <S extends RunConfiguration> NamedDomainObjectProvider<S> named(
        final String name,
        final Class<S> type
    ) throws UnknownDomainObjectException {
        return this.delegate.named(name, type);
    }

    @Override
    public <S extends RunConfiguration> NamedDomainObjectProvider<S> named(
            final String name,
            final Class<S> type,
            final Action<? super S> configurationAction
    ) throws UnknownDomainObjectException {
        return this.delegate.named(name, type, configurationAction);
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return this.delegate.getCollectionSchema();
    }

    @Override
    public Set<RunConfiguration> findAll(final Closure spec) {
        return this.delegate.findAll(spec);
    }
}
