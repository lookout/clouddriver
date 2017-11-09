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

import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.*
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.SEPARATOR

class KeysSpec extends Specification {

  def buildKey(String namespace, String account, String region, String identifier) {
    return ID + SEPARATOR + namespace + SEPARATOR + account + SEPARATOR + region + SEPARATOR + identifier
  }

  def 'should parse a given key properly'() {
    given:
    Keys keys = new Keys()

    expect:
    keys.parseKey(buildKey(namespace, account, region, identifier as String)) == parsedKey

    where:

    account          | region      | namespace              | identifier                                                                                        | parsedKey
    'test-account-1' | 'us-west-1' | TASKS.ns               | '1dc5c17a-422b-4dc4-b493-371970c6c4d6'                                                            | [provider: ID, type: TASKS.ns, account: account, region: region, taskId: identifier]
    'test-account-2' | 'us-west-2' | SERVICES.ns            | 'test-stack-detail-v001'                                                                          | [provider: ID, type: SERVICES.ns, account: account, region: region, serviceName: identifier]
    'test-account-3' | 'us-west-3' | ECS_CLUSTERS.ns        | 'test-cluster-1'                                                                                  | [provider: ID, type: ECS_CLUSTERS.ns, account: account, region: region, clusterName: identifier]
    'test-account-4' | 'us-west-4' | CONTAINER_INSTANCES.ns | 'arn:aws:ecs:' + region + ':012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98' | [provider: ID, type: CONTAINER_INSTANCES.ns, account: account, region: region, containerInstanceArn: identifier]
    'test-account-5' | 'us-west-5' | TASK_DEFINITIONS.ns    | 'arn:aws:ecs:' + region + ':012345678910:task-definition/hello_world:10'                          | [provider: ID, type: TASK_DEFINITIONS.ns, account: account, region: region, taskDefinitionArn: identifier]

  }

  def 'should generate the proper task key'() {
    expect:
    Keys.getTaskKey(account, region, taskId) == key

    where:
    region      | account          | taskId                                 | key
    'us-west-1' | 'test-account-1' | '1dc5c17a-422b-4dc4-b493-371970c6c4d6' | buildKey(TASKS.ns, account, region, taskId)
    'us-west-2' | 'test-account-2' | 'deadbeef-422b-4dc4-b493-371970c6c4d6' | buildKey(TASKS.ns, account, region, taskId)
  }

  def 'should generate the proper service key'() {
    expect:
    Keys.getServiceKey(account, region, serviceName) == key

    where:
    region      | account          | serviceName                            | key
    'us-west-1' | 'test-account-1' | '1dc5c17a-422b-4dc4-b493-371970c6c4d6' | buildKey(SERVICES.ns, account, region, serviceName)
    'us-west-2' | 'test-account-2' | 'deadbeef-422b-4dc4-b493-371970c6c4d6' | buildKey(SERVICES.ns, account, region, serviceName)
  }

  def 'should generate the proper cluster key'() {
    expect:
    Keys.getClusterKey(account, region, clusterName) == key

    where:
    region      | account          | clusterName      | key
    'us-west-1' | 'test-account-1' | 'test-cluster-1' | buildKey(ECS_CLUSTERS.ns, account, region, clusterName)
    'us-west-2' | 'test-account-2' | 'test-cluster-2' | buildKey(ECS_CLUSTERS.ns, account, region, clusterName)
  }
}
