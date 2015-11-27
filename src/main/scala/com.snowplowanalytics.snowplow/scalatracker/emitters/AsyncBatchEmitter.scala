/*
 * Copyright (c) 2015 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.scalatracker.emitters

// Java
import java.util.concurrent.LinkedBlockingQueue

// Scala
import scala.collection.mutable.ListBuffer


object AsyncBatchEmitter {
  // Avoid starting thread in constructor
  /**
   * Start async emitter with batch event payload
   *
   * @param host collector host
   * @param port collector port
   * @param bufferSize quantity of events in batch request
   * @param https should this use the https scheme
   * @return emitter
   */
  def createAndStart(host: String, port: Int = 80, bufferSize: Int = 50, https: Boolean = false): AsyncBatchEmitter = {
    val emitter = new AsyncBatchEmitter(host, port, bufferSize, https = https)
    emitter.startWorker()
    emitter
  }
}

/**
 * Asynchronous batch emitter
 * Store events in buffer and send them with POST request when buffer exceeds `bufferSize`
 *
 * @param host collector host
 * @param port collector port
 * @param bufferSize quantity of events in a batch request
 * @param https should this use the https scheme
 */
class AsyncBatchEmitter private(host: String, port: Int, bufferSize: Int, https: Boolean = false) extends TEmitter {

  val queue = new LinkedBlockingQueue[Seq[Map[String, String]]]()

  // 2 seconds timeout after 1st failed request
  val initialBackoffPeriod = 2000

  private var buffer = ListBuffer[Map[String, String]]()

  // Start consumer thread synchronously trying to send events to collector
  val worker = new Thread {
    override def run {
      while (true) {
        val batch = queue.take()
        RequestUtils.retryPostUntilSuccessful(host, batch, port, initialBackoffPeriod, https = https)
      }
    }
  }

  worker.setDaemon(true)

  /**
   * Method called to send an event from the tracker to the emitter
   * Adds the event to the queue
   *
   * @param event Fully assembled event
   */
  def input(event: Map[String, String]): Unit = {
    buffer.append(event)
    if (buffer.size >= bufferSize) {
      queue.put(buffer)
      buffer = ListBuffer[Map[String, String]]()
    }
  }

  private def startWorker(): Unit = {
    worker.start()
  }
}
