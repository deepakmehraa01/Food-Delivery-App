package com.ecommerce.notification;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean NewTopic orderTopic(@Value("${notifications.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
    @Bean NewTopic failedTopic(@Value("${notifications.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 4L));
    }
}
