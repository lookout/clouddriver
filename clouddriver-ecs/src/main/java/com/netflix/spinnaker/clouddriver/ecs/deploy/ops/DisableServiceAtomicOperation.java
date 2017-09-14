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
  private static final String REGION = "us-west-2";
  private static final String APP_VERSION = "v1337";

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
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), REGION);
    AmazonElasticLoadBalancing elb = amazonClientProvider.getAmazonElasticLoadBalancingV2(description.getCredentialAccount(), credentials.getCredentialsProvider(), REGION);

    String serviceName = description.getApplication();
    if (description.getStack() != null) {
      serviceName += "-" + description.getStack();
    }
    if (description.getDetail() != null) {
      serviceName += "-" + description.getDetail();
    }
    serviceName += "-" + APP_VERSION;

    Collection<String> services = new LinkedList<>();
    services.add(serviceName);

    DescribeServicesRequest describeServicesRequest = new DescribeServicesRequest();
    describeServicesRequest.setServices(services);

    getTask().updateStatus(BASE_PHASE, "Describing " + serviceName + " service.");
    DescribeServicesResult describeServicesResult = ecs.describeServices(describeServicesRequest);

    Service service = null;
    for (Service returnedService : describeServicesResult.getServices()) {
      if (returnedService.getServiceName().equals(serviceName)) {
        service = returnedService;
        break;
      }
    }

    if (service == null) {
      getTask().updateStatus(BASE_PHASE, "Failed to describe " + serviceName + " service.");
      getTask().fail();
      return null;
    }

    getTask().updateStatus(BASE_PHASE, "Listing tasks for " + serviceName + " service.");
    ListTasksRequest listTasksRequest = new ListTasksRequest();
    listTasksRequest.setServiceName(serviceName);
    ListTasksResult listTasksResult = ecs.listTasks(listTasksRequest);

    getTask().updateStatus(BASE_PHASE, "Describing tasks for " + serviceName + " service.");
    DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest();
    describeTasksRequest.setTasks(listTasksResult.getTaskArns());
    DescribeTasksResult describeTasksResult = ecs.describeTasks(describeTasksRequest);

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

    getTask().updateStatus(BASE_PHASE, "Describing container instances " + serviceName + " service.");
    DescribeContainerInstancesRequest describeContainerInstancesRequest = new DescribeContainerInstancesRequest();
    describeContainerInstancesRequest.setContainerInstances(containerInstancePorts.keySet());
    DescribeContainerInstancesResult describeContainerInstancesResult = ecs.describeContainerInstances(describeContainerInstancesRequest);

    getTask().updateStatus(BASE_PHASE, "Creating TargetDescription set to deregister.");
    Collection<TargetDescription> targetDescriptions = new HashSet<>();
    for (ContainerInstance containerInstance : describeContainerInstancesResult.getContainerInstances()) {
      for (Integer port : containerInstancePorts.get(containerInstance.getContainerInstanceArn())) {
        TargetDescription targetDescription = new TargetDescription();
        targetDescription.setId(containerInstance.getEc2InstanceId());
        targetDescription.setPort(port);
        targetDescriptions.add(targetDescription);
      }
    }

    getTask().updateStatus(BASE_PHASE, "Deregistering targets");
    // Currently there should only be 1 load balancer based on what's written in AWS docs.
    List<LoadBalancer> loadBalancers = service.getLoadBalancers();
    for (LoadBalancer loadBalancer : loadBalancers) {
      DeregisterTargetsRequest deregisterTargetsRequest = new DeregisterTargetsRequest();
      deregisterTargetsRequest.setTargets(targetDescriptions);
      deregisterTargetsRequest.setTargetGroupArn(loadBalancer.getTargetGroupArn());
      elb.deregisterTargets(deregisterTargetsRequest);
    }

    return null;
  }

}
