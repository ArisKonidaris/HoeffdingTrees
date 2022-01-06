package ht

import ht.kafka.LongStringKafkaConsumer
import ht.dataBuffers.DataSet
import ht.math.{LabeledPoint, Point}
import ht.trees.HoeffdingTree
import moa.core.TimingUtils
import java.io.PrintWriter

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks.{break, breakable}

/**
 * A single threaded implementation of Hoeffding Tree training.
 */
object SingleThreaded {

  def main(args: Array[String]): Unit = {


    ///////////////////////////////////////////// Setting up the test arguments ////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    assert(args.length == 0 || args.length == 4)

    // Forming the topic name of the streaming training data set.
    val topic: String = {
      if (args.length >= 1) {
        try {
          args(0)
        } catch {
          case _: Throwable => "trainingData"
        }
      } else "trainingData"
    }

    // Forming the n_min hyper parameter of the Hoeffding Tree.
    val n_min: Int = {
      if (args.length >= 1) {
        try {
          args(1).toInt
        } catch {
          case _: Throwable => DefaultTestSettings.defaultNMin
        }
      } else DefaultTestSettings.defaultNMin
    }

    // Forming the tau hyper parameter of the Hoeffding Tree.
    val tau: Double = {
      if (args.length >= 1) {
        try {
          args(2).toDouble
        } catch {
          case _: Throwable => DefaultTestSettings.defaultTau
        }
      } else DefaultTestSettings.defaultTau
    }

    // Forming the delta hyper parameter of the Hoeffding Tree.
    val delta: Double = {
      if (args.length >= 1) {
        try {
          args(3).toDouble
        } catch {
          case _: Throwable => DefaultTestSettings.defaultDelta
        }
      } else DefaultTestSettings.defaultDelta
    }


    ///////////////////////////////////// Starting the training test.  /////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Initialize the data steam.
    val stream: DataStream = new DataStream(new LongStringKafkaConsumer(topic)) // The data source.

    try {

      var count: Int = 0 // This counter is used for counting the number of tuples received from the stream.
      var processTime: Long = 0 // The processing time (in milliseconds) of the streaming algorithm.
      var experimentTime: Long = 0 // The time (in milliseconds) for the experiment to terminate.
      val testSet: DataSet[LabeledPoint] = new DataSet[LabeledPoint](1000) // The test set.
      val ht: HoeffdingTree = new HoeffdingTree(n_min = n_min, tau = tau, delta = delta) // The Hoeffding tree.

      val startExpTime: Long = System.currentTimeMillis() // Starting time of the test.

      breakable {
        while (true) {
          val (stream_ended: Boolean, points: ListBuffer[Point]) = stream.receiveStream
          count += points.length // Count the number of points received from the data stream.

          // Form the training and test sets.
          // The test set will be consisted of the last 10000 labeled points of the data stream.
          val trainingSet: ListBuffer[LabeledPoint] = ListBuffer[LabeledPoint]()
          for (point: Point <- points) {
            point match {
              case labeledPoint: LabeledPoint =>
                testSet append labeledPoint match {
                  case Some(l_point: LabeledPoint) => trainingSet += l_point
                  case None =>
                }
              case _ =>
            }
          }

          // Train the Hoeffding Tree on the labeled data point steam.
          for (point <- trainingSet) {
            val t1 = TimingUtils.getNanoCPUTimeOfCurrentThread
            ht.fit(point)
            processTime += TimingUtils.getNanoCPUTimeOfCurrentThread - t1
          }

          // A progress report.
          println("Data points processed: " + count)

          // End the test if the data stream has ended.
          if (stream_ended) break

        }
      }

      experimentTime = System.currentTimeMillis() - startExpTime


      ///////////////////////////////////////// End of the experiment. /////////////////////////////////////////////////
      //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

      stream.closeConsumer()
      println(ht)

      if (DefaultTestSettings.storeTreeToFile)
        new PrintWriter(DefaultTestSettings.storeFilepath) { write(ht.serialize.toJsonString); close() }

      // Prints.
      println("Single threaded test results ...")
      println("Accuracy: " + ht.score(testSet.data_buffer))
      println("Tree size: " + ht.calculateTreeSize)
      println("Test set size: " + testSet.data_buffer.length)
      println("Number of tuples processed: " + ht.getNumberOfDataPointsSeen)
//      println("Processing time: " + processTime)
      println("Processing time: " + TimingUtils.nanoTimeToSeconds(processTime))
      println("Total experiment time: " + experimentTime)

    } catch {
      case e: Throwable =>
        stream.closeConsumer()
        e.printStackTrace()
    }

  }

}
