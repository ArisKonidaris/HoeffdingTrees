package ht.dataBuffers.removeStrategy

import ht.dataBuffers.DataSet

import scala.util.Random

/**
 * A trategy that remove a datum from a data set at random.
 *
 * @tparam T The type of data that are buffered.
 */
case class RandomRemoveStrategy[T]() extends RemoveStrategy[T] {
  override def removeTuple(dataSet: DataSet[T]): Option[T] = dataSet.remove(Random.nextInt(dataSet.length))
}
