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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.amazonaws.services.ec2.model.Placement
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.cache.client.*
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask
import com.netflix.spinnaker.clouddriver.ecs.model.TaskDefinition
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsServerClusterProviderSpec extends Specification {
  def account = 'test-account'
  def region = 'us-west-1'
  def applicationName = 'myapp'
  def taskId = 'task-id'
  def ip = '127.0.0.0'
  def availabilityZone = "${region}a"
  def serviceName = "${applicationName}-kcats-liated-v007"
  def startedAt = System.currentTimeMillis()

  def task = new Task(
    taskId: taskId,
    taskArn: 'task-arn',
    clusterArn: 'cluster-arn',
    containerInstanceArn: 'container-instance-arn',
    group: 'service:' + serviceName,
    lastStatus: 'RUNNING',
    desiredStatus: 'RUNNING',
    startedAt: startedAt,
    containers: []
  )

  Map healthStatus = [
    instanceId: taskId,
    state     : 'RUNNING',
    type      : 'loadbalancer'
  ]

  def ec2Instance = new com.amazonaws.services.ec2.model.Instance(
    placement: new Placement(
      availabilityZone: availabilityZone
    )
  )

  def cachedService = new Service(
    serviceName: serviceName,
    applicationName: applicationName
  )

  def loadbalancer = new EcsLoadBalancerCache()

  def taskDefinition = new TaskDefinition()

  def taskCacheClient = Mock(TaskCacheClient)
  def serviceCacheClient = Mock(ServiceCacheClient)
  def scalableTargetCacheClient = Mock(ScalableTargetCacheClient)
  def taskDefinitionCacheClient = Mock(TaskDefinitionCacheClient)
  def ecsLoadbalancerCacheClient = Mock(EcsLoadbalancerCacheClient)
  def ecsCloudWatchAlarmCacheClient = Mock(EcsCloudWatchAlarmCacheClient)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def containerInformationService = Mock(ContainerInformationService)

  @Subject
  def provider = new EcsServerClusterProvider(accountCredentialsProvider,
    containerInformationService,
    taskCacheClient,
    serviceCacheClient,
    scalableTargetCacheClient,
    ecsLoadbalancerCacheClient,
    taskDefinitionCacheClient,
    ecsCloudWatchAlarmCacheClient)


  def setup() {
    serviceCacheClient.getAll(_, _) >> [cachedService]
    containerInformationService.getTaskPrivateAddress(_, _, _) >> "${ip}:1337"
    containerInformationService.getHealthStatus(_, _, _, _) >> [healthStatus]
    containerInformationService.getEc2Instance(account, region, task) >> ec2Instance
    taskCacheClient.getAll() >> [task]
    ecsLoadbalancerCacheClient.findAll() >> [loadbalancer]
    accountCredentialsProvider.getAll() >> [TestCredential.named('test', [CLOUD_PROVIDER: 'ecs'])]
    taskDefinitionCacheClient.get(_) >> taskDefinition
  }

  def 'should convert to ecs task'() {
    given:
    def expectedEcsTask = new EcsTask(taskId, startedAt, 'RUNNING', 'RUNNING',
      availabilityZone, [healthStatus], "${ip}:1337")

    when:
    def retrievedEcsTask = provider.convertToEcsTask(account, region, serviceName, task)

    then:
    retrievedEcsTask == expectedEcsTask
  }

  def 'should get a cluster'() {
    given:
    def expectedCluster = []

    when:
    def retrievedCluster = provider.getClusterDetails(applicationName)

    then:
    retrievedCluster == expectedCluster
  }
}
