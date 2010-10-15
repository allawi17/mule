/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.model.seda;

import org.mule.MessageExchangePattern;
import org.mule.api.MuleEventContext;
import org.mule.api.config.MuleProperties;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.lifecycle.Callable;
import org.mule.api.registry.RegistrationException;
import org.mule.api.service.Service;
import org.mule.component.DefaultJavaComponent;
import org.mule.component.SimpleCallableJavaComponent;
import org.mule.config.ChainedThreadingProfile;
import org.mule.config.QueueProfile;
import org.mule.model.AbstractServiceTestCase;
import org.mule.object.PrototypeObjectFactory;
import org.mule.tck.MuleTestUtils;
import org.mule.util.concurrent.Latch;
import org.mule.util.queue.QueueManager;

import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import junit.framework.AssertionFailedError;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SedaServiceTestCase extends AbstractServiceTestCase
{
    private SedaService service;
    private Thread mainThread = Thread.currentThread();

    public SedaServiceTestCase()
    {
        setStartContext(true);
    }

    @Override
    protected void doSetUp() throws Exception
    {
        service = new SedaService(muleContext);
        service.setName("test");
        PrototypeObjectFactory factory = new PrototypeObjectFactory(Object.class);
        service.setComponent(new DefaultJavaComponent(factory));
        service.setModel(new SedaModel());
        service.getModel().setMuleContext(muleContext);
        service.getModel().initialise();
    }

    @Override
    protected Service getService()
    {
        return service;
    }

    /**
     * ENSURE THAT: 1) The queueProfile set on the SedaService is used to configure
     * the queue that is used. 2) The queue used by the SedaService has the correct
     * name.
     */
    public void testQueueConfiguration() throws Exception
    {
        boolean persistent = true;
        int capacity = 345;

        QueueManager queueManager = muleContext.getQueueManager();

        QueueManager mockTransactionalQueueManager = mock(QueueManager.class);
        when(mockTransactionalQueueManager.getQueueSession()).thenReturn(queueManager.getQueueSession());

        // Replace queueManager instance with mock via registry as it cannot be set
        // once muleContext is initialized.
        muleContext.getRegistry().registerObject(MuleProperties.OBJECT_QUEUE_MANAGER,
            mockTransactionalQueueManager);

        service.setQueueProfile(new QueueProfile(capacity, persistent));

        try
        {
            muleContext.getRegistry().registerService(service);
        }
        catch (RegistrationException e)
        {
            if (e.getCause().getCause().getCause() instanceof AssertionFailedError)
            {
                fail("Queue configuration does not match service queue profile");
            }
            else
            {
                throw e;
            }
        }
    }

    public void testSedaModelEventTimeoutDefault() throws Exception
    {
        service.initialise();

        assertNotNull(service.getQueueTimeout());
        assertTrue(service.getQueueTimeout() != 0);
    }

    /**
     * SEE MULE-3684
     * 
     * @throws Exception
     */
    public void testDispatchToPausedService() throws Exception
    {
        service.initialise();
        service.start();
        service.pause();
        service.dispatchEvent(MuleTestUtils.getTestEvent("test", 
            getTestInboundEndpoint(MessageExchangePattern.ONE_WAY), muleContext));

        // This test will timeout and fail if dispatch() blocks

    }

    /**
     * SEE MULE-3974
     * 
     * @throws Exception
     */
    public void testMaxActiveThreadsEqualsOneWhenExhaustedActionWait() throws Exception
    {
        final Latch latch = new Latch();
        service.setName("testMaxActiveThreadsEqualsOne");
        ChainedThreadingProfile threadingProfile = (ChainedThreadingProfile) muleContext.getDefaultServiceThreadingProfile();
        threadingProfile.setMaxThreadsActive(1);
        threadingProfile.setThreadWaitTimeout(200);
        threadingProfile.setPoolExhaustedAction(ThreadingProfile.WHEN_EXHAUSTED_WAIT);
        service.setThreadingProfile(threadingProfile);
        final SimpleCallableJavaComponent component = new SimpleCallableJavaComponent(new Callable()
        {

            public Object onCall(MuleEventContext eventContext) throws Exception
            {
                latch.countDown();
                return null;
            }
        });
        component.setMuleContext(muleContext);
        service.setComponent(component);
        muleContext.getRegistry().registerService(service);

        service.dispatchEvent(MuleTestUtils.getTestInboundEvent("test", service, muleContext));

        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));

        // This test will fail with RejectedExcecutionException if dispatch() blocks

    }

    /**
     * SEE MULE-3975
     * 
     * @throws Exception
     */
    public void testDoThreadingFalse() throws Exception
    {
        final Latch latch = new Latch();
        final String serviceName = "testDoThreadingFalse";

        service.setName(serviceName);
        ChainedThreadingProfile threadingProfile = (ChainedThreadingProfile) muleContext.getDefaultServiceThreadingProfile();
        threadingProfile.setDoThreading(false);
        service.setThreadingProfile(threadingProfile);
        final SimpleCallableJavaComponent component = new SimpleCallableJavaComponent(new Callable()
        {
            public Object onCall(MuleEventContext eventContext) throws Exception
            {
                assertEquals(mainThread, Thread.currentThread());
                latch.countDown();
                return null;
            }
        });
        component.setMuleContext(muleContext);
        service.setComponent(component);
        muleContext.getRegistry().registerService(service);

        service.dispatchEvent(MuleTestUtils.getTestEvent("test", 
            getTestInboundEndpoint(MessageExchangePattern.ONE_WAY), muleContext));

        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));

    }

    /**
     * SEE MULE-3975
     * 
     * @throws Exception
     */
    public void testDoThreadingTrue() throws Exception
    {
        final Latch latch = new Latch();
        final String serviceName = "testDoThreadingFalse";

        service.setName(serviceName);
        ChainedThreadingProfile threadingProfile = (ChainedThreadingProfile) muleContext.getDefaultServiceThreadingProfile();
        threadingProfile.setDoThreading(true);
        service.setThreadingProfile(threadingProfile);
        final SimpleCallableJavaComponent component = new SimpleCallableJavaComponent(new Callable()
        {
            public Object onCall(MuleEventContext eventContext) throws Exception
            {
                assertTrue(Thread.currentThread().getName().startsWith(serviceName));
                latch.countDown();
                return null;
            }
        });
        component.setMuleContext(muleContext);
        service.setComponent(component);
        muleContext.getRegistry().registerService(service);

        service.dispatchEvent(MuleTestUtils.getTestEvent("test", 
            getTestInboundEndpoint(MessageExchangePattern.ONE_WAY), muleContext));

        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));

    }

}
