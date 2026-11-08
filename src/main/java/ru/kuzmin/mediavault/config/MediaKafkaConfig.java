package ru.kuzmin.mediavault.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import ru.kuzmin.mediavault.service.ThumbnailOutbox;

@Configuration
@Profile("media-processing")
public class MediaKafkaConfig {
    private static final String DEAD_LETTER_TOPIC = ThumbnailOutbox.TOPIC + ".dlt";
    private static final int PARTITIONS = 3;

    @Bean
    NewTopic mediaReadyTopic() {
        return TopicBuilder.name(ThumbnailOutbox.TOPIC).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic mediaReadyDeadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    CommonErrorHandler thumbnailErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
