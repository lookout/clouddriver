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

package com.netflix.spinnaker.clouddriver.ecs.services

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ecs.model.Container
import com.amazonaws.services.ecs.model.NetworkBinding
import com.netflix.spinnaker.clouddriver.ecs.cache.client.*
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import spock.lang.Specification
import spock.lang.Subject

class ContainerInformationServiceSpec extends Specification {
  def ecsCredentialsConfig = Mock(ECSCredentialsConfig)
  def taskCacheClient = Mock(TaskCacheClient)
  def serviceCacheClient = Mock(ServiceCacheClient)
  def taskHealthCacheClient = Mock(TaskHealthCacheClient)
  def ecsInstanceCacheClient = Mock(EcsInstanceCacheClient)
  def containerInstanceCacheClient = Mock(ContainerInstanceCacheClient)

  @Subject
  def service = new ContainerInformationService(ecsCredentialsConfig,
    taskCacheClient,
    serviceCacheClient,
    taskHealthCacheClient,
    ecsInstanceCacheClient,
    containerInstanceCacheClient)

  def 'should return a proper private address for a task'() {
    given:
    def account = 'test-account'
    def region = 'us-west-1'
    def containerInstanceArn = 'container-instance-arn'
    def ip = '127.0.0.1'
    def port = 1337

    def ecsAccount = new ECSCredentialsConfig.Account(
      name: account,
      awsAccount: 'aws-' + account
    )

    def task = new Task(
      containerInstanceArn: containerInstanceArn,
      containers: [
        new Container(
          networkBindings: [
            new NetworkBinding(
              hostPort: port
            )
          ]
        )
      ]
    )

    def containerInstance = new ContainerInstance(
      ec2InstanceId: 'i-deadbeef'
    )

    def instance = new Instance(
      privateIpAddress: ip
    )

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsInstanceCacheClient.find(_, _, _) >> [instance]
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]

    when:
    def retrievedIp = service.getTaskPrivateAddress(account, region, task)

    then:
    retrievedIp == ip + ':' + port
  }
}
