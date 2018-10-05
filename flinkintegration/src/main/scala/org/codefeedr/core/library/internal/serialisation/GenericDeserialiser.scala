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

package org.codefeedr.core.library.internal.serialisation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.core.memory.DataInputDeserializer

import scala.reflect.ClassTag

class GenericDeserialiser[T: ClassTag](implicit val ec: ExecutionConfig) {
  @transient private lazy val inputDeserializer = new DataInputDeserializer()
  @transient private lazy val ct = implicitly[ClassTag[T]]
  @transient implicit lazy val ti: TypeInformation[T] =
    TypeInformation.of(ct.runtimeClass.asInstanceOf[Class[T]])
  @transient private lazy val serializer: TypeSerializer[T] = ti.createSerializer(ec)

  private val deserializeInternal =
    /**
      * Prevent double serialization for the cases where bytearrays are directly sent to kafka
      */
    if (classOf[Array[Byte]].isAssignableFrom(ct.getClass)) { data: Array[Byte] =>
      data.asInstanceOf[T]
    } else { data: Array[Byte] =>
      deserialize(data)
    }

  /**
    * Serialize the element to byte array
    *
    * @param data the data to serialize
    * @return the data as bytearray
    */
  def deserialize(data: Array[Byte]): T = {
    {
      inputDeserializer.setBuffer(data, 0, data.length)
      serializer.deserialize(inputDeserializer)
    }
  }
}

/**
  * Deserialise an object serialised by the GenericSerialiser
  */
object GenericDeserialiser {
  def apply[TData: ClassTag](data: Array[Byte])(implicit executionConfig: ExecutionConfig): TData = {
    new GenericDeserialiser[TData]()(implicitly[ClassTag[TData]], executionConfig)
      .deserialize(data)
  }
}
