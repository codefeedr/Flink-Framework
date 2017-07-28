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

package org.codefeedr.Library

import org.apache.flink.api.common.typeinfo.TypeInformation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.{universe => ru}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.codefeedr.Library.Internal.Kafka.{KafkaController, KafkaSink, KafkaSource}

import scala.reflect.ClassTag

/**
  * ThreadSafe
  * Created by Niels on 18/07/2017.
  */
object SubjectFactory {
  def GetSink[TData: ru.TypeTag: ClassTag]: Future[SinkFunction[TData]] = {
    SubjectLibrary
      .GetType[TData]()
      .flatMap(o =>
        KafkaController.GuaranteeTopic(s"${o.name}_${o.uuid}").map(_ => new KafkaSink[TData](o)))
  }

  def GetSource[TData: ru.TypeTag: TypeInformation: ClassTag]: Future[SourceFunction[TData]] = {
    SubjectLibrary.GetType[TData]().map(o => new KafkaSource[TData](o))
  }
}
