package ht.kafka

/**
 * A Kafka Client for writing training data to a Kafka topic.
 */
object TrainingDataClient {

  def main(args: Array[String]): Unit = {

    assert(args.length == 4)

    var topic: String = "" // The topic name.
    var boostrap_servers: String = "" // The Kafka servers.
    var partitions: Int = 0 // The partitions of the Kafka data topic.
    var filepath: String = "" // The filepath of the json file, containing the data in csv format.
    var kafkaProducer: TrainingStreamProducer = new TrainingStreamProducer() // The Kafka data producer.

    // Check for arguments else set the default ones.
    try {
      topic = args(0)
      boostrap_servers = args(1)
      partitions = args(2).toInt
      filepath = args(3)
    } catch {
      case _: Exception => throw new RuntimeException("Invalid arguments provided.")
    }

    // Create the producer and write data to the given topic.
    try { // Create the kafka data producer
      kafkaProducer = kafkaProducer.setProducer(topic, boostrap_servers, partitions)
      // Send the data to the data topic
      kafkaProducer.sendDataPointsFromCSVFile(filepath, verbose = true)
      kafkaProducer.sendEndOfStream()
      System.out.println("Wrote the data set to the trainingData topic.")
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.out.println("Something went wrong. Terminating the training data client.")
    } finally {
      // Ensure that you always close the Kafka producer when you are done using it.
      kafkaProducer.close()
    }

  }

}
