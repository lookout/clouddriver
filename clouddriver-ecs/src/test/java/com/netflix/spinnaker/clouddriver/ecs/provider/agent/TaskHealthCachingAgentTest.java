/*
 * Copyright 2014 Netflix, Inc.
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
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TaskHealthCachingAgentTest extends CommonCachingAgent {
  @Subject
  private final TaskHealthCachingAgent agent = new TaskHealthCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider);


  @Test
  public void shouldAddToCache() {
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
    taskAttributes.put("containerInstanceArn", CONTAINER_INSTANCE_ARN_1);
    taskAttributes.put("group", "service:" + SERVICE_NAME_1);
    taskAttributes.put("containers", Collections.singletonList(containerMap));
    CacheData taskCacheData = new DefaultCacheData(taskKey, taskAttributes, Collections.emptyMap());
    when(providerCache.getAll(Keys.Namespace.TASKS.toString()))
      .thenReturn(Collections.singletonList(taskCacheData));

    Map<String, Object> serviceAttributes = new HashMap<>();
    serviceAttributes.put("loadBalancers", Collections.singletonList(loadbalancerMap));
    serviceAttributes.put("taskDefinition", TASK_DEFINITION_ARN_1);
    CacheData serviceCacheData = new DefaultCacheData(serviceKey, serviceAttributes, Collections.emptyMap());
    when(providerCache.get(Keys.Namespace.SERVICES.toString(), serviceKey))
      .thenReturn(serviceCacheData);

    Map<String, Object> containerInstanceAttributes = new HashMap<>();
    containerInstanceAttributes.put("ec2InstanceId", EC2_INSTANCE_ID_1);
    CacheData containerInstanceCache = new DefaultCacheData(containerInstanceKey, containerInstanceAttributes, Collections.emptyMap());
    when(providerCache.get(Keys.Namespace.CONTAINER_INSTANCES.toString(), containerInstanceKey))
      .thenReturn(containerInstanceCache);

    DescribeTargetHealthResult describeTargetHealthResult = new DescribeTargetHealthResult().withTargetHealthDescriptions(
      new TargetHealthDescription().withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Healthy))
    );

    when(amazonloadBalancing.describeTargetHealth(any(DescribeTargetHealthRequest.class))).thenReturn(describeTargetHealthResult);

    //When
    CacheResult cacheResult = agent.loadData(providerCache);

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(HEALTH.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + healthKey + " but retrieved ID " + retrievedKey, retrievedKey.equals(healthKey));
  }
}
