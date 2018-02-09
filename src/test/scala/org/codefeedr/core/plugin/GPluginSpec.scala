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
package org.codefeedr.core.plugin

import com.typesafe.scalalogging.LazyLogging
import org.codefeedr.core.LibraryServiceSpec
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatest.tagobjects.Slow
import async.Async._

class GPluginSpec extends LibraryServiceSpec with Matchers with LazyLogging {

  "" should "" taggedAs (Slow) in async {
    val plugin = new GPlugin()

    await(plugin.run())

    assert(1 == 1)
  }

}
