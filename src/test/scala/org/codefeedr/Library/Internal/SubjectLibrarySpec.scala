/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.codefeedr.Library.Internal

import org.codefeedr.Library.SubjectLibrary
import org.scalatest._

import scala.concurrent.ExecutionContextExecutor

case class TestTypeA(prop1: String)

/**
  * Created by Niels on 18/07/2017.
  */
class SubjectLibrarySpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {
  implicit override def executionContext: ExecutionContextExecutor =
    scala.concurrent.ExecutionContext.Implicits.global

  "A KafkaLibrary" should "be able to register a new type" in {
    for {
      subject <- SubjectLibrary.GetType[TestTypeA]()
    } yield assert(subject.properties.map(o => o.name).contains("prop1"))
  }

  "A KafkaLibrary" should "list all current registered types" in {
    assert(SubjectLibrary.GetSubjectNames().contains("TestTypeA"))
  }

  "A KafkaLibrary" should "be able to remove a registered type again" in {
    for {
      _ <- SubjectLibrary.UnRegisterSubject("TestTypeA")
    } yield assert(!SubjectLibrary.GetSubjectNames().contains("TestTypeA"))
  }

  "A KafkaLibrary" should "return the same subjecttype if GetType is called twice in parallel" in {
    val t1 = SubjectLibrary.GetType[TestTypeA]()
    val t2 = SubjectLibrary.GetType[TestTypeA]()
    for {
      r1 <- t1
      r2 <- t2
    } yield assert(r1.uuid == r2.uuid)
  }

  "A KafkaLibrary" should "return the same subjecttype if GetType is called twice sequential" in {
    for {
      r1 <- SubjectLibrary.GetType[TestTypeA]()
      r2 <- SubjectLibrary.GetType[TestTypeA]()
    } yield assert(r1.uuid == r2.uuid)
  }
}
