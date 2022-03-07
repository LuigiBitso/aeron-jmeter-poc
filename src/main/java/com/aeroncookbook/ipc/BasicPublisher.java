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
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.concurrent.TimeUnit;

/**
 * Basic Aeron publisher application.
 * <p>
 * This publisher sends a fixed number of messages on a channel and stream ID,
 * then lingers to allow any consumers that may have experienced loss a chance to NAK for
 * and recover any missing data.
 * The default values for number of messages, channel, and stream ID are
 * defined in {@link io.aeron.samples.SampleConfiguration} and can be overridden by
 * setting their corresponding properties via the command-line; e.g.:
 * -Daeron.sample.channel=aeron:udp?endpoint=localhost:5555 -Daeron.sample.streamId=20
 */
public class BasicPublisher {

    private final int streamId;
    private final String channel;

    public BasicPublisher(String channel, int streamId) {
        this.streamId = streamId;
        this.channel = channel;
    }

    public static void main(String[] args) throws InterruptedException {
        //new BasicPublisher("aeron:udp?endpoint=localhost:8081", 8).send("teste");
    }

    public long send(String message) throws InterruptedException {
        System.out.println("Publishing to " + channel + " on stream id " + streamId);

        // If configured to do so, create an embedded media driver within this application rather
        // than relying on an external one.
        final MediaDriver driver = MediaDriver.launchEmbedded();

        final Aeron.Context ctx = new Aeron.Context();

        ctx.aeronDirectoryName(driver.aeronDirectoryName());

        long result;

        try (
                Aeron aeron = Aeron.connect(ctx);
                Publication publication = aeron.addPublication(channel, streamId)
        ) {
            final UnsafeBuffer buffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(256, 64));

            System.out.println("Sending - " + message);

            final int length = buffer.putStringWithoutLengthAscii(0, message);

            Thread.sleep(TimeUnit.SECONDS.toMillis(1));

            result = publication.offer(buffer, 0, length);

            if (result > 0) {
                System.out.println("sent");
            } else if (result == Publication.BACK_PRESSURED) {
                System.out.println("Offer failed due to back pressure");
            } else if (result == Publication.NOT_CONNECTED) {
                System.out.println("Offer failed because publisher is not connected to a subscriber");
            } else if (result == Publication.ADMIN_ACTION) {
                System.out.println("Offer failed because of an administration action in the system");
            } else if (result == Publication.CLOSED) {
                System.out.println("Offer failed because publication is closed");
            } else if (result == Publication.MAX_POSITION_EXCEEDED) {
                System.out.println("Offer failed due to publication reaching its max position");
            } else {
                System.out.println("Offer failed due to unknown reason: " + result);
            }

            if (!publication.isConnected()) {
                System.out.println("No active subscribers detected");
            }
        }

        CloseHelper.close(driver);

        return result;
    }
}
