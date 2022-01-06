package ht

import ht.kafka.LongStringKafkaConsumer
import ht.concurrent.{ConcurrentTree, Worker => MLWorker}
import ht.dataBuffers.DataSet
import ht.math.{LabeledPoint, Point}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks.{break, breakable}
import java.util.concurrent.CyclicBarrier

import ht.trees.HoeffdingTree

/**
 * A concurrent implementation of Hoeffding Tree training.
 */
object ParallelHoeffdingTreeTraining {

  def main(args: Array[String]): Unit = {


    ///////////////////////////////////////////// Setting up the test arguments ////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    var experimentTime: Long = 0 // The time (in milliseconds) for the experiment to terminate.

    // Forming the topic name of the streaming training data set.
    val topic: String = {
      if (args.length >= 1) {
        try {
          args(0)
        } catch {
          case _: Throwable => DefaultTestSettings.defaultTopicName
        }
      } else DefaultTestSettings.defaultTopicName
    }

    // Forming the parallelism/partitions.
    val parallelism: Int = {
      if (args.length >= 1) {
        try {
          args(1).toInt
        } catch {
          case _: Throwable => DefaultTestSettings.defaultParallelism
        }
      } else DefaultTestSettings.defaultParallelism
    }

    // Forming the n_min hyper parameter of the Hoeffding Tree.
    val n_min: Int = {
      if (args.length >= 1) {
        try {
          args(2).toInt
        } catch {
          case _: Throwable => DefaultTestSettings.defaultNMin
        }
      } else DefaultTestSettings.defaultNMin
    }

    // Forming the tau hyper parameter of the Hoeffding Tree.
    val tau: Double = {
      if (args.length >= 1) {
        try {
          args(8).toDouble
        } catch {
          case _: Throwable => DefaultTestSettings.defaultTau
        }
      } else DefaultTestSettings.defaultTau
    }

    // Forming the delta hyper parameter of the Hoeffding Tree.
    val delta: Double = {
      if (args.length >= 1) {
        try {
          args(9).toDouble
        } catch {
          case _: Throwable => DefaultTestSettings.defaultDelta
        }
      } else DefaultTestSettings.defaultDelta
    }


    ///////////////////////////////////// Starting the training test.  /////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    if (parallelism == 1)
      SingleThreaded.main(Array(topic, n_min + "", tau + "", delta + "")) // Single thread training.
    else {

      // Multi thread training.

      // A CyclicBarrier to synchronize the starting of the worker threads.
      val start_barrier: CyclicBarrier = new CyclicBarrier(parallelism + 1)
      val sync_barrier: CyclicBarrier = new CyclicBarrier(parallelism + 1)
      val worker_barrier: CyclicBarrier = new CyclicBarrier(parallelism + 1)

      // Get number of points that each worker thread fits to its local leaves before notifying the coordinator thread.
      val n_min_par: Int = n_min / parallelism

      // Initializing necessary objects for parallel training.
      val ht: HoeffdingTree = new HoeffdingTree(n_min = n_min, tau = 0.05, delta = 1.0E-7D)
      val streams: ListBuffer[DataStream] = ListBuffer[DataStream]()
      val testSets: ListBuffer[DataSet[LabeledPoint]] = ListBuffer[DataSet[LabeledPoint]]()
      val dataBuffers: ListBuffer[ListBuffer[Point]] = ListBuffer[ListBuffer[Point]]()
      val trees: mutable.Map[Int, HoeffdingTree] = mutable.Map[Int, HoeffdingTree]()

      // Open the Kafka consumers.
      for (i: Int <- 0 until parallelism) {
        streams += new DataStream(new LongStringKafkaConsumer(topic, Some(List(i))))
        testSets += new DataSet[LabeledPoint](1000 / parallelism)
      }

      val startExpTime: Long = System.currentTimeMillis() // Starting time of the test.

      println("Warming up the Hoeffding Tree.")

      // Warmup.
      var warmup_count = 0
      breakable {
        while (true) {
          for (i: Int <- 0 until parallelism)
            if (dataBuffers.length < parallelism) dataBuffers += streams(i).receiveStream._2
          for (i: Int <- 0 until parallelism) {
            if (dataBuffers(i).nonEmpty) {
              dataBuffers(i).remove(0) match {
                case labeledPoint: LabeledPoint =>
                  ht.fit(labeledPoint)
                  warmup_count += 1
                case _ =>
              }
            } else dataBuffers(i) = streams(i).receiveStream._2
            if (ht.getHeight > 1 && ht.getNumberOfLeaves > 1) break
          }
        }
      }

      println("Warmup completed.")
      println(ht)

      // Create the Hoeffding Trees.
      for (i: Int <- 0 until parallelism) {
        val serialized_tree = ht.serialize
        trees.put(i, {
          val local_tree: HoeffdingTree = new HoeffdingTree(tau = 0.05, delta = 1.0E-7D)
          local_tree.deserialize(serialized_tree)
          local_tree.setNMin(n_min_par)
          local_tree
        }
        )
      }
      val concurrent_tree: ConcurrentTree = new ConcurrentTree(trees, sync_barrier, worker_barrier)

      // Create the workers.
      for (i: Int <- 0 until parallelism) {
        val workerThread: Thread = new Thread(
          new MLWorker(
            i,
            n_min_par,
            concurrent_tree,
            dataBuffers(i),
            streams(i),
            testSets(i),
            start_barrier,
            sync_barrier,
            worker_barrier
          )
        )
        concurrent_tree.addWorker(i, workerThread)
      }

      for (worker <- concurrent_tree.workers) worker._2.start()

      println("Starting the parallel training procedure.")
      start_barrier.await()
      for (worker <- concurrent_tree.workers) worker._2.join()

      experimentTime = System.currentTimeMillis() - startExpTime
      println("Training ended.")

      val testSet: ListBuffer[LabeledPoint] = testSets.flatMap(x => x.data_buffer)
      println("Multithreaded test results ...")
      Thread.sleep(2500)
      println("\nWarmup size: " + warmup_count)
      println("Test set size: " + testSet.length)
      println("Accuracy: " + trees.head._2.score(testSet))
      println("Tree size: " + trees.head._2.calculateTreeSize)
      println("Number of tuples processed: " + trees.head._2.getNumberOfDataPointsSeen)
      println("Total experiment time: " + experimentTime)

    }

  }

}
