package ht

import java.io.{BufferedReader, IOException, InputStreamReader}
import ht.kafka.{KafkaConstants, TrainingDataClient}
import org.slf4j.LoggerFactory

object Main {

  /**
   * A method for checking if a Kafka topic already exists.
   *
   * @param rt                An instance of class [[Runtime]].
   * @param kafka_path        The absolute path of the Apache Kafka bin file.
   * @param topic             The topic name.
   * @param bootstrap_servers The servers of the Kafka cluster.
   * @return True if the given topic exists.
   */
  @throws[IOException]
  def topicExists(rt: Runtime, kafka_path: String, topic: String, bootstrap_servers: String): Boolean = {

    val topic_list_command: String = kafka_path + "kafka-topics.sh --list --bootstrap-server " + bootstrap_servers

    // Run Kafka command to list all the available topics.
    println(">> " + topic_list_command)
    val topicList: Process = rt.exec(topic_list_command)

    // Parse the answer to check if the topic exists.
    val in: BufferedReader = new BufferedReader(new InputStreamReader(topicList.getInputStream))
    var inputLine: String = in.readLine
    var exists: Boolean = false
    while (inputLine != null) {
      if (inputLine == topic) exists = true
      inputLine = in.readLine
    }

    exists
  }

  /**
   * Checks whether the actual number of partitions is the same as the given partitions for the test.
   *
   * @param rt                An instance of class [[Runtime]].
   * @param kafka_path        The absolute path of the Apache Kafka bin file.
   * @param topic             The topic name.
   * @param bootstrap_servers The servers of the Kafka cluster.
   * @param partitions        The given partitions of the test to test against the actual number of
   *                          partitions for the given topic.
   * @return A flag determining if the the actual number of partitions of the given topic is equal to the given number
   *         of partitions for the test.
   */
  def checkPartitions(rt: Runtime,
                      kafka_path: String,
                      topic: String,
                      bootstrap_servers: String,
                      partitions: Int): Boolean = {

    val topic_info_command: String = kafka_path + "kafka-topics.sh --describe --bootstrap-server " +
      bootstrap_servers + " --topic " + topic

    println(">> " + topic_info_command)
    val topic_info: Process = rt.exec(topic_info_command)

    val actual_partitions: Int = {
      try {
        val in: BufferedReader = new BufferedReader(new InputStreamReader(topic_info.getInputStream))
        val inputLine: String = in.readLine
        if (inputLine != null) {
          val start_index: Int = inputLine.indexOf("PartitionCount")
          val end_index: Int = inputLine.indexOf("ReplicationFactor")
          inputLine.substring(start_index + 16, end_index).trim().toInt
        } else 0
      } catch {
        case _: Throwable => 0
      }
    }

    println("partitions: " + partitions)
    println("actual_partitions: " + actual_partitions)
    partitions == actual_partitions

  }

  /**
   * A method for creating a Kafka topic.
   *
   * @param rt                An instance of class [[Runtime]].
   * @param kafka_path        The absolute path of the Apache Kafka bin file.
   * @param topic             The name of the topic to be created.
   * @param partitions        The number of partitions of the topic to be created.
   * @param bootstrap_servers The servers of the Kafka cluster.
   * @param rep_factor        The replication factor of the topic to be created.
   */
  @throws[IOException]
  @throws[InterruptedException]
  def createTopic(rt: Runtime,
                  kafka_path: String,
                  topic: String,
                  partitions: Int,
                  bootstrap_servers: String,
                  rep_factor: Int): Unit = {
    System.out.println("Creating topic " + topic)
    while (!topicExists(rt, kafka_path, topic, bootstrap_servers)) {
      val create_topic_command: String = kafka_path + "kafka-topics.sh --create --bootstrap-server " +
        bootstrap_servers + " --replication-factor " + rep_factor + " --partitions " + partitions + " --topic " + topic
      println(">> " + create_topic_command)
      rt.exec(create_topic_command)
      Thread.sleep(5000)
    }
    System.out.println("Topic " + topic + " created.")
  }

  /**
   * A method for deleting a Kafka topic.
   *
   * @param rt                An instance of class [[Runtime]].
   * @param kafka_path        The absolute path of the Apache Kafka bin file.
   * @param topic             The name of the topic to be deleted.
   * @param bootstrap_servers The servers of the Kafka cluster.
   */
  def deleteTopic(rt: Runtime,
                  kafka_path: String,
                  topic: String,
                  bootstrap_servers: String): Unit = {
    try {
      System.out.println("Deleting topic " + topic)
      while (topicExists(rt, kafka_path, topic, bootstrap_servers)) {
        val delete_topic_command: String = kafka_path + "kafka-topics.sh --delete --bootstrap-server " +
          bootstrap_servers + " --topic " + topic
        println(">> " + delete_topic_command)
        rt.exec(delete_topic_command)
        Thread.sleep(5000)
      }
      System.out.println("Topic " + topic + " deleted.")
    } catch {
      case _: Throwable =>
    }
  }


  /**
   * Creates and writes data to a Kafka topic.
   *
   * @param rt                An instance of class [[Runtime]].
   * @param kafka_path        The absolute path of the Apache Kafka bin file.
   * @param topic             The name of the topic to be created and write data into.
   * @param partitions        The partitions of the topic to be created and write data into.
   * @param bootstrap_servers The servers of the Kafka cluster.
   * @param rep_factor        The replication factor of the topic to be created.
   * @param dataPath          The path to the data set file.
   */
  @throws[IOException]
  @throws[InterruptedException]
  def createAndWriteDataToTopic(rt: Runtime,
                                kafka_path: String,
                                topic: String,
                                partitions: Int,
                                bootstrap_servers: String,
                                rep_factor: Int,
                                dataPath: String): Unit = {
    val topic_exists: Boolean = topicExists(rt, kafka_path, topic, bootstrap_servers)
    if (!topic_exists || (topic_exists && !checkPartitions(rt, kafka_path, topic, bootstrap_servers, partitions))) {
      if (topic_exists) deleteTopic(rt, kafka_path, topic, bootstrap_servers)
      createTopic(rt, kafka_path, topic, partitions, bootstrap_servers, rep_factor)
      TrainingDataClient.main(Array[String](topic, bootstrap_servers, partitions + "", dataPath))
    }
  }

  /**
   * Sets up the test for running a Hoeffding Tree experiment.
   *
   * @param args [path of data file,
   *             topic name,
   *             partitions/parallelism,
   *             replication factor,
   *             kafka path,
   *             kafka servers/brokers,
   *             n_min,
   *             tau,
   *             delta]
   */
  def main(args: Array[String]): Unit = {


    ///////////////////////////////////////////// Setting up the test arguments ////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    assert(args.length == 0 || args.length == 9) // Asserting the command line parameters.
    val rt = Runtime.getRuntime // Create a Runtime instance to execute command lines.

    // Forming the absolute path of the training data set file.
    val filepath: String = {
      if (args.length == 10) {
        try {
          args(0)
        } catch {
          case _: Throwable => DefaultTestSettings.defaultFilepath
        }
      } else DefaultTestSettings.defaultFilepath
    }
    println("Data set file path: " + filepath)

    // Forming the topic name of the streaming training data set.
    val topic: String = {
      if (args.length == 10) {
        try {
          args(1)
        } catch {
          case _: Throwable => DefaultTestSettings.defaultTopicName
        }
      } else DefaultTestSettings.defaultTopicName
    }
    println("Topic name: " + topic)

    // Forming the parallelism/partitions.
    val parallelism: Int = {
      if (args.length == 10) {
        try {
          args(2).toInt
        } catch {
          case _: Throwable => DefaultTestSettings.defaultParallelism
        }
      } else DefaultTestSettings.defaultParallelism
    }
    println("partitions/parallelism: " + parallelism)

    // Forming the replication factor of the Kafka topic that contains the training data set.
    val replication_factor: Int = {
      if (args.length == 10) {
        try {
          args(3).toInt
        } catch {
          case _: Throwable => DefaultTestSettings.defaultReplicationFactor
        }
      } else DefaultTestSettings.defaultReplicationFactor
    }
    println("replication_factor: " + replication_factor)

    // Forming the absolute path of the Apache Kafka bin folder.
    val kafka_path: String = {
      if (args.length == 10) {
        try {
          args(4)
        } catch {
          case _: Throwable => KafkaConstants.KAFKA_PATH
        }
      } else KafkaConstants.KAFKA_PATH
    }
    println("Kafka path: " + kafka_path)

    // Forming the server of the Apache Kafka.
    val bootstrap_servers: String = {
      if (args.length == 10) {
        try {
          args(5)
        } catch {
          case _: Throwable => KafkaConstants.BOOTSTRAP_SERVER
        }
      } else KafkaConstants.BOOTSTRAP_SERVER
    }
    println("Bootstrap servers: " + bootstrap_servers)

    // Forming the n_min hyper parameter of the Hoeffding Tree.
    val n_min: Int = {
      if (args.length == 10) {
        try {
          args(6).toInt
        } catch {
          case _: Throwable => DefaultTestSettings.defaultNMin
        }
      } else DefaultTestSettings.defaultNMin
    }
    println("n_min: " + n_min)
    println("n_min_local: " + n_min / parallelism)

    // Forming the tau hyper parameter of the Hoeffding Tree.
    val tau: Double = {
      if (args.length == 10) {
        try {
          args(7).toDouble
        } catch {
          case _: Throwable => DefaultTestSettings.defaultTau
        }
      } else DefaultTestSettings.defaultTau
    }
    println("tau: " + tau)

    // Forming the delta hyper parameter of the Hoeffding Tree.
    val delta: Double = {
      if (args.length == 10) {
        try {
          args(8).toDouble
        } catch {
          case _: Throwable => DefaultTestSettings.defaultDelta
        }
      } else DefaultTestSettings.defaultDelta
    }
    println("delta: " + delta)


    /////////////////////////////////// Creating the Kafka Data Source  ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    println("Setting up the training data topic.")
    createAndWriteDataToTopic(rt, kafka_path, topic, parallelism, bootstrap_servers, replication_factor, filepath)
    System.out.println("The training data topic is all set up.")


    ///////////////////////////////////// Starting the training test.  /////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ParallelHoeffdingTreeTraining.main(Array(topic, parallelism + "", n_min + "", tau + "", delta + ""))


  }

}
