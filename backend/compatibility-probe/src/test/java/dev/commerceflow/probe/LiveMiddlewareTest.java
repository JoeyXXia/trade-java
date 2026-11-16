package dev.commerceflow.probe;

import com.alibaba.nacos.api.NacosFactory;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "middleware.live", matches = "true")
class LiveMiddlewareTest {
    @Test
    void nacosRegistersAndDiscoversAnInstance() throws Exception {
        var naming = NacosFactory.createNamingService(System.getProperty("nacos.address", "127.0.0.1:8848"));
        String service = "day1-probe-" + UUID.randomUUID();
        try {
            naming.registerInstance(service, "127.0.0.1", 18080);
            assertThat(naming.getAllInstances(service, false))
                .anyMatch(instance -> instance.getIp().equals("127.0.0.1") && instance.getPort() == 18080);
        } finally {
            try { naming.deregisterInstance(service, "127.0.0.1", 18080); }
            finally { naming.shutDown(); }
        }
    }

    @Test
    void rocketMqSendsAndReadsBackExactPayload() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String topic = "Day1Probe" + suffix;
        String namesrv = System.getProperty("rocketmq.namesrv", "127.0.0.1:9876");
        var producer = new DefaultMQProducer("day1-producer-" + suffix);
        var consumer = new DefaultMQPullConsumer("day1-consumer-" + suffix);
        producer.setNamesrvAddr(namesrv);
        producer.setVipChannelEnabled(false);
        consumer.setNamesrvAddr(namesrv);
        consumer.setVipChannelEnabled(false);
        byte[] body = ("commerceflow-" + suffix).getBytes(StandardCharsets.UTF_8);
        try {
            producer.start();
            consumer.start();
            var sent = producer.send(new Message(topic, body));
            assertThat(sent.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
            var received = consumer.pull(sent.getMessageQueue(), "*", sent.getQueueOffset(), 1);
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (received.getPullStatus() != PullStatus.FOUND && System.nanoTime() < deadline) {
                Thread.sleep(100);
                received = consumer.pull(sent.getMessageQueue(), "*", sent.getQueueOffset(), 1);
            }
            assertThat(received.getPullStatus()).isEqualTo(PullStatus.FOUND);
            assertThat(received.getMsgFoundList()).hasSize(1);
            assertThat(received.getMsgFoundList().getFirst().getBody()).isEqualTo(body);
        } finally {
            consumer.shutdown();
            producer.shutdown();
        }
    }
}
