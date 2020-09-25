package ht.POJOs;

import java.io.Serializable;

/**
 * A Kafka Record class.
 */
public interface KafkaRecord extends Serializable {
    void setMetadata(String topic, Integer partition, Long key, Long offset, Long timestamp);
}
