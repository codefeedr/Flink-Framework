

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

package org.codefeedr.core.engine.query

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.scala._
import org.codefeedr.core.library.internal.{RecordTransformer, SubjectTypeFactory}
import org.codefeedr.model.{ActionType, _}
import org.codefeedr.core.engine.query
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

case class SomeJoinTestObject(id: Int, name: String)
case class SomeJoinTestMessage(id: Int, objectId: Int, message: String, dataBag: Array[Byte])

/**
  * Created by Niels on 02/08/2017.
  */
class JoinQueryComposerSpec
    extends AsyncFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {

  val objectType: SubjectType = SubjectTypeFactory.getSubjectType[SomeJoinTestObject](Array("id"))
  val messageType: SubjectType = SubjectTypeFactory.getSubjectType[SomeJoinTestMessage](Array("id"))

  val objectTransformer = new RecordTransformer[SomeJoinTestObject](objectType)
  val messageTransformer = new RecordTransformer[SomeJoinTestMessage](messageType)

  "A buildComposedType method" should "create a new type with the given alias" in {
    val mergedType = JoinQueryComposer.buildComposedType(objectType,
                                                         messageType,
                                                         Array("name"),
                                                         Array("message"),
                                                         "testObjectMessages")
    assert(mergedType.name == "testObjectMessages")
  }

  "A buildComposedType method" should "merge properties from left and right" in {
    val mergedType = JoinQueryComposer.buildComposedType(objectType,
                                                         messageType,
                                                         Array("name"),
                                                         Array("message"),
                                                         "testObjectMessages")

    assert(mergedType.properties.length == 2)
    assert(mergedType.properties.map(o => o.name).contains("name"))
    assert(mergedType.properties.map(o => o.name).contains("message"))

  }

  "A buildComposedType method" should "copy type information from source types" in {
    val mergedType = JoinQueryComposer.buildComposedType(objectType,
                                                         messageType,
                                                         Array("name"),
                                                         Array("id", "message", "dataBag"),
                                                         "testObjectMessages")
    assert(mergedType.properties.length == 4)
    assert(
      mergedType.properties.filter(o => o.name == "name").head.propertyType.canEqual(createTypeInformation[String]))
    assert(
      mergedType.properties.filter(o => o.name == "id").head.propertyType.canEqual(createTypeInformation[Int]))
    assert(
      mergedType.properties
        .filter(o => o.name == "message")
        .head
        .propertyType == createTypeInformation[String])
    assert(
      mergedType.properties.filter(o => o.name == "dataBag").head.propertyType.canEqual(createTypeInformation[Array[Byte]]))
  }

  "A buildComposedType method" should "throw an exception when a name occurs twice in the select" in {
    assertThrows[Exception](
      JoinQueryComposer.buildComposedType(objectType,
                                          messageType,
                                          Array("id", "name"),
                                          Array("id", "message", "dataBag"),
                                          "testObjectMessages"))
  }

  "A PartialKeyFunction" should "Produce equal key when the key values are equal" in {
    val keyFunction =
      JoinQueryComposer.buildPartialKeyFunction(Array("objectId", "message"), messageType)
    val m1 =
      TrailedRecord(messageTransformer.bag(SomeJoinTestMessage(1, 1, "a message", Array[Byte]()),
                                           ActionType.Add),
                    Source(Array[Byte](), Array[Byte]()))
    val m2 =
      TrailedRecord(messageTransformer.bag(SomeJoinTestMessage(2, 1, "a message", Array[Byte]()),
                                           ActionType.Add),
                    Source(Array[Byte](), Array[Byte]()))
    assert(keyFunction(m1).equals(keyFunction(m2)))
  }
  "A PartialKeyFunction" should "Produce different keys when the key values are not equal" in {
    val keyFunction =
      JoinQueryComposer.buildPartialKeyFunction(Array("objectId", "message"), messageType)
    val m1 =
      TrailedRecord(messageTransformer.bag(SomeJoinTestMessage(1, 1, "a message", Array[Byte]()),
                                           ActionType.Add),
                    Source(Array[Byte](), Array[Byte]()))
    val m2 =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 1, "another message", Array[Byte]()),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte]()))
    assert(!keyFunction(m1).equals(keyFunction(m2)))
  }

  "A MergeFunction" should "map properties from two types into a single type based on given fieldnames, and compose the source trail" in {

    val mergedType: SubjectType =
      JoinQueryComposer.buildComposedType(objectType,
                                          messageType,
                                          Array("name"),
                                          Array("id", "message", "dataBag"),
                                          "testObjectMessages")

    val mergeFn: (TrailedRecord, TrailedRecord, ActionType.Value) => TrailedRecord =
      JoinQueryComposer.buildMergeFunction(objectType,
                                           messageType,
                                           mergedType,
                                           Array("name"),
                                           Array("id", "message", "dataBag"),
                                           Array[Byte](12.toByte))

    val o = TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "object 1"), ActionType.Add),
                          Source(Array[Byte](), Array[Byte](10.toByte)))
    val m =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "a message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))

    val merged = mergeFn(o, m, ActionType.Add)
    assert(merged.field(0).asInstanceOf[String].equals("object 1"))
    assert(merged.field(1).asInstanceOf[Int] == 2)
    assert(merged.field(2).asInstanceOf[String].equals("a message"))
    assert(merged.field(3).asInstanceOf[Array[Byte]](0) == 4.toByte)
    assert(merged.action == ActionType.Add)
    assert(merged.trail.isInstanceOf[ComposedSource])
    assert(merged.trail.asInstanceOf[ComposedSource].SourceId(0) == 12.toByte)
    assert(
      merged.trail
        .asInstanceOf[ComposedSource]
        .pointers(0)
        .asInstanceOf[Source]
        .Key(0) == 10.toByte)
    assert(
      merged.trail
        .asInstanceOf[ComposedSource]
        .pointers(1)
        .asInstanceOf[Source]
        .Key(0) == 11.toByte)
  }

  val mergedType: SubjectType =
    JoinQueryComposer.buildComposedType(objectType,
                                        messageType,
                                        Array("name"),
                                        Array("id", "message", "dataBag"),
                                        "testObjectMessages")

  val mergeFn: (TrailedRecord, TrailedRecord, ActionType.Value) => TrailedRecord =
    JoinQueryComposer.buildMergeFunction(objectType,
                                         messageType,
                                         mergedType,
                                         Array("name"),
                                         Array("id", "message", "dataBag"),
                                         Array[Byte](12.toByte))

  "A InnerJoinFunction" should "not emit values until a join partner has been found" in {
    val state = None: Option[JoinState]
    val joinFunction = JoinQueryComposer.mapSideInnerJoin(mergeFn) _
    val o = TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "object 1"), ActionType.Add),
                          Source(Array[Byte](), Array[Byte](10.toByte)))

    val m =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "a message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))

    val (r1, state1) = joinFunction(query.Left(o), state)
    assert(r1.isEmpty)
    assert(state1.get.left.values.toArray.contains(o))

    val (r2, state2) = joinFunction(query.Right(m), state)
    assert(r2.isEmpty)
    assert(state2.get.right.values.toArray.contains(m))
  }

  "A InnerJoinFunction" should "emit a value when a join partner has been found" in {
    val state = None: Option[JoinState]
    val joinFunction = JoinQueryComposer.mapSideInnerJoin(mergeFn) _
    val o = TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "object 1"), ActionType.Add),
                          Source(Array[Byte](), Array[Byte](10.toByte)))

    val m =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "a message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))

    val (r1, state1) = joinFunction(query.Left(o), state)
    assert(r1.isEmpty)
    assert(state1.get.left.values.toArray.contains(o))

    val (r2, state2) = joinFunction(query.Right(m), state1)
    assert(r2.size == 1)
    assert(state2.get.right.values.toArray.contains(m))
    assert(state2.get.left.values.toArray.contains(o))
  }

  "A InnerJoinFunction" should "emit a cross join when multiple join partners are found in" in {
    val state = None: Option[JoinState]
    val joinFunction = JoinQueryComposer.mapSideInnerJoin(mergeFn) _
    val o = TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "object 1"), ActionType.Add),
                          Source(Array[Byte](), Array[Byte](10.toByte)))

    val o2 =
      TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "Another object"), ActionType.Add),
                    Source(Array[Byte](), Array[Byte](10.toByte)))

    val m =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "a message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))
    val m2 =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "another message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))

    val (r1, state1) = joinFunction(query.Left(o), state)
    val (r2, state2) = joinFunction(query.Right(m), state1)
    val (r3, state3) = joinFunction(query.Left(o2), state2)
    assert(r3.size == 1)
    assert(state3.get.right.values.toArray.contains(m))
    assert(state3.get.left.values.toArray.contains(o))
    assert(state3.get.left.values.toArray.contains(o2))
    val (r4, state4) = joinFunction(query.Right(m2), state3)
    assert(r4.size == 2)
    assert(state4.get.right.values.toArray.contains(m))
    assert(state4.get.right.values.toArray.contains(m2))
    assert(state4.get.left.values.toArray.contains(o))
    assert(state4.get.left.values.toArray.contains(o2))
  }
  "A InnerJoinFunction" should "not add an element to its state twice if it occurs twice" in {
    val state = None: Option[JoinState]
    val joinFunction = JoinQueryComposer.mapSideInnerJoin(mergeFn) _
    val o = TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "object 1"), ActionType.Add),
                          Source(Array[Byte](), Array[Byte](10.toByte)))

    val o2 =
      TrailedRecord(objectTransformer.bag(SomeJoinTestObject(1, "Another object"), ActionType.Add),
                    Source(Array[Byte](), Array[Byte](10.toByte)))

    val m =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "a message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))
    val m2 =
      TrailedRecord(
        messageTransformer.bag(SomeJoinTestMessage(2, 3, "another message", Array[Byte](4.toByte)),
                               ActionType.Add),
        Source(Array[Byte](), Array[Byte](11.toByte)))

    val (r1, state1) = joinFunction(query.Left(o), state)
    val (r2, state2) = joinFunction(query.Right(m), state1)
    val (r3, state3) = joinFunction(query.Left(o), state2)
    assert(r3.size == 1)
    assert(state3.get.right.values.toArray.count(l => l.equals(m)) == 1)
    assert(state3.get.left.values.toArray.count(l => l.equals(o)) == 1)
    val (r4, state4) = joinFunction(query.Right(m), state3)
    assert(r4.size == 1)
    assert(state3.get.right.values.toArray.count(l => l.equals(m)) == 1)
    assert(state3.get.left.values.toArray.count(l => l.equals(o)) == 1)
  }
}
