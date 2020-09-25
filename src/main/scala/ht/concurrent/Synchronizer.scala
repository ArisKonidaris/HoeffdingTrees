package ht.concurrent

import java.util.concurrent.CyclicBarrier

import ht.trees.HoeffdingTree
import ht.trees.nodes.{InternalNode, LeafNode, TestAttribute}

import scala.collection.mutable

class Synchronizer(val trees: mutable.Map[Int, HoeffdingTree],
                   val workers: mutable.Map[Int, Thread],
                   val leaf_counters: mutable.Map[String, Int],
                   val syncClass: SyncClass,
                   val sync_barrier: CyclicBarrier,
                   val worker_barrier: CyclicBarrier)
  extends Runnable {

  var numberOfDataPointsSeen: Long = trees.head._2.getNumberOfDataPointsSeen
  val leafMap: mutable.HashMap[String, LeafNode] = {
    val lm = mutable.HashMap[String, LeafNode]()
    for ((leafID, leaf) <- trees(0).getLeafMap) lm.put(leafID, leaf.generateNode.asInstanceOf[LeafNode])
    lm
  }

  /**
   * A method for suspending the Synchronizer. This is done for releasing CPU time.
   */
  def suspend(): Unit = {
    try {
//      println("Suspending synchronizer ...")
      syncClass.pending.synchronized(syncClass.pending = 1)
      worker_barrier.await()
//      println("Resuming synchronizer ...")
    } catch {
      case _: InterruptedException => throw new InterruptedException
    }
  }

  /**
   * A method for generating a new leaf by combining all the parallel local leaves with a specific leaf id.
   *
   * @param leafID The id of the leaf to combine.
   * @return A combined leaf.
   */
  def mergeLeaf(leafID: String): LeafNode = {
    val first_tree: HoeffdingTree = trees.head._2
    if (trees.size == 1)
      first_tree.getLeafMap(leafID)
    else {
      val new_leaf: LeafNode = first_tree.getLeafMap(leafID).generateNode.asInstanceOf[LeafNode]
      for ((workerID, _) <- trees.tail) {
        val leaf_drift: LeafNode = getLeafDrift(trees(workerID).getLeafMap(leafID))
        new_leaf.n += leaf_drift.n
        new_leaf.errors += leaf_drift.errors
        new_leaf.stats.mergeStats(leaf_drift.stats)
      }
      new_leaf
    }
  }

  def getLeafDrift(leaf: LeafNode): LeafNode = {
    val leaf_drift: LeafNode = leaf.generateNode.asInstanceOf[LeafNode]
    val previous_leaf: LeafNode = leafMap(leaf_drift.id + "_" + leaf_drift.height)
    leaf_drift.stats.n_l -= previous_leaf.stats.n_l
    for ((target, count) <- previous_leaf.stats.classDistribution)
      leaf_drift.stats.classDistribution(target) -= count
    leaf_drift
  }

  def terminateWorker(workerID: Int): Unit = {
    workers.remove(workerID)
    if (trees.size == 1) {
      for ((_, leaf) <- trees.head._2.getLeafMap)
        if (leaf.n > 0) trees.head._2.split(leaf, checkMemory = false)
      throw new InterruptedException
    } else {
      val deleted_tree: HoeffdingTree = {
        val g_tree: HoeffdingTree = new HoeffdingTree()
        g_tree.deserialize(trees(workerID).serialize)
        trees.remove(workerID)
        g_tree
      }
      val head_tree: HoeffdingTree = trees.head._2
      for ((leafID, leaf) <- head_tree.getLeafMap) {
        val terminated_leaf: LeafNode = deleted_tree.getLeafMap(leafID)
        if (terminated_leaf.n > 0) {
          val leaf_drift: LeafNode = getLeafDrift(terminated_leaf)
          leaf.n += leaf_drift.n
          leaf.errors += leaf_drift.errors
          leaf.stats.mergeStats(leaf_drift.stats)
        }
      }
    }
  }

  def synchronizeTrees(): Unit = {

    while (syncClass.synchronized(syncClass.sync.nonEmpty)) {

      val leafID: String = syncClass.sync.synchronized(syncClass.sync.dequeue())
      val first_tree: HoeffdingTree = trees.head._2

      // Generate the new Leaf.
      val new_leaf: LeafNode = mergeLeaf(leafID)

      // Calculating the Hoeffding bound along with the best splits for the new leaf. Check if the Leaf must split.
      val hoeffdingBound: Double = Math.sqrt(
        (Math.pow(first_tree.getRange, 2) * Math.log(1.0 / first_tree.getDelta)) / (2.0 * new_leaf.stats.n_l)
      )
      val bestSplits = new_leaf.stats.bestSplits
      val bestSplit = bestSplits.head
      val secondBestSplit = bestSplits.tail.head

      // Check if split is needed.
      if (bestSplit._3 - secondBestSplit._3 > hoeffdingBound || hoeffdingBound < first_tree.getTau) {

        // Update the auxiliary data of the tree.
        val h: Int = new_leaf.height + 1
        var new_height: Int = first_tree.getHeight
        if (first_tree.getHeight < h) new_height = h
        val new_numberOfInternalNodes: Int = first_tree.getNumberOfInternalNodes + 1
        val new_numberOfLeaves: Int = first_tree.getNumberOfLeaves + 1
        val new_activeLeaves: Int = first_tree.getNumberOfActiveLeaves + 1
        val new_LeafCounter: Int = first_tree.getLeafCounter + 2

        // Update the Local Trees.
        for ((_, local_tree) <- trees) {

          // Get the local leaf to be replaced by a new Internal Node.
          val local_leaf: LeafNode = local_tree.getLeafMap(leafID)

          // Update the auxiliary data of the local tree.
          local_tree.setHeight(new_height)
          local_tree.setNumberOfInternalNodes(new_numberOfInternalNodes)
          local_tree.setNumberOfLeaves(new_numberOfLeaves)
          local_tree.setActiveLeaves(new_activeLeaves)
          local_tree.setLeafCounter(new_LeafCounter)

          // Create the new Internal Node along with its two child leaves and update the local_tree.
          val intNode = new InternalNode(new TestAttribute(bestSplit._1, bestSplit._2), new_leaf.height)
          intNode.leftChild = LeafNode(-1, isLeft = true, new_leaf.stats.createStats.setStats(bestSplit._4.head), intNode, new_leaf.height + 1)
          intNode.rightChild = LeafNode(-1, isLeft = false, new_leaf.stats.createStats.setStats(bestSplit._4.tail.head), intNode, new_leaf.height + 1)
          intNode.leftChild.asInstanceOf[LeafNode].id = new_LeafCounter - 1
          intNode.rightChild.asInstanceOf[LeafNode].id = new_LeafCounter
          if (local_leaf.isLeft)
            local_leaf.parent.leftChild = intNode
          else
            local_leaf.parent.rightChild = intNode

          // Update the local LeafMap.
          local_tree.getLeafMap.remove(local_leaf.id + "_" + local_leaf.height)
          local_tree.getLeafMap.put(
            intNode.leftChild.asInstanceOf[LeafNode].id + "_" + intNode.leftChild.asInstanceOf[LeafNode].height,
            intNode.leftChild.asInstanceOf[LeafNode]
          )
          local_tree.getLeafMap.put(
            intNode.rightChild.asInstanceOf[LeafNode].id + "_" + intNode.rightChild.asInstanceOf[LeafNode].height,
            intNode.rightChild.asInstanceOf[LeafNode]
          )
        }

        leafMap.remove(leafID)
        leaf_counters.remove(leafID)
        val key1: String = (trees.head._2.getLeafCounter - 1) + "_" + (new_leaf.height + 1)
        val key2: String = trees.head._2.getLeafCounter + "_" + (new_leaf.height + 1)
        leafMap.put(key1, trees.head._2.getLeafMap(key1).generateNode.asInstanceOf[LeafNode])
        leafMap.put(key2, trees.head._2.getLeafMap(key2).generateNode.asInstanceOf[LeafNode])

      } else leaf_counters(leafID) = 0

//      println("Synchronized leaf " + leafID + ".")

    }

    // Final synchronizations.
    numberOfDataPointsSeen += (
      for ((_, local_tree) <- trees) yield local_tree.getNumberOfDataPointsSeen - numberOfDataPointsSeen
      ).sum
      for ((_, local_tree) <- trees) yield
        local_tree.setNumberOfDataPointsSeen(numberOfDataPointsSeen)

    // Check for terminated workers.
    syncClass.terminated_workers.synchronized {
      if (syncClass.terminated_workers.nonEmpty) {
        for (terminated_worker <- syncClass.terminated_workers) {
          if(trees.contains(terminated_worker))
            terminateWorker(terminated_worker)
        }
      }
      syncClass.synchronize = false // End synchronization.
    }

   // Resume training.
//    println("Number of workers " + workers.size)
    for (_ <- 0 until sync_barrier.getParties - sync_barrier.getNumberWaiting)
      new Thread(new PseudoBarrier(sync_barrier)).start()
  }

  override def run(): Unit = {

    println("Synchronizer started running ...")

    try {
      while (true) {

        // Suspending the Synchronizer to release CPU time. It will be enabled only when synchronization is needed.
        suspend()

        // Synchronize the Trees.
        synchronizeTrees()

      }
    } catch {
      case _: InterruptedException => println("The coordinator Thread has been terminated.")
    }

  }

}
