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

import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;


public class ContainerInstanceCacheTest extends CommonCachingAgent {
  @Subject
  private ContainerInstanceCachingAgent agent = new ContainerInstanceCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);
  @Subject
  private ContainerInstanceCacheClient client = new ContainerInstanceCacheClient(providerCache);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    //Given
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String containerInstanceArn = "arn:aws:ecs:" + REGION + ":012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98";
    String key = Keys.getContainerInstanceKey(ACCOUNT, REGION, containerInstanceArn);

    ContainerInstance containerInstance = new ContainerInstance();
    containerInstance.setContainerInstanceArn(containerInstanceArn);
    containerInstance.setEc2InstanceId("i-042f39dc");

    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(new ListClustersResult().withClusterArns(clusterArn));
    when(ecs.listContainerInstances(any(ListContainerInstancesRequest.class))).thenReturn(new ListContainerInstancesResult().withContainerInstanceArns(containerInstanceArn));
    when(ecs.describeContainerInstances(any(DescribeContainerInstancesRequest.class))).thenReturn(new DescribeContainerInstancesResult().withContainerInstances(containerInstance));

    //When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(CONTAINER_INSTANCES.toString(), key)).thenReturn(cacheResult.getCacheResults().get(CONTAINER_INSTANCES.toString()).iterator().next());

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(CONTAINER_INSTANCES.toString());
    com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance ecsContainerInstance = client.get(key);

    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));

    assertTrue("Expected the container instance to have EC2 instance ID of " + containerInstance.getEc2InstanceId() + " but got " + ecsContainerInstance.getEc2InstanceId(),
      containerInstance.getEc2InstanceId().equals(ecsContainerInstance.getEc2InstanceId()));
    assertTrue("Expected the container instance to have the ARN " + containerInstance.getContainerInstanceArn() + " but got " + ecsContainerInstance.getArn(),
      containerInstance.getContainerInstanceArn().equals(ecsContainerInstance.getArn()));
  }
}
