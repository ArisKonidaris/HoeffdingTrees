package ht.kafka

/**
 * An object that contains some default parameters for Apache Kafka.
 */
object KafkaConstants {

  val KAFKA_PATH = "/home/aris/software/kafka/bin/"
  val BOOTSTRAP_SERVER = "localhost:9092"
  val ZOOKEEPER_SERVER_PORT = "2182"

  val KAFKA_BROKER_1 = "localhost:9092"
  val KAFKA_BROKER_2 = "localhost:9093"
  val KAFKA_BROKER_3 = "localhost:9094"
  val KAFKA_BROKER_4 = "localhost:9095"
  val KAFKA_BROKERS_LIST: String = KAFKA_BROKER_1 + "," + KAFKA_BROKER_2 + "," + KAFKA_BROKER_3 + "," + KAFKA_BROKER_4

  val DATA_TOPIC_NAME = "trainingData"
  val DATA_KAFKA_BROKERS = "localhost:9092"
  val DATA_CLIENT_ID = "data_client_1"

  //////////////////////// Other useful parameters (currently not used) for a Kafka client /////////////////////////////

  val ACKS = "all"
  val RETRIES = 0
  val BATCH_SIZE = 16384
  val LINGER_MS = 0
  val BUFFER_MEMORY = 33554432
  val OFFSET_RESET_LATEST = "latest"
  val OFFSET_RESET_EARLIER = "earliest"
  val MAX_POLL_RECORDS = 1

}
