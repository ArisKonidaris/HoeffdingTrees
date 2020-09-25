package ht.kafka

import java.io.{BufferedReader, File, FileReader}
import java.util
import java.util.Properties
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.databind.ObjectMapper
import ht.POJOs.DataInstance
import org.apache.kafka.clients.producer.{KafkaProducer, Producer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{LongSerializer, StringSerializer}
import scala.collection.JavaConverters._

/**
 * A class, wrapping a Kafka data producer for the training data. The class provides
 * several helper methods for sending training data and managing the Kafka Producer.
 */
class TrainingStreamProducer {

  private var topic: String = _ // The topic name for the training data.
  private var partitions: Int = _ // The number of partitions this topic.
  private var producer: Producer[Long, String] = _ // The actual Kafka producer.
  private var counter: Long = 0L // A counter utilized as a key for the Kafka messages.

  // Main constructor.
  def this(topic: String, broker_list: String, partitions: Integer) {
    this()
    this.topic = topic
    this.partitions = partitions
    this.counter = 0L
    // Creating the properties of the data Kafka Producer
    val props = new Properties
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker_list)
    props.put(ProducerConfig.CLIENT_ID_CONFIG, KafkaConstants.DATA_CLIENT_ID)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[LongSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    this.producer = new KafkaProducer[Long, String](props)
    System.out.println("INFO [KafkaDataProducer]: " + this.toString)
  }

  def sendEndOfStream(): Unit = {
    for (p <- 0 until partitions) {
      producer.send(new ProducerRecord[Long, String](topic, p, counter, "EOS"))
    }
  }

  /**
   * This method writes a String data point written in JSON format into the training data topic.
   *
   * @param data    A training data point.
   * @param verbose A flag for printing the training data point that was sent.
   */
  def sendDataPoint(data: String, verbose: Boolean): Unit = {
    try {
      if (this.partitions == 1)
        producer.send(new ProducerRecord[Long, String](topic, counter, data))
      else
        producer.send(new ProducerRecord[Long, String](topic, counter.toInt % this.partitions, counter, data))
      if (verbose)
        if (counter % 10000 == 0)
          System.out.println("INFO [KafkaDataProducer]:  runProducer(), counter=" + counter + ", data: " + data)
      if (counter == Long.MaxValue)
        counter = 0L
      else
        counter += 1
    } catch {
      case _: Exception =>
        System.out.println("Couldn't write the data point " + data + " to the Kafka topic " + topic + ".")
    }
  }

  /**
   * This method writes the training data, written into the provided file in csv format, into the
   * training data topic. This method currently supports only csv files with numerical values only.
   *
   * @param filepath The absolute path of the file with the data set written csv format.
   * @param verbose  A flag for printing the training data point that was sent.
   */
  def sendDataPointsFromCSVFile(filepath: String, verbose: Boolean): Unit = {
    val featureNames = util.Arrays.asList("simulated time", "num cells", "num division", "num death", "wall time", "oxygen", "tnf")
    try {
      val br = new BufferedReader(new FileReader(filepath))
      try {
        var line: String = br.readLine()
        while (line != null) {
          val split: Array[Double] = line.split(",").map(x => x.toDouble)
          val size = split.length - 1
          val features: java.util.List[java.lang.Double] = split.slice(0, size).map(Double.box).toList.asJava
          val target: Double = split(size)
          val dataPoint = new DataInstance(features, target)
          if (dataPoint.isValid) sendDataPoint(dataPoint.toString, verbose)
          line = br.readLine()
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          throw new RuntimeException("Something went wrong while streaming the file " + filepath + " to Kafka.")
      } finally if (br != null) br.close()
    }
  }

  /**
   * This method writes the training data, written into the
   * provided file in json format, into the training data topic.
   *
   * @param filepath The absolute path of the file with the training data set written json format.
   * @param verbose  A flag for printing the training data point that was sent.
   */
  def sendDataPointsFromJSONFile(filepath: String, verbose: Boolean): Unit = {
    assert(filepath.substring(filepath.length - 4) == ".json")
    try {
      val mapper = new ObjectMapper
      val root = mapper.readTree(new File(filepath))
      if (!root.isArray)
        throw new RuntimeException("A JSON Array is expected")
      else
        for (dataPoint <- mapper.convertValue(root, classOf[Array[DataInstance]]))
          if (dataPoint.isValid)
            sendDataPoint(dataPoint.toString, verbose)
    } catch {
      case _: Exception =>
        throw new RuntimeException("Something went wrong while streaming the file " + filepath + " to Kafka.")
    }
  }

  /**
   * This method closes the producer if its not null.
   * Never forget to call this method after you are done
   * this data producer.
   */
  def close(): Unit = {
    if (producer != null) {
      System.out.println("Terminating the Kafka producer " + this.toString + ".")
      producer.close()
    }
  }

  /**
   * A factory method.
   *
   * @param topic       The name of this topic.
   * @param partitions  The number of partitions this topic.
   * @param broker_list The Kafka broker's address. If Kafka is running in a cluster
   *                    then you can provide comma (,) separated addresses.
   * @return A TrainingDataStreamProducer instance.
   */
  def setProducer(topic: String, broker_list: String, partitions: Integer): TrainingStreamProducer = {
    new TrainingStreamProducer(topic, broker_list, partitions)
  }
}
