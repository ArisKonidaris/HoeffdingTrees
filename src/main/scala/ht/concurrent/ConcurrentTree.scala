package ht.concurrent

import java.util.concurrent.CyclicBarrier

import ht.trees.HoeffdingTree
import ht.trees.nodes.LeafNode

import scala.collection.mutable

/**
 * A class containing all the Hoeffding Trees of the concurrent workers. An instance of this class is shared by all the
 * Worker Threads. Each and every one of them fits its own Tree. This class is also used for synchronizing all the trees
 * by utilizing Cyclic Barriers and a synchronizing thread that this class spawns.
 *
 * @param trees          A hash map of Hoeffding Trees with size equal to the number of Worker Threads.
 * @param sync_barrier   A [[CyclicBarrier]] instance for synchronizing the execution of the Hub.
 * @param worker_barrier A [[CyclicBarrier]] instance for synchronizing the execution of the Worker Threads.
 */
class ConcurrentTree(val trees: mutable.Map[Int, HoeffdingTree],
                     val sync_barrier: CyclicBarrier,
                     val worker_barrier: CyclicBarrier) {

  val workers: mutable.Map[Int, Thread] = mutable.Map[Int, Thread]()
  var parallelism: Int = 0
  val leaf_counters: mutable.Map[String, Int] = mutable.HashMap[String, Int]()
  val leaf_map: mutable.Map[String, LeafNode] = mutable.HashMap[String, LeafNode]()
  var syncClass: SyncClass = SyncClass()
  val synchronizer: Thread = {
    val hub = new Thread(new Synchronizer(trees, workers, leaf_counters, syncClass, sync_barrier, worker_barrier))
    hub.start()
    hub
  }

  /**
   * Add a Worker Thread to the training procedure.
   *
   * @param id     The id of the Worker.
   * @param worker The Thread running the Worker.
   */
  def addWorker(id: Int, worker: Thread): Unit = {
    workers.put(id, worker)
    parallelism += 1
  }

  /**
   * This method is called by a Worker Thread when is about to terminate. This method triggers a synchronization/merging
   * of the concurrent Hoeffding Trees.
   *
   * @param worker_id The id of the Worker Thread that is about to terminate.
   * @return Returns true if the Worker that is about to terminate is the last one alive.
   */
  def terminateWorker(worker_id: Int): Boolean = {
    synchronized {
      syncClass.terminated_workers += worker_id
      if (syncClass.terminated_workers.length < parallelism) {
        syncClass.synchronized(syncClass.synchronize = true)
        false
      } else true
    }
  }

  /**
   * A method called by a Worker Thread when trained on a specific number of data points per leaf. This method can trigger
   * a synchronization of the concurrent trees.
   *
   * @param leafID The id of the leaf to synchronize.
   */
  def updateTree(leafID: String): Unit = {
    synchronized {
      val new_count = {
        try {
          leaf_counters(leafID) + 1
        } catch {
          case _: java.util.NoSuchElementException => leaf_counters.put(leafID, 0)
            1
        }
      }
      leaf_counters(leafID) = new_count
      if (new_count == parallelism) {
        syncClass.synchronized {
          syncClass.synchronize = true
          syncClass.sync.enqueue(leafID)
        }
      }
    }
  }

}
