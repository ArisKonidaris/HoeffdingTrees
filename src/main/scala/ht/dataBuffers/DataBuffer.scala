package ht.dataBuffers

/**
 * A basic trait for a data buffer.
 *
 * @tparam T The type of data to be buffered.
 */
trait DataBuffer[T] extends java.io.Serializable {

  /**
   * The capacity of the buffer data structure.
   */
  def getMaxSize: Int

  /**
   * A method that returns true if the buffer is empty.
   */
  def isEmpty: Boolean

  /**
   * Returns true if the buffer is non empty.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Append an element to the buffer.
   */
  def append(tuple: T): Option[T]

  /**
   * Insert an element into the specified position.
   */
  def insert(index: Int, tuple: T): Option[T]

  /**
   * Remove the oldest element in the buffer.
   *
   * @return The removed element.
   */
  def pop: Option[T]

  /**
   * Remove an element from a specific position.
   *
   * @return The removed element.
   */
  def remove(index: Int): Option[T]

  /**
   * The length of the data buffer.
   */
  def length: Int

  /**
   * Clears the data buffer.
   */
  def clear(): Unit

}
