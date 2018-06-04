

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.codefeedr.core.library.internal.kafka.sink

import org.apache.flink.streaming.api.operators.StreamingRuntimeContext
import org.codefeedr.core.LibraryServiceSpec
import org.codefeedr.core.library.internal.SubjectTypeFactory
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.async.Async.{async, await}
import scala.concurrent.Await
import scala.concurrent.duration._

case class TestKafkaSinkSubject(prop1: String)

/**
  * Test for [[KafkaGenericSink]]
  */
class KafkaSinkIntegrationSpec extends LibraryServiceSpec with BeforeAndAfterEach with BeforeAndAfterAll with MockitoSugar {


  val testSubjectName = "TestKafkaSinkSubject"



  "A KafkaSink" should "Register and remove itself in the SubjectLibrary" in async {
    val sinkId = "testSink"

    val subject = SubjectTypeFactory.getSubjectType[TestKafkaSinkSubject]
    val subjectNode = await(subjectFactory.create(subject))

    val job = subjectLibrary.getJob("testJob")
    //,subjectFactory.getTransformer[TestKafkaSinkSubject](subject)
    val sink = new KafkaGenericSink[TestKafkaSinkSubject](subjectNode,job,kafkaProducerFactory,epochStateManager,sinkId)
    val sinkNode = subjectNode.getSinks().getChild(sinkId)
    val runtimeContext = mock[StreamingRuntimeContext]
    sink.setRuntimeContext(runtimeContext)


    assert(!await(sinkNode.exists()))

    sink.open(null)


    assert(await(sinkNode.exists()))
    assert(await(subjectNode.getSinks().getState()))
    sink.close()

    assert(!await(subjectNode.getSinks().getState()))
  }

  override def afterEach(): Unit = {
    Await.ready(zkClient.deleteRecursive("/"), Duration(1, SECONDS))
    Await.ready(subjectLibrary.initialize(),Duration(1, SECONDS))
  }
}