package ht.trees.nodes

import ht.math.Vector
import ht.trees.serializable.nodes.{LeafNodeDescriptor, NodeDescriptor}
import ht.trees.stats.{NumericalStatistics, Statistics}

import scala.collection.mutable

/**
 * A class representing a leaf node in a Hoeffding tree.
 *
 * @param id     The id of the current leaf node. The unique id of each leaf is generated by this id anf the height.
 * @param isLeft A flag determining if this leaf is a left or a right child of the parent internal node.
 * @param stats  The sufficient statistics data structure.
 * @param parent The parent internal node.
 * @param height The height of the Hoeffding Tree that this leaf is on.
 */
case class LeafNode(var id: Int,
                    var isLeft: Boolean,
                    var stats: Statistics = NumericalStatistics(10),
                    var parent: InternalNode,
                    override var height: Int)
  extends Node {

  var n: Long = 0
  var errors: Long = 0

  def this(isLeft: Boolean, stats: Statistics, height: Int) = this(-1, isLeft, stats, null, height)

  def this(id: Int, isLeft: Boolean, stats: Statistics, height: Int) = this(id, isLeft, stats, null, height)

  //////////////////////////////////////////////////// Getters /////////////////////////////////////////////////////////

  override def getNodeSize: Int = 25 + stats.getSize

  override def getSize: Int = getNodeSize

  //////////////////////////////////////////////////// Methods /////////////////////////////////////////////////////////

  override def predict(point: Vector, method: String = "MajorityVote"): (Int, Double) = {
    if (n == 0)
      (stats.prediction, Math.max(0.0, stats.classDistribution.getOrElse(stats.prediction, -1D) / stats.n_l))
    else
      stats.predict(point, method)
  }

  override def deactivate(): Unit = {
    stats.deactivate()
    n = 0L
  }

  override def activate(): Unit = stats.activate()

  override def serialize: NodeDescriptor = {
    LeafNodeDescriptor(id, isLeft, stats.serialize, height)
  }

  override def filterNode(point: Vector): LeafNode = this

  override def toString: String = {
    val tabs = if (height > 1) (for (_ <- 1 until height) yield "\t").reduce(_ + _) else ""
    tabs + "LeafNode: " + stats.classDistribution.toString()
  }

  override def equals(o: Any): Boolean = {
    o match {
      case leaf: LeafNode => if (leaf.id == id && height == leaf.height) true else false
      case _ => false
    }
  }

  override def createLeafMap(leafMap: mutable.Map[String, LeafNode]): Unit = leafMap.put(id + "_" + height, this)

  override def generateNode: Node = LeafNode(id, isLeft, stats.generateStats, null, height)

}

object LeafNode {
  def deserialize(descriptor: LeafNodeDescriptor): LeafNode = {
    new LeafNode(
      id = descriptor.getId,
      isLeft = descriptor.getIsLeft,
      stats = Statistics.deserializeStats(descriptor.getStats),
      height = descriptor.getHeight
    )
  }
}
