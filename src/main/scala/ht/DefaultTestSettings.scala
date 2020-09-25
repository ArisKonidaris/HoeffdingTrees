package ht

/**
 * These are the default test settings. If the user does not provide all the necessary command line arguments,
 * then these will we used.
 */
object DefaultTestSettings {

  val default_filepath: String = "/home/aris/IdeaProjects/DataStream/LinearClassificationTrainingSet30_10^6.txt"
  val default_topic_name: String = "trainingData"
  val default_parallelism: Int = 1
  val default_replication_factor: Int = 1
  val default_n_min: Int = 200
  val default_tau: Double = 0.05
  val default_delta: Double = 1.0E-7D

}
