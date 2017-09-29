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
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

@Component
public class ContainerInformationService {

  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  private AmazonClientProvider amazonClientProvider;

  @Autowired
  private Cache cacheView;


  public List<Map<String, String>> getHealthStatus(String clusterArn, String taskId, String serviceArn, String accountName, String region) {
    NetflixAmazonCredentials accountCredentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(accountName);

    String serviceCacheKey = Keys.getServiceKey(accountName, region, StringUtils.substringAfterLast(serviceArn, "/"));

    CacheData serviceCacheData = cacheView.get(SERVICES.toString(), serviceCacheKey);
    if (serviceCacheData == null) {
      return null;
    }

    // TODO: Find a way to deserialize LoadBalancer properly from withen the service cache data.
    List<Map<String, Object>> loadBalancers = (List<Map<String, Object>>) serviceCacheData.getAttributes().get("loadBalancers");
    //There should only be 1 based on AWS documentation.
    if (loadBalancers.size() == 1) {
      //TODO: getAmazonElasticLoadBalancingV2 should be cached.
      AmazonElasticLoadBalancing AmazonloadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(accountName, accountCredentials.getCredentialsProvider(), region);

      String taskCacheKey = Keys.getTaskKey(accountName, region, taskId);
      CacheData taskCacheData = cacheView.get(TASKS.toString(), taskCacheKey);

      String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, (String) taskCacheData.getAttributes().get("containerInstanceArn"));
      CacheData containerInstance = cacheView.get(CONTAINER_INSTANCES.toString(), containerInstanceCacheKey);

      if (containerInstance == null) {
        return null;
      }

      // TODO: Currently assuming there's 1 container with 1 port for the task given.
      // TODO: Find a way to deserialize containers properly from withen the task cache data.
      int port = (Integer) ((List<Map<String, Object>>) ((List<Map<String, Object>>) taskCacheData.getAttributes().get("containers")).get(0).get("networkBindings")).get(0).get("hostPort");

      DescribeTargetHealthResult targetGroupHealthResult = null;

      for (Map<String, Object> loadBalancer : loadBalancers) {
        //TODO: describeTargetHealth should be cached.
        targetGroupHealthResult = AmazonloadBalancing.describeTargetHealth(
          new DescribeTargetHealthRequest().withTargetGroupArn((String) loadBalancer.get("targetGroupArn")).withTargets(
            new TargetDescription().withId((String) containerInstance.getAttributes().get("ec2InstanceId")).withPort(port)));
      }

      List<Map<String, String>> healthMetrics = new ArrayList<>();
      Map<String, String> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);

      String targetHealth = targetGroupHealthResult.getTargetHealthDescriptions().get(0).getTargetHealth().getState();

      loadBalancerHealth.put("state", targetHealth.equals("healthy") ? "Up" : "Unknown");  // TODO - Return better values, and think of a better strategy at defining health
      loadBalancerHealth.put("type", "loadBalancer");

      healthMetrics.add(loadBalancerHealth);
      return healthMetrics;
    } else if (loadBalancers.size() > 1) {
      throw new IllegalArgumentException("Cannot have more than 1 loadbalancer while checking ECS health.");
    }
    return null;

  }

  public String getClusterArn(String accountName, String region, String taskId) {
    String taskCacheKey = Keys.getTaskKey(accountName, region, taskId);
    CacheData taskCacheData = cacheView.get(TASKS.toString(), taskCacheKey);
    if (taskCacheData != null) {
      return (String) taskCacheData.getAttributes().get("clusterArn");
    }
    return null;
  }

  public String getTaskPrivateAddress(String accountName, String region, AmazonEC2 amazonEC2, Task task) {
    List<Container> containers = task.getContainers();
    if (containers == null || containers.size() < 1) {
      return "unknown";
    }

    List<NetworkBinding> networkBindings = containers.get(0).getNetworkBindings();
    if (networkBindings == null || networkBindings.size() < 1) {
      return "unknown";
    }

    int hostPort = networkBindings.get(0).getHostPort();
    String taskCacheKey = Keys.getTaskKey(accountName, region, task.getTaskArn());
    CacheData taskCacheData = cacheView.get(TASKS.toString(), taskCacheKey);

    String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, (String) taskCacheData.getAttributes().get("containerInstanceArn"));
    CacheData containerInstanceCacheData = cacheView.get(CONTAINER_INSTANCES.toString(), containerInstanceCacheKey);
    String hostEc2InstanceId = (String) containerInstanceCacheData.getAttributes().get("ec2InstanceId");

    //TODO: describeInstances should probably be cached.
    DescribeInstancesResult describeInstancesResult = amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(hostEc2InstanceId));
    String hostPrivateIpAddress = describeInstancesResult.getReservations().get(0).getInstances().get(0).getPrivateIpAddress(); // TODO - lots of assumptions are made here and need to be relaxed.  get(0) are probably all no-no's

    return String.format("%s:%s", hostPrivateIpAddress, hostPort);
  }

  public String getContainerInstanceArn(String accountName, String region, Task task) {
    if (task == null) {
      return null;
    }

    String taskCacheKey = Keys.getTaskKey(accountName, region, task.getTaskArn());
    CacheData taskCacheData = cacheView.get(TASKS.toString(), taskCacheKey);

    if (taskCacheData != null) {
      return (String) taskCacheData.getAttributes().get("containerInstanceArn");
    }

    return null;
  }

  //TODO: Delete this method once EcsServerClusterProvider has been reworked to use the cache.
  @Deprecated
  public String getEC2InstanceHostID(String accountName, String region, String containerArn) {
    String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, containerArn);
    CacheData containerInstanceCacheData = cacheView.get(CONTAINER_INSTANCES.toString(), containerInstanceCacheKey);
    if (containerInstanceCacheData != null) {
      return (String) containerInstanceCacheData.getAttributes().get("ec2InstanceId");
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
      if (instanceStatusList.size() != 1) {
        String message = "Container instances should only have only one Instance Status. Multiple found";
        throw new InvalidParameterException(message);
      }
      instanceStatus = instanceStatusList.get(0);
    }

    return instanceStatus;
  }

  public String getClusterName(String serviceName, String accountName, String region) {
    String serviceCachekey = Keys.getServiceKey(accountName, region, serviceName);
    CacheData serviceCacheData = cacheView.get(SERVICES.toString(), serviceCachekey);
    if (serviceCacheData != null) {
      serviceCacheData.getAttributes().get("clusterName");
    }
    return null;
  }

  public int getLatestServiceVersion(String clusterName, String serviceName, String accountName, String region) {
    int latestVersion = 0;

    Collection<CacheData> allServiceCache = cacheView.getAll(SERVICES.toString());
    for (CacheData serviceCacheData : allServiceCache) {
      if (serviceCacheData.getAttributes().get("clusterName").equals(clusterName) &&
        ((String) serviceCacheData.getAttributes().get("serviceName")).contains(serviceName)) {
        int currentVersion;
        try {
          currentVersion = Integer.parseInt(StringUtils.substringAfterLast(((String) serviceCacheData.getAttributes().get("serviceName")), "-").replaceAll("v", ""));
        } catch (NumberFormatException e) {
          currentVersion = 0;
        }
        latestVersion = Math.max(currentVersion, latestVersion);
      }
    }

    return latestVersion;
  }
}
