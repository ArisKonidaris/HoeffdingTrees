package ht.dataBuffers.removeStrategy

import ht.dataBuffers.DataSet

import scala.collection.mutable.ListBuffer

/**
 * A basic trait of a strategy for removing data from a data set.
 *
 * @tparam T The type of data that are buffered.
 */
trait RemoveStrategy[T] extends java.io.Serializable {

  def removeTuple(dataSet: DataSet[T]): Option[T]

  def remove(dataSet: DataSet[T]): ListBuffer[T] = {
    assert(dataSet.length > dataSet.max_size)
    val extraData = new ListBuffer[T]()
    while (dataSet.length > dataSet.max_size)
      extraData += removeTuple(dataSet).get
    extraData
  }

}