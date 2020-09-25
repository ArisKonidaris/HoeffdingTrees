package ht.dataBuffers

import ht.dataBuffers.removeStrategy.{RemoveOldestStrategy, RemoveStrategy}

import scala.collection.mutable.ListBuffer

/**
 * A data set class with a maximum capacity. If the maximum capacity of the buffer is exceeded then the oldest
 * datum will be removed from the buffer.
 *
 * @param data_buffer The buffer of data.
 * @param max_size    The maximum size of the data buffer.
 * @tparam T The type of data to be buffered.
 */
case class DataSet[T](var data_buffer: ListBuffer[T], var max_size: Int) extends DataBuffer[T] {

  def this() = this(ListBuffer[T](), 500000)

  def this(training_set: ListBuffer[T]) = this(training_set, 500000)

  def this(max_size: Int) = this(ListBuffer[T](), max_size)

  /** This is the removal strategy of data from the buffer. */
  var remove_strategy: RemoveStrategy[T] = RemoveOldestStrategy[T]()

  override def isEmpty: Boolean = data_buffer.isEmpty

  override def append(data: T): Option[T] = {
    data_buffer += data
    overflowCheck()
  }

  override def insert(index: Int, data: T): Option[T] = {
    data_buffer.insert(index, data)
    overflowCheck()
  }

  override def length: Int = data_buffer.length

  override def clear(): Unit = {
    data_buffer.clear()
    max_size = 500000
  }

  override def pop: Option[T] = remove(0)

  override def remove(index: Int): Option[T] = {
    if (data_buffer.length > index) Some(data_buffer.remove(index)) else None
  }

  def overflowCheck(): Option[T] = {
    if (data_buffer.length > max_size)
      Some(remove_strategy.removeTuple(this).get)
    else
      None
  }

  /////////////////////////////////////////// Getters ////////////////////////////////////////////////

  def getDataBuffer: ListBuffer[T] = data_buffer

  def getMaxSize: Int = max_size

  def getRemoveStrategy: RemoveStrategy[T] = remove_strategy

  /////////////////////////////////////////// Setters ////////////////////////////////////////////////

  def setDataBuffer(data_set: ListBuffer[T]): Unit = this.data_buffer = data_set

  def setMaxSize(max_size: Int): Unit = this.max_size = max_size

  def setRemoveStrategy(remove_strategy: RemoveStrategy[T]): Unit = this.remove_strategy = remove_strategy

}
