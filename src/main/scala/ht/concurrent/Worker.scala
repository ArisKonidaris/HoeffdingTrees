package ht.concurrent

import java.util.concurrent.CyclicBarrier

import ht.dataBuffers.DataSet
import ht.DataStream
import ht.math.{LabeledPoint, Point}
import ht.trees.HoeffdingTree
import ht.trees.nodes.LeafNode

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks.{break, breakable}

/**
 * A Worker that trains its local Hoeffding Tree in a concurrent training procedure.
 *
 * @param id              The id of the Worker.
 * @param n_min           The number of data points that a leaf must observe before sending a notification to the Hub.
 * @param concurrent_tree The concurrent Hoeffding Trees.
 * @param data_buffer     A buffer that the Worker uses for initial training.
 * @param data_stream     A local Kafka data stream of training data.
 * @param test_set        A buffer that keeps some training data for testing the performance of the Hoeffding Tree.
 * @param start_barrier   A CyclicBarrier for starting the training procedure. This is used to start all the Worker
 *                        Threads approximately at the same time.
 * @param sync_barrier    A CyclicBarrier used for Synchronizing the Hub.
 * @param worker_barrier  A CyclicBarrier for Synchronizing the Workers.
 */
class Worker(val id: Int,
             val n_min: Long,
             val concurrent_tree: ConcurrentTree,
             val data_buffer: ListBuffer[Point],
             val data_stream: DataStream,
             val test_set: DataSet[LabeledPoint],
             val start_barrier: CyclicBarrier,
             val sync_barrier: CyclicBarrier,
             val worker_barrier: CyclicBarrier) extends Runnable {

  println("Worker " + id + " initialized.")

  var count: Int = 0 // This counter is used for counting the number of tuples received from the stream.
  var process_time: Long = 0 // The processing time (in milliseconds) of the worker.
  var experiment_time: Long = 0 // The time (in milliseconds) for the execution time of the worker.
  val local_tree: HoeffdingTree = concurrent_tree.trees(id) // The tree that this worker trains.

  def fit(point: LabeledPoint): Unit = {

    // Suspend the worker if synchronization is needed.
    if (concurrent_tree.syncClass.synchronize.synchronized(concurrent_tree.syncClass.synchronize)) {
      concurrent_tree.syncClass.terminated_workers.synchronized {
        concurrent_tree.syncClass.pending.synchronized {
          val r = {
            if (concurrent_tree.syncClass.pending == concurrent_tree.parallelism - concurrent_tree.syncClass.terminated_workers.length)
              1 + concurrent_tree.syncClass.terminated_workers.length
            else
              1
          }
          for (_ <- 0 until r) new Thread(new PseudoBarrier(worker_barrier)).start()
          concurrent_tree.syncClass.pending += r
        }
      }
      sync_barrier.await()
    }

    // Update the counter indicating the total number of data points that the Hoeffding Tree has been trained on.
    local_tree.incrementNumberOfDataProcessed()

    // Prepare the labeled point for learning.
    val (dataPoint, target) = local_tree.preprocessPoint(point)

    // Filter the data point through the Hoeffding Tree.
    val filtered_leaf: LeafNode = local_tree.filterLeaf(dataPoint)

    // Fit the data point to the filtered leaf.
    local_tree.updateLeaf(filtered_leaf, dataPoint, target, splitting = false, memoryCheck = false)

    if (filtered_leaf.n % n_min == 0)
      concurrent_tree.updateTree(filtered_leaf.id + "_" + filtered_leaf.height)

  }

  def fit(batch: ListBuffer[LabeledPoint]): Unit = for (point <- batch) fit(point)


  override def run(): Unit = {

    start_barrier.await()
    println("Worker " + id + " starts training.")

    val start_exp_time: Long = System.currentTimeMillis() // Starting time of this worker's execution.

    for (point: Point <- data_buffer) {
      point match {
        case labeledPoint: LabeledPoint =>
          test_set append labeledPoint match {
            case Some(l_point: LabeledPoint) => fit(l_point)
            case None =>
          }
        case _ =>
      }
    }
    data_buffer.clear()

    breakable {
      while (true) {
        val (stream_ended: Boolean, points: ListBuffer[Point]) = data_stream.receiveStream
        count += points.length // Count the number of points received from the data stream.

        // Form the training and test sets.
        // The test set will be consisted of the last 10000 labeled points of the data stream.
        val trainingSet: ListBuffer[LabeledPoint] = ListBuffer[LabeledPoint]()
        for (point: Point <- points) {
          point match {
            case labeledPoint: LabeledPoint =>
              test_set append labeledPoint match {
                case Some(l_point: LabeledPoint) => trainingSet += l_point
                case None =>
              }
            case _ =>
          }
        }

        // Train the Hoeffding Tree on the labeled data point steam.
        val t1: Long = System.currentTimeMillis()
        fit(trainingSet)
        process_time += System.currentTimeMillis() - t1

        // A progress report.
        println("Data points processed (" + id + "): " + count)

        // End the test if the data stream has ended.
        if (stream_ended) break

      }
    }

    experiment_time = System.currentTimeMillis() - start_exp_time

    // Ending the execution of the worker thread.
    data_stream.closeConsumer()
    println("Worker " + id + " terminated with. (experiment time: " +
      experiment_time + ", processing time: " + process_time + ")"
    )

    if (concurrent_tree.terminateWorker(id))
      for (_ <- 0 until concurrent_tree.parallelism) new Thread(new PseudoBarrier(worker_barrier)).start()

  }

}
