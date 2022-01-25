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
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Basic Aeron publisher application.
 * <p>
 * This publisher sends a fixed number of messages on a channel and stream ID,
 * then lingers to allow any consumers that may have experienced loss a chance to NAK for
 * and recover any missing data.
 * The default values for number of messages, channel, and stream ID are
 * defined in {@link SampleConfiguration} and can be overridden by
 * setting their corresponding properties via the command-line; e.g.:
 * -Daeron.sample.channel=aeron:udp?endpoint=localhost:5555 -Daeron.sample.streamId=20
 */
public class BasicPublisher
{
    private static final int STREAM_ID = 8;
    private static final String CHANNEL = "aeron:udp?endpoint=localhost:8081";
    private static final long LINGER_TIMEOUT_MS = 5000;
    private static final boolean EMBEDDED_MEDIA_DRIVER = true;

    public static void main(final String[] args) throws InterruptedException
    {
        // If configured to do so, create an embedded media driver within this application rather
        // than relying on an external one.
        isEmbeddedMediaDriver();
        sendingMessages(10);
    }

    public static MediaDriver isEmbeddedMediaDriver(){
        final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;
        return driver;
    }

    public static void sendingMessages(int n_messages) {
        final Aeron.Context ctx = new Aeron.Context();
        if (EMBEDDED_MEDIA_DRIVER)
        {
            ctx.aeronDirectoryName(isEmbeddedMediaDriver().aeronDirectoryName()); ///dev/shm
        }

        // Connect a new Aeron instance to the media driver and create a publication on
        // the given channel and stream ID.
        // The Aeron and Publication classes implement "AutoCloseable" and will automatically
        // clean up resources when this try block is finished
        try (Aeron aeron = Aeron.connect(ctx);
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID))
        {
            final UnsafeBuffer buffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(256, 64));

            for (long i = 0; i < n_messages; i++)
            {
                System.out.print("Offering " + i + "/" + n_messages + " - ");

                final int length = buffer.putStringWithoutLengthAscii(0, "Hello World! " + i);
                final long result = publication.offer(buffer, 0, length);

                if (result > 0)
                {
                    System.out.println("Sending ... :) !");
                }
                else if (result == Publication.BACK_PRESSURED)
                {
                    System.out.println("Offer failed due to back pressure");
                }
                else if (result == Publication.NOT_CONNECTED)
                {
                    System.out.println("Offer failed because publisher is not connected to a subscriber");
                }
                else if (result == Publication.ADMIN_ACTION)
                {
                    System.out.println("Offer failed because of an administration action in the system");
                }
                else if (result == Publication.CLOSED)
                {
                    System.out.println("Offer failed because publication is closed");
                    break;
                }
                else if (result == Publication.MAX_POSITION_EXCEEDED)
                {
                    System.out.println("Offer failed due to publication reaching its max position");
                    break;
                }
                else
                {
                    System.out.println("Offer failed due to unknown reason: " + result);
                }

                if (!publication.isConnected())
                {
                    System.out.println("No active subscribers detected");
                }

                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            }

            System.out.println("Done sending...");

            if (LINGER_TIMEOUT_MS > 0)
            {
                System.out.println("Lingering for " + LINGER_TIMEOUT_MS + " milliseconds...");
                Thread.sleep(LINGER_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
