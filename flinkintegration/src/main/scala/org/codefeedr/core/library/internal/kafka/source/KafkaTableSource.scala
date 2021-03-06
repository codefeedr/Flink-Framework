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

package org.codefeedr.core.library.internal.kafka.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.table.api.TableSchema
import org.apache.flink.table.sources.StreamTableSource
import org.apache.flink.types.Row
import org.codefeedr.core.library.metastore.SubjectNode
import org.codefeedr.core.library.{TypeInformationServices}
import org.codefeedr.model.SubjectType

/**
  * Kafka source that exposes codefeedr subjects to flink's table api
  * @param subjectNode
  */
class KafkaTableSource(subjectNode: SubjectNode, sourceId: String, source: SourceFunction[Row])
    extends StreamTableSource[Row] {
  //Map the TrailedRecord provided by the source to a row
  override def getDataStream(execEnv: StreamExecutionEnvironment): DataStream[Row] =
    execEnv
      .addSource(source)

  /**
    * Use subjectType to get row type information
    * @return flinks rowtypeinformation
    */
  override def getReturnType: TypeInformation[Row] =
    TypeInformationServices.getEnrichedRowTypeInfo(subjectNode.getDataSync().get)

  override def getTableSchema: TableSchema = {
    TableSchema.fromTypeInfo(getReturnType)
  }
}
