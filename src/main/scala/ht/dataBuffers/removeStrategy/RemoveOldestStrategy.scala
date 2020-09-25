package ht.dataBuffers.removeStrategy

import ht.dataBuffers.DataSet

/**
 * A strategy that removes the oldest datum from a data set.
 *
 * @tparam T The type of data that are buffered.
 */
case class RemoveOldestStrategy[T]() extends RemoveStrategy[T] {
  override def removeTuple(dataSet: DataSet[T]): Option[T] = dataSet.pop
}
