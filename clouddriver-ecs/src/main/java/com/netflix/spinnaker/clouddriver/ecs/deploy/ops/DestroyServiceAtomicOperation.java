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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DeleteServiceRequest;
import com.amazonaws.services.ecs.model.DeleteServiceResult;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DestroyServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DestroyServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_ECS_SERVER_GROUP";

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  EcsCloudWatchAlarmCacheClient metricAlarmCacheClient;
  @Autowired
  ContainerInformationService containerInformationService;

  DestroyServiceDescription description;

  public DestroyServiceAtomicOperation(DestroyServiceDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Destroy Amazon ECS Server Group (Service) Operation...");
    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());

    String clusterName = containerInformationService.getClusterName(description.getServerGroupName(), description.getAccount(), description.getRegion());

    getTask().updateStatus(BASE_PHASE, "Removing MetricAlarms from " + description.getServerGroupName() + ".");
    deleteMetrics();
    getTask().updateStatus(BASE_PHASE, "Done removing MetricAlarms from " + description.getServerGroupName() + ".");

    UpdateServiceRequest updateServiceRequest = new UpdateServiceRequest();
    updateServiceRequest.setService(description.getServerGroupName());
    updateServiceRequest.setDesiredCount(0);
    updateServiceRequest.setCluster(clusterName);

    getTask().updateStatus(BASE_PHASE, "Scaling " + description.getServerGroupName() + " service down to 0.");
    ecs.updateService(updateServiceRequest);

    DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest();
    deleteServiceRequest.setService(description.getServerGroupName());
    deleteServiceRequest.setCluster(clusterName);

    getTask().updateStatus(BASE_PHASE, "Deleting " + description.getServerGroupName() + " service.");
    DeleteServiceResult deleteServiceResult = ecs.deleteService(deleteServiceRequest);

    getTask().updateStatus(BASE_PHASE, "Deleting " + deleteServiceResult.getService().getTaskDefinition() + " task definition belonging to the service.");
    ecs.deregisterTaskDefinition(new DeregisterTaskDefinitionRequest().withTaskDefinition(deleteServiceResult.getService().getTaskDefinition()));

    return null;
  }

  private void deleteMetrics() {
    List<EcsMetricAlarm> metricAlarms = metricAlarmCacheClient.getMetricAlarms(description.getServerGroupName(), description.getCredentialAccount(), description.getRegion());

    if (metricAlarms.isEmpty()) {
      return;
    }

    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AmazonCloudWatch amazonCloudWatch = amazonClientProvider.getAmazonCloudWatch(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());

    amazonCloudWatch.deleteAlarms(new DeleteAlarmsRequest().withAlarmNames(metricAlarms.stream()
      .map(MetricAlarm::getAlarmName)
      .collect(Collectors.toSet())));

    Set<String> resources = new HashSet<>();
    // Stream and flatMap it? Couldn't figure out how.
    for (MetricAlarm metricAlarm : metricAlarms) {
      resources.addAll(buildResourceList(metricAlarm.getOKActions()));
      resources.addAll(buildResourceList(metricAlarm.getAlarmActions()));
      resources.addAll(buildResourceList(metricAlarm.getInsufficientDataActions()));
    }

    deregisterScalableTargets(resources);
  }

  private Set<String> buildResourceList(List<String> metricAlarmArn) {
    return metricAlarmArn.stream()
      .filter(arn -> arn.contains(description.getServerGroupName()))
      .map(arn -> {
        String resource = StringUtils.substringAfterLast(arn, ":resource/");
        resource = StringUtils.substringBeforeLast(resource, ":policyName");
        return resource;
      })
      .collect(Collectors.toSet());
  }

  private void deregisterScalableTargets(Set<String> resources) {
    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AWSApplicationAutoScaling autoScaling = amazonClientProvider.getAmazonApplicationAutoScaling(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());

    Map<String, Set<String>> resourceMap = new HashMap<>();
    for (String resource : resources) {
      String namespace = StringUtils.substringBefore(resource, "/");
      String service = StringUtils.substringAfter(resource, "/");
      if (resourceMap.containsKey(namespace)) {
        resourceMap.get(namespace).add(service);
      } else {
        resourceMap.put(namespace, Collections.singleton(service));
      }
    }

    Set<DeregisterScalableTargetRequest> deregisterRequests = new HashSet<>();
    for (String namespace : resourceMap.keySet()) {
      String nextToken = null;
      do {
        DescribeScalableTargetsRequest request = new DescribeScalableTargetsRequest()
          .withServiceNamespace(namespace)
          .withResourceIds(resourceMap.get(namespace));

        if (nextToken != null) {
          request.setNextToken(nextToken);
        }

        DescribeScalableTargetsResult result = autoScaling.describeScalableTargets(request);

        deregisterRequests.addAll(result.getScalableTargets().stream()
          .map(scalableTarget -> new DeregisterScalableTargetRequest()
            .withResourceId(scalableTarget.getResourceId())
            .withScalableDimension(scalableTarget.getScalableDimension())
            .withServiceNamespace(scalableTarget.getServiceNamespace()))
          .collect(Collectors.toSet()));

        nextToken = result.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }

    for (DeregisterScalableTargetRequest request : deregisterRequests) {
      autoScaling.deregisterScalableTarget(request);
    }

  }
}
