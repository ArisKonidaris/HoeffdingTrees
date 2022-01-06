package ht

/**
 * These are the default test settings. If the user does not provide all the necessary command line arguments,
 * then these will we used.
 */
object DefaultTestSettings {

  val defaultFilepath: String = "/home/aris/IdeaProjects/DataStream/DummyDataSet_43f2c_mil_e1.txt"
  val defaultTopicName: String = "trainingData"
  val defaultParallelism: Int = 1 // 7
  val defaultReplicationFactor: Int = 1
  val defaultNMin: Int = 200 // 400
  val defaultTau: Double = 0.05
  val defaultDelta: Double = 1.0E-7D

  val storeTreeToFile: Boolean = true
  val storeFilepath: String = "/home/aris/Desktop/storedTree.txt"

}
