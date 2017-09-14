/*
 *
 *  * Copyright 2017 Lookout, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContainerInformationService {

  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  private AmazonClientProvider amazonClientProvider;


  public List<Map<String, String>> getHealthStatus(String taskId, String serviceArn, String accountName, String region) {
    // TODO - remove the cheese here

    NetflixAmazonCredentials accountCredentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(accountName);
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(accountName, accountCredentials.getCredentialsProvider(), region);

//    DescribeServicesResult describeServicesResult = amazonECS.describeServices(new DescribeServicesRequest().withServices(serviceArn));

    List<Map<String, String>> healthMetrics = new ArrayList<>();
    Map<String, String> loadBalancerHealth = new HashMap<>();
    loadBalancerHealth.put("instanceId", taskId);
    loadBalancerHealth.put("state", "Up");
    loadBalancerHealth.put("type", "loadBalancer");

    Map<String, String> firstLoadBalancer = new HashMap<>();
    firstLoadBalancer.put("healthState", "Up");
    firstLoadBalancer.put("instanceId", "i-055cc597eec0597eb");
    firstLoadBalancer.put("loadBalancerName", "ALB-Name");
    firstLoadBalancer.put("loadBalancerType", "classic");
    firstLoadBalancer.put("state", "InService");

    healthMetrics.add(loadBalancerHealth);
    return healthMetrics;
  }

  public ContainerInstance getContainerInstance(AmazonECS amazonECS, Task task) {
    if (task == null) {
      return null;
    }

    ContainerInstance container = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(task.getContainerInstanceArn());
    DescribeContainerInstancesRequest request = new DescribeContainerInstancesRequest()
      .withCluster(task.getClusterArn())
      .withContainerInstances(queryList);
    List<ContainerInstance> containerList = amazonECS.describeContainerInstances(request).getContainerInstances();

    if (!containerList.isEmpty()) {
      if (containerList.size() != 1) {
        throw new InvalidParameterException("Tasks should only have one container associated to them. Multiple found");
      }
      container = containerList.get(0);
    }

    return container;
  }

  public InstanceStatus getEC2InstanceStatus(AmazonEC2 amazonEC2, ContainerInstance container) {
    if (container == null) {
      return null;
    }

    InstanceStatus instanceStatus = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(container.getEc2InstanceId());
    DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest()
      .withInstanceIds(queryList);
    List<InstanceStatus> instanceStatusList = amazonEC2.describeInstanceStatus(request).getInstanceStatuses();

    if (!instanceStatusList.isEmpty()) {
      if (instanceStatusList.size() != 1) {
        String message = "Container instances should only have only one Instance Status. Multiple found";
        throw new InvalidParameterException(message);
      }
      instanceStatus = instanceStatusList.get(0);
    }

    return instanceStatus;
  }

}
