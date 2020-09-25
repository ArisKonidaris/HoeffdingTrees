package ht

import com.fasterxml.jackson.databind.ObjectMapper
import ht.kafka.LongStringKafkaConsumer
import ht.POJOs.DataInstance
import ht.math.{DenseVector, LabeledPoint, Point, UnlabeledPoint, Vector}
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

class DataStream(consumer: LongStringKafkaConsumer)  {

  private val mapper: ObjectMapper = new ObjectMapper()

  def parseDataInstance(instance: DataInstance): Point = {

    val features: (Vector, Vector, Array[String]) = {
      (if (instance.getNumericalFeatures == null)
        DenseVector()
      else
        DenseVector(instance.getNumericalFeatures.asInstanceOf[java.util.List[Double]].asScala.toArray),
        if (instance.getDiscreteFeatures == null)
          DenseVector()
        else
          DenseVector(instance.getDiscreteFeatures.asInstanceOf[java.util.List[Int]].asScala.toArray.map(x => x.toDouble)),
        if (instance.getCategoricalFeatures == null)
          Array[String]()
        else
          instance.getCategoricalFeatures.asScala.toArray
        )
    }

    if (instance.getTarget != null)
      LabeledPoint(instance.getTarget, features._1, features._2, features._3)
    else
      UnlabeledPoint(features._1, features._2, features._3)
  }

  def receiveStream: (Boolean, ListBuffer[Point]) = {
    val stream: ListBuffer[Point] = new ListBuffer[Point]()
    val records = consumer.consumeRecordsFromKafka()
    var end_of_stream = false
    records.foreach(
      {
        record: ConsumerRecord[Long, String] =>
          try {
            if (record.value().equals("EOS")) {
              closeConsumer()
              end_of_stream = true
            }
            val instance = mapper.readValue(record.value(), classOf[DataInstance])
            if (instance.isValid) stream.append(parseDataInstance(instance))
          } catch {
            case _: Throwable =>
          }
      }
    )
    (end_of_stream, stream)
  }

  def closeConsumer(): Unit = consumer.closeConsumer()

}
