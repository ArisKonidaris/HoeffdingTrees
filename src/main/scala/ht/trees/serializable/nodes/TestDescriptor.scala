package ht.trees.serializable.nodes

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * A serializable descriptor of an attribute test.
 */
class TestDescriptor(var id: Int, var value: Double) extends java.io.Serializable {

  def setId(id: Int): Unit = this.id = id

  def getId: Int = id

  def setValue(value: Double): Unit = this.value = value

  def getValue: Double = value

  override def toString: String = {
    try {
      toJsonString
    } catch {
      case _: JsonProcessingException => "Non printable " + this.getClass.getName
    }
  }

  def toJsonString: String = {
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this)
  }

}
