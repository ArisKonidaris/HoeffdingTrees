package ht.kafka

import java.util
import java.util.Properties

import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.{PartitionInfo, TopicPartition}

import scala.collection.JavaConverters._

/**
 * An Apache Kafka consumer with a Long key and a String record.
 *
 * @param topic      A topic name.
 * @param partitions The number of partitions of the topic.
 * @param groupId    The group id of the Kafka consumer.
 * @param server     The boostrap-servers of the Kafka cluster.
 */
class LongStringKafkaConsumer(topic: String, partitions: Option[List[Int]], groupId: String, server: String) {

  ///////////////////////////////////////////////// Constructors ///////////////////////////////////////////////////////

  def this(topic: String) = this(topic, None, "consumer-group-" + topic, "localhost:9092")

  def this(topic: String, partition: Option[List[Int]]) = this(topic, partition, "consumer-group-" + topic, "localhost:9092")

  def this(topic: String, server: String) = this(topic, None, "consumer-group-" + topic, server)

  def this(topic: String, groupId: String, server: String) = this(topic, None, groupId, server)

  ////////////////////////////////////////////////// Variables /////////////////////////////////////////////////////////

  var consumer: KafkaConsumer[Long, String] = _

  val props: Properties = new Properties()
  props.put("bootstrap.servers", server)
  props.put("key.deserializer", "org.apache.kafka.common.serialization.LongDeserializer")
  props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("auto.offset.reset", "earliest")
  props.put("enable.auto.commit", "false")
  props.put("group.id", groupId)

  consumer = new KafkaConsumer[Long, String](props)

  partitions match {
    case Some(p: List[Int]) =>

      val existingPartitions: util.List[Int] = (
        for (part: PartitionInfo <- consumer.partitionsFor(topic).asScala) yield part.partition()
        ).asJava

      val validPartitions: util.List[TopicPartition] = p
        .filter(part => existingPartitions.contains(part))
        .map(part => new TopicPartition(topic, part))
        .asJava

      consumer.assign(validPartitions)
    case None =>
      consumer.subscribe(util.Arrays.asList(topic))

  }

  /////////////////////////////////////////////////// Methods //////////////////////////////////////////////////////////

  def consumeRecordsFromKafka(): Array[ConsumerRecord[Long, String]] = consumer.poll(100).asScala.toArray

  def consumeFromKafka(): Array[String] = consumer.poll(100).asScala.toArray.map(x => x.value())

  def closeConsumer(): Unit = {
    try {
      consumer.close()
    } catch {
      case _: Throwable =>
    }

  }

}
