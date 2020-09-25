package ht.concurrent

import java.util.concurrent.CyclicBarrier

/**
 * A Runnable instance that calls a CyclicBarrier. This is used for synchronization purposes
 *
 * @param sync_barrier A [[CyclicBarrier]] instance.
 */
class PseudoBarrier(val sync_barrier: CyclicBarrier) extends Runnable {
  override def run(): Unit = {
    try {
      sync_barrier.await()
    } catch {
      case _: InterruptedException => println("Terminating Synchronization barrier.")
    }
  }
}
