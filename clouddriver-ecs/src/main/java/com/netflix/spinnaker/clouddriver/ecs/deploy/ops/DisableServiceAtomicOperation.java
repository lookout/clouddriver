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

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class DisableServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DISABLE_ECS_SERVER_GROUP";

  // TODO: Remove hardcoded CLUSTER_NAME
  private static final String CLUSTER_NAME = "poc";

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  DisableServiceDescription description;

  public DisableServiceAtomicOperation(DisableServiceDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    //deregister all instances from target - see AbstractEnableDisableAtomicOperation
    getTask().updateStatus(BASE_PHASE, "Initializing Disable Amazon ECS Server Group Operation...");

    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());
    AmazonElasticLoadBalancing elb = amazonClientProvider.getAmazonElasticLoadBalancingV2(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());

    Collection<String> services = new LinkedList<>();
    services.add(description.getServerGroupName());

    getTask().updateStatus(BASE_PHASE, "Describing " + description.getServerGroupName() + " service.");
    DescribeServicesResult describeServicesResult = ecs.describeServices(
      new DescribeServicesRequest().withServices(services).withCluster(CLUSTER_NAME));

    Service service = null;
    for (Service returnedService : describeServicesResult.getServices()) {
      if (returnedService.getServiceName().equals(description.getServerGroupName())) {
        service = returnedService;
        break;
      }
    }

    if (service == null) {
      getTask().updateStatus(BASE_PHASE, "Failed to describe " + description.getServerGroupName() + " service.");
      getTask().fail();
      return null;
    }

    getTask().updateStatus(BASE_PHASE, "Listing tasks for " + description.getServerGroupName() + " service.");
    ListTasksResult listTasksResult = ecs.listTasks(
      new ListTasksRequest().withServiceName(description.getServerGroupName()).withCluster(CLUSTER_NAME));

    getTask().updateStatus(BASE_PHASE, "Describing tasks for " + description.getServerGroupName() + " service.");
    DescribeTasksResult describeTasksResult = ecs.describeTasks(
      new DescribeTasksRequest().withTasks(listTasksResult.getTaskArns()).withCluster(CLUSTER_NAME));

    HashMap<String, List<Integer>> containerInstancePorts = new HashMap<>();
    for (com.amazonaws.services.ecs.model.Task task : describeTasksResult.getTasks()) {
      List<Integer> portList = new LinkedList<>();
      for (Container container : task.getContainers()) {
        for (NetworkBinding networkBinding : container.getNetworkBindings()) {
          portList.add(networkBinding.getHostPort());
        }
      }
      containerInstancePorts.put(task.getContainerInstanceArn(), portList);
    }

    getTask().updateStatus(BASE_PHASE, "Describing container instances " + description.getServerGroupName() + " service.");
    DescribeContainerInstancesResult describeContainerInstancesResult = ecs.describeContainerInstances(
      new DescribeContainerInstancesRequest().withContainerInstances(containerInstancePorts.keySet()).withCluster(CLUSTER_NAME));

    getTask().updateStatus(BASE_PHASE, "Creating TargetDescription set to deregister.");
    Collection<TargetDescription> targetDescriptions = new HashSet<>();
    for (ContainerInstance containerInstance : describeContainerInstancesResult.getContainerInstances()) {
      for (Integer port : containerInstancePorts.get(containerInstance.getContainerInstanceArn())) {
        targetDescriptions.add(new TargetDescription().withPort(port).withId(containerInstance.getEc2InstanceId()));
      }
    }

    getTask().updateStatus(BASE_PHASE, "Deregistering targets");
    // Currently there should only be 1 load balancer based on what's written in AWS docs.
    List<LoadBalancer> loadBalancers = service.getLoadBalancers();
    for (LoadBalancer loadBalancer : loadBalancers) {
      elb.deregisterTargets(new DeregisterTargetsRequest().withTargets(targetDescriptions).withTargetGroupArn(loadBalancer.getTargetGroupArn()));
    }

    return null;
  }

}
