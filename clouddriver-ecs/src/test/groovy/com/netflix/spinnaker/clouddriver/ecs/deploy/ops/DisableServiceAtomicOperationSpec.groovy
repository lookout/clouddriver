/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription
import spock.lang.Specification

class DisableServiceAtomicOperationSpec extends Specification {

  void 'should execute the operation'() {
    given:
    def description = new DisableServiceDescription(
      serverGroupName: "test-server-group"
    )
    def operation = new DisableServiceAtomicOperation(description)

    when:
    operation.operate([])

    then:
    //TODO: Implement a proper `then`
    true
  }
}
