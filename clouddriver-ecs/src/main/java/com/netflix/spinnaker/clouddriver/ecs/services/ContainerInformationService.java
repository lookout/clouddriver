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

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContainerInformationService {

  private final TaskCacheClient taskCacheClient;
  private final ServiceCacheClient serviceCacheClient;
  private final TaskHealthCacheClient taskHealthCacheClient;
  private final ContainerInstanceCacheClient containerInstanceCacheClient;

  @Autowired
  public ContainerInformationService(Cache cacheView) {
    this.taskCacheClient = new TaskCacheClient(cacheView);
    this.serviceCacheClient = new ServiceCacheClient(cacheView);
    this.taskHealthCacheClient = new TaskHealthCacheClient(cacheView);
    this.containerInstanceCacheClient = new ContainerInstanceCacheClient(cacheView);
  }


  public List<Map<String, String>> getHealthStatus(String taskId, String serviceName, String accountName, String region) {
    String serviceCacheKey = Keys.getServiceKey(accountName, region, serviceName);
    Service service = serviceCacheClient.get(serviceCacheKey);

    String healthKey = Keys.getTaskHealthKey(accountName, region, taskId);
    TaskHealth taskHealth = taskHealthCacheClient.get(healthKey);

    // A bit more of a graceful return, when the results haven't been cached yet - see the TO DO above.
    if (service == null || taskHealth == null) {
      List<Map<String, String>> healthMetrics = new ArrayList<>();

      Map<String, String> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);
      loadBalancerHealth.put("state", "Unknown");
      loadBalancerHealth.put("type", "loadBalancer");

      healthMetrics.add(loadBalancerHealth);
      return healthMetrics;
    }

    List<LoadBalancer> loadBalancers = service.getLoadBalancers();
    //There should only be 1 based on AWS documentation.
    if (loadBalancers.size() == 1) {

      List<Map<String, String>> healthMetrics = new ArrayList<>();
      Map<String, String> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);
      loadBalancerHealth.put("state", taskHealth.getState());
      loadBalancerHealth.put("type", taskHealth.getType());

      healthMetrics.add(loadBalancerHealth);
      return healthMetrics;
    } else if (loadBalancers.size() >= 2) {
      throw new IllegalArgumentException("Cannot have more than 1 load balancer while checking ECS health.");
    }
    return null;

  }

  public String getClusterArn(String accountName, String region, String taskId) {
    String key = Keys.getTaskKey(accountName, region, taskId);
    Task task = taskCacheClient.get(key);
    if (task != null) {
      return task.getClusterArn();
    }
    return null;
  }

  //TODO: clean up after EcsServerClusterProvider has been changed. hostPort and containerArn may be replaced with a CacheData instead.
  public String getTaskPrivateAddress(String accountName, String region, AmazonEC2 amazonEC2, Task task) {
    int hostPort;
    try {
      hostPort = task.getContainers().get(0).getNetworkBindings().get(0).getHostPort();
    } catch (Exception e) {
      hostPort = -1;
    }

    if (hostPort < 0 || hostPort > 65535) {
      return "unknown";
    }

    String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, task.getContainerInstanceArn());
    ContainerInstance containerInstance = containerInstanceCacheClient.get(containerInstanceCacheKey);
    if (containerInstance == null) {
      return "unknown";
    }

    //TODO: describeInstances should probably be cached.
    DescribeInstancesResult describeInstancesResult = amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(containerInstance.getEc2InstanceId()));
    String hostPrivateIpAddress = describeInstancesResult.getReservations().get(0).getInstances().get(0).getPrivateIpAddress(); // TODO - lots of assumptions are made here and need to be relaxed.  get(0) are probably all no-no's

    return String.format("%s:%s", hostPrivateIpAddress, hostPort);
  }

  //TODO: Delete this method once EcsServerClusterProvider has been reworked to use the cache.
  public String getEC2InstanceHostID(String accountName, String region, String containerArn) {
    String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, containerArn);
    ContainerInstance containerInstance = containerInstanceCacheClient.get(containerInstanceCacheKey);
    if (containerInstance != null) {
      return containerInstance.getEc2InstanceId();
    }
    return null;
  }

  public InstanceStatus getEC2InstanceStatus(AmazonEC2 amazonEC2, String accountName, String region, String containerArn) {
    if (containerArn == null) {
      return null;
    }

    String hostEc2InstanceId = getEC2InstanceHostID(accountName, region, containerArn);

    InstanceStatus instanceStatus = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(hostEc2InstanceId);
    DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest()
      .withInstanceIds(queryList);
    //TODO: describeInstanceStatus should probably be cached.
    List<InstanceStatus> instanceStatusList = amazonEC2.describeInstanceStatus(request).getInstanceStatuses();

    if (!instanceStatusList.isEmpty()) {
      //TODO: return the status of the container instance (ecs call) as opposed to ec2 instance(s).
      /*if (instanceStatusList.size() != 1) {
        String message = "Container instances should only have only one Instance Status. Multiple found";
        throw new InvalidParameterException(message);
      }*/
      instanceStatus = instanceStatusList.get(0);
    }

    return instanceStatus;
  }

  public String getClusterName(String serviceName, String accountName, String region) {
    String serviceCachekey = Keys.getServiceKey(accountName, region, serviceName);
    Service service = serviceCacheClient.get(serviceCachekey);
    if (service != null) {
      return service.getClusterName();
    }
    return null;
  }
}
