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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealth;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthStateEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TaskHealthCacheTest extends CommonCachingAgent {
  @Subject
  private final TaskHealthCachingAgent agent = new TaskHealthCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, mapper);
  private final TaskHealthCacheClient client = new TaskHealthCacheClient(providerCache);


  @Test
  public void shouldRetrieveFromWrittenCache() {
    //Given
    AmazonElasticLoadBalancing amazonloadBalancing = mock(AmazonElasticLoadBalancing.class);
    when(clientProvider.getAmazonElasticLoadBalancingV2(anyString(), any(AWSCredentialsProvider.class), anyString())).thenReturn(amazonloadBalancing);

    String targetGroupArn = "arn:aws:elasticloadbalancing:" + REGION + ":769716316905:targetgroup/test-target-group/9e8997b7cff00c62";

    String taskKey = Keys.getTaskKey(ACCOUNT, REGION, TASK_ID_1);
    String healthKey = Keys.getTaskHealthKey(ACCOUNT, REGION, TASK_ID_1);
    String serviceKey = Keys.getServiceKey(ACCOUNT, REGION, SERVICE_NAME_1);
    String containerInstanceKey = Keys.getContainerInstanceKey(ACCOUNT, REGION, CONTAINER_INSTANCE_ARN_1);

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> containerMap = mapper.convertValue(new Container().withNetworkBindings(new NetworkBinding().withHostPort(1337)), Map.class);
    Map<String, Object> loadbalancerMap = mapper.convertValue(new LoadBalancer().withTargetGroupArn(targetGroupArn), Map.class);

    Map<String, Object> taskAttributes = new HashMap<>();
    taskAttributes.put("taskId", TASK_ID_1);
    taskAttributes.put("taskArn", TASK_ARN_1);
    taskAttributes.put("startedAt", new Date().getTime());
    taskAttributes.put("containerInstanceArn", CONTAINER_INSTANCE_ARN_1);
    taskAttributes.put("group", "service:" + SERVICE_NAME_1);
    taskAttributes.put("containers", Collections.singletonList(containerMap));
    CacheData taskCacheData = new DefaultCacheData(taskKey, taskAttributes, Collections.emptyMap());
    when(providerCache.getAll(TASKS.toString()))
      .thenReturn(Collections.singletonList(taskCacheData));

    Map<String, Object> serviceAttributes = new HashMap<>();
    serviceAttributes.put("loadBalancers", Collections.singletonList(loadbalancerMap));
    serviceAttributes.put("taskDefinition", TASK_DEFINITION_ARN_1);
    serviceAttributes.put("desiredCount", 1);
    serviceAttributes.put("maximumPercent", 1);
    serviceAttributes.put("minimumHealthyPercent", 1);
    serviceAttributes.put("createdAt", new Date().getTime());
    CacheData serviceCacheData = new DefaultCacheData(serviceKey, serviceAttributes, Collections.emptyMap());
    when(providerCache.get(Namespace.SERVICES.toString(), serviceKey))
      .thenReturn(serviceCacheData);

    Map<String, Object> containerInstanceAttributes = new HashMap<>();
    containerInstanceAttributes.put("ec2InstanceId", EC2_INSTANCE_ID_1);
    CacheData containerInstanceCache = new DefaultCacheData(containerInstanceKey, containerInstanceAttributes, Collections.emptyMap());
    when(providerCache.get(CONTAINER_INSTANCES.toString(), containerInstanceKey))
      .thenReturn(containerInstanceCache);

    DescribeTargetHealthResult describeTargetHealthResult = new DescribeTargetHealthResult().withTargetHealthDescriptions(
      new TargetHealthDescription().withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Healthy))
    );

    when(amazonloadBalancing.describeTargetHealth(any(DescribeTargetHealthRequest.class))).thenReturn(describeTargetHealthResult);


    //When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(HEALTH.toString(), healthKey)).thenReturn(cacheResult.getCacheResults().get(HEALTH.toString()).iterator().next());


    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(HEALTH.toString());
    TaskHealth taskHealth = client.get(healthKey);

    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + healthKey + " but retrieved ID " + retrievedKey, retrievedKey.equals(healthKey));

    assertTrue("Expected the task health to have state of Up but got " + taskHealth.getState(), taskHealth.getState().equals("Up"));
    assertTrue("Expected the task health to instance id of " + TASK_ARN_1 + " but got " + taskHealth.getInstanceId(), taskHealth.getInstanceId().equals(TASK_ARN_1));
    assertTrue("Expected the task health to service name of " + SERVICE_NAME_1 + " but got " + taskHealth.getServiceName(), taskHealth.getServiceName().equals(SERVICE_NAME_1));
    assertTrue("Expected the task health to task ARN of " + TASK_ARN_1 + " but got " + taskHealth.getTaskArn(), taskHealth.getTaskArn().equals(TASK_ARN_1));
    assertTrue("Expected the task health to task ID of " + TASK_ID_1 + " but got " + taskHealth.getTaskId(), taskHealth.getTaskId().equals(TASK_ID_1));
    assertTrue("Expected the task health to health type of loadBalancer but got " + taskHealth.getType(), taskHealth.getType().equals("loadBalancer"));
  }
}
