/*
 * Copyright 2014-2021 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aeroncookbook.ipc;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.Configuration;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.samples.SampleConfiguration;
import org.agrona.CloseHelper;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * This is a Basic Aeron subscriber application.
 * <p>
 * The application subscribes to a default channel and stream ID. These defaults can
 * be overwritten by changing their value in {@link SampleConfiguration} or by
 * setting their corresponding Java system properties at the command line, e.g.:
 * -Daeron.sample.channel=aeron:udp?endpoint=localhost:5555 -Daeron.sample.streamId=20
 * This application only handles non-fragmented data. A DataHandler method is called
 * for every received message or message fragment.
 * For an example that implements reassembly of large, fragmented messages, see
 * {@link MultipleSubscribersWithFragmentAssembly}.
 */
public class BasicSubscriber
{
    private static final int STREAM_ID = 8;
    private static final String CHANNEL = "aeron:udp?endpoint=localhost:8081";
    private static final int FRAGMENT_COUNT_LIMIT = 10;
    private static final boolean EMBEDDED_MEDIA_DRIVER = true;

    /**
     * Main method for launching the process.
     *
     * @param args passed to the process.
     */

    public static void main(final String[] args)
    {
        System.out.println("Subscribing to " + CHANNEL + " on stream id " + STREAM_ID);

        final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;
        final Aeron.Context ctx = new Aeron.Context();

        if (EMBEDDED_MEDIA_DRIVER)
        {
            ctx.aeronDirectoryName(driver.aeronDirectoryName());
        }

        final FragmentHandler fragmentHandler = printAsciiMessage(STREAM_ID);
        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        // Create an Aeron instance using the configured Context and create a
        // Subscription on that instance that subscribes to the configured
        // channel and stream ID.
        // The Aeron and Subscription classes implement "AutoCloseable" and will automatically
        // clean up resources when this try block is finished
        try (Aeron aeron = Aeron.connect(ctx);
             Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID))
        {
            subscriberLoop(fragmentHandler, FRAGMENT_COUNT_LIMIT, running).accept(subscription);

            System.out.println("Shutting down...");
        }

        CloseHelper.close(driver);
    }

    /**
     * Return a reusable, parametrised event loop that calls a default {@link IdleStrategy} when no messages
     * are received.
     *
     * @param fragmentHandler to be called back for each message.
     * @param limit           passed to {@link Subscription#poll(FragmentHandler, int)}.
     * @param running         indication for loop.
     * @return loop function.
     */
    public static Consumer<Subscription> subscriberLoop(
            final FragmentHandler fragmentHandler, final int limit, final AtomicBoolean running)
    {
        return subscriberLoop(fragmentHandler, limit, running, newIdleStrategy());
    }

    /**
     * Return a reusable, parametrised event loop that calls and idler when no messages are received.
     *
     * @param fragmentHandler to be called back for each message.
     * @param limit           passed to {@link Subscription#poll(FragmentHandler, int)}.
     * @param running         indication for loop.
     * @param idleStrategy    to use for loop.
     * @return loop function.
     */
    public static Consumer<Subscription> subscriberLoop(
            final FragmentHandler fragmentHandler,
            final int limit,
            final AtomicBoolean running,
            final IdleStrategy idleStrategy)
    {
        return
                (subscription) ->
                {
                    final FragmentAssembler assembler = new FragmentAssembler(fragmentHandler);
                    while (running.get())
                    {
                        final int fragmentsRead = subscription.poll(assembler, limit);
                        idleStrategy.idle(fragmentsRead);
                    }
                };
    }

    public static IdleStrategy newIdleStrategy()
    {
        return Configuration.agentIdleStrategy("org.agrona.concurrent.BusySpinIdleStrategy", null);
    }

    public static FragmentHandler printAsciiMessage(final int streamId)
    {
        return (buffer, offset, length, header) ->
        {
            final String msg = buffer.getStringWithoutLengthAscii(offset, length);
            System.out.printf(
                    "Message to stream %d from session %d (%d@%d) <<%s>>%n",
                    streamId, header.sessionId(), length, offset, msg);
        };
    }

}
