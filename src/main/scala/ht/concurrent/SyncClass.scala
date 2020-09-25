package ht.concurrent

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * A class that contains all the synchronizing variables that the concurrent implementation needs.
 *
 * @param synchronize A flag determining if a synchronization of the trees needs to happen.
 * @param pending The number of Threads that are waiting to execute. This is used for synchronizing the execution the Hub.
 * @param sync A Queue containing the leaves that need to be synchronized.
 * @param terminated_workers A list of terminated workers.
 */
case class SyncClass(var synchronize: Boolean = false,
                     var pending: Int = 0,
                     sync : mutable.Queue[String] = mutable.Queue[String](),
                     terminated_workers: ListBuffer[Int] = ListBuffer[Int]())
