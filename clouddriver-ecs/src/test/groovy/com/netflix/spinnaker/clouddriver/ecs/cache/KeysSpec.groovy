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

package com.netflix.spinnaker.clouddriver.ecs.cache

import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.*
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.SEPARATOR

class KeysSpec extends Specification {
  @Shared
  String account = 'test-account'
  @Shared
  String region = 'us-east-1'

  def 'should parse a given key properly'() {
    given:
    Keys keys = new Keys()

    expect:
    keys.parseKey(ID + SEPARATOR + namespace + SEPARATOR + account + SEPARATOR + region + SEPARATOR + identifier) == parsedKey

    where:

    namespace              | identifier                                                                                        | parsedKey
    TASKS.ns               | '1dc5c17a-422b-4dc4-b493-371970c6c4d6'                                                            | [provider: ID, type: TASKS.ns, account: account, region: region, taskId: identifier]
    SERVICES.ns            | 'test-stack-detail-v001'                                                                          | [provider: ID, type: SERVICES.ns, account: account, region: region, taskId: identifier]
    ECS_CLUSTERS.ns        | 'test-cluster-1'                                                                                  | [provider: ID, type: ECS_CLUSTERS.ns, account: account, region: region, taskId: identifier]
    CONTAINER_INSTANCES.ns | 'arn:aws:ecs:' + region + ':012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98' | [provider: ID, type: CONTAINER_INSTANCES.ns, account: account, region: region, taskId: identifier]
    TASK_DEFINITIONS.ns    | 'arn:aws:ecs:' + region + ':012345678910:task-definition/hello_world:10'                          | [provider: ID, type: TASK_DEFINITIONS.ns, account: account, region: region, taskId: identifier]

  }

  def 'should generate the proper task key'(){
    //expect:
    //Keys.getTaskKey() ==

  }
}
