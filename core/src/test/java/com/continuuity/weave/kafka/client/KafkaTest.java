package com.continuuity.weave.kafka.client;

import com.continuuity.weave.common.Services;
import com.continuuity.weave.internal.kafka.client.Compression;
import com.continuuity.weave.internal.kafka.client.SimpleKafkaClient;
import com.continuuity.weave.zookeeper.ZKClientService;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.Futures;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class KafkaTest {

  @Test
  @Ignore
  public void testKafka() throws InterruptedException, ExecutionException {
    ZKClientService zkClientService = ZKClientService.Builder.of("localhost:2181").build();

    KafkaClient kafkaClient = new SimpleKafkaClient(zkClientService);
    Services.chainStart(zkClientService, kafkaClient).get();

    String topic = "topic" + System.currentTimeMillis();

    Thread t1 = createPublishThread(kafkaClient, topic, Compression.GZIP, "GZIP Testing message", 10);
    Thread t2 = createPublishThread(kafkaClient, topic, Compression.NONE, "Testing message", 10);

    t1.start();
    t2.start();

    Thread t3 = createPublishThread(kafkaClient, topic, Compression.SNAPPY, "Snappy Testing message", 10);
    t2.join();
    t3.start();

    Iterator<FetchedMessage> consumer = kafkaClient.consume(topic, 0, 0, 1048576);
    int count = 0;
    long startTime = System.nanoTime();
    while (count < 30 && consumer.hasNext() && secondsPassed(startTime, TimeUnit.NANOSECONDS) < 5) {
      System.out.println(Charsets.UTF_8.decode(consumer.next().getBuffer()));
      count++;
    }

    Assert.assertEquals(30, count);

    Services.chainStop(kafkaClient, zkClientService).get();
  }

  private Thread createPublishThread(final KafkaClient kafkaClient, final String topic,
                                     final Compression compression, final String message, final int count) {
    return new Thread() {
      public void run() {
        PreparePublish preparePublish = kafkaClient.preparePublish(topic, compression);
        for (int i = 0; i < count; i++) {
          preparePublish.add((i + " " + message).getBytes(Charsets.UTF_8), 0);
        }
        Futures.getUnchecked(preparePublish.publish());
      }
    };
  }

  private long secondsPassed(long startTime, TimeUnit startUnit) {
    return TimeUnit.SECONDS.convert(System.nanoTime() - TimeUnit.NANOSECONDS.convert(startTime, startUnit),
                                    TimeUnit.NANOSECONDS);
  }
}
