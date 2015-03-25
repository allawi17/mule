/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.config;

import static org.mule.module.extension.internal.config.XmlExtensionParserUtils.getResolverSet;
import org.mule.api.MuleContext;
import org.mule.extension.ExtensionManager;
import org.mule.extension.introspection.Configuration;
import org.mule.extension.introspection.Extension;
import org.mule.module.extension.internal.runtime.resolver.ConfigurationValueResolver;

import org.springframework.beans.factory.FactoryBean;

/**
 * A {@link FactoryBean} which returns a {@link ConfigurationValueResolver} that provides the actual instances
 * that implement a given {@link Configuration}. Subsequent invokations to {@link #getObject()} method
 * returns always the same {@link ConfigurationValueResolver}.
 *
 * @since 3.7.0
 */
final class ConfigurationFactoryBean extends BaseResolverFactoryBean<ConfigurationValueResolver>
{

    private final Extension extension;
    private final Configuration configuration;
    private final ExtensionManager extensionManager;
    private final MuleContext muleContext;

    ConfigurationFactoryBean(String name,
                             Extension extension,
                             Configuration configuration,
                             ElementDescriptor element,
                             ExtensionManager extensionManager,
                             MuleContext muleContext)
    {
        super(name, element);
        this.extension = extension;
        this.configuration = configuration;
        this.extensionManager = extensionManager;
        this.muleContext = muleContext;
        valueResolver = createValueResolver();
    }

    @Override
    protected ConfigurationValueResolver createValueResolver()
    {
        return new ConfigurationValueResolver(name,
                                              extension,
                                              configuration,
                                              getResolverSet(element, configuration.getParameters()),
                                              extensionManager,
                                              muleContext);
    }

    /**
     * @return {@link ConfigurationValueResolver}
     */
    @Override
    public Class<ConfigurationValueResolver> getObjectType()
    {
        return ConfigurationValueResolver.class;
    }
}
