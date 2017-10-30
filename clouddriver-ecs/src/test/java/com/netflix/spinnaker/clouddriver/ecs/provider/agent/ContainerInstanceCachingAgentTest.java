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


public class ContainerInstanceCachingAgentTest extends CommonCachingAgent {
  @Subject
  private ContainerInstanceCachingAgent agent = new ContainerInstanceCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldGetListOfContainerInstances() {
    //Given
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String containerInstanceArn1 = "arn:aws:ecs:" + REGION + ":012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98";
    String containerInstanceArn2 = "arn:aws:ecs:" + REGION + ":012345678910:container-instance/deadbeef-0b16-4af4-bfac-a85f7587aa98";

    List<String> containerInstanceArns = new LinkedList<>();
    containerInstanceArns.add(containerInstanceArn1);
    containerInstanceArns.add(containerInstanceArn2);

    Collection<ContainerInstance> containerInstances = new LinkedList<>();
    containerInstances.add(new ContainerInstance().withContainerInstanceArn(containerInstanceArn1));
    containerInstances.add(new ContainerInstance().withContainerInstanceArn(containerInstanceArn2));

    ListContainerInstancesResult listContainerInstacesResult = new ListContainerInstancesResult().withContainerInstanceArns(containerInstanceArns);
    when(ecs.listContainerInstances(any(ListContainerInstancesRequest.class))).thenReturn(listContainerInstacesResult);

    DescribeContainerInstancesResult describeContainerInstanceResult = new DescribeContainerInstancesResult().withContainerInstances(containerInstances);
    when(ecs.describeContainerInstances(any(DescribeContainerInstancesRequest.class))).thenReturn(describeContainerInstanceResult);

    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(new ListClustersResult().withClusterArns(clusterArn));

    //When
    List<ContainerInstance> returnedContainerInstances = agent.getItems(ecs, providerCache);

    //Then
    assertTrue("Expected the list to contain " + containerInstances.size() + " ECS container instances, but got " + returnedContainerInstances.size(), returnedContainerInstances.size() == containerInstances.size());
    for (ContainerInstance containerInstance : returnedContainerInstances) {
      assertTrue("Expected the container instance to be in  " + containerInstances + " list but it was not. The container instance is: " + containerInstance, containerInstances.contains(containerInstance));
      assertTrue("Expected the container instance arn to be in  " + containerInstanceArns + " list but it was not. The container instance ARN is: " + containerInstance.getContainerInstanceArn(), containerInstanceArns.contains(containerInstance.getContainerInstanceArn()));
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    //Given
    String containerInstanceArn1 = "arn:aws:ecs:" + REGION + ":012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98";
    String containerInstanceArn2 = "arn:aws:ecs:" + REGION + ":012345678910:container-instance/deadbeef-0b16-4af4-bfac-a85f7587aa98";
    String ec2Id1 = "i-042f39dc";
    String ec2Id2 = "i-deadbeef";

    Set<String> arns = new HashSet<>();
    arns.add(containerInstanceArn1);
    arns.add(containerInstanceArn2);

    Set<String> ec2Ids = new HashSet<>();
    ec2Ids.add(ec2Id1);
    ec2Ids.add(ec2Id2);

    Collection<ContainerInstance> containerInstances = new LinkedList<>();
    containerInstances.add(new ContainerInstance().withContainerInstanceArn(containerInstanceArn1).withEc2InstanceId(ec2Id1));
    containerInstances.add(new ContainerInstance().withContainerInstanceArn(containerInstanceArn2).withEc2InstanceId(ec2Id2));

    //When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(containerInstances);

    //Then
    assertTrue("Expected the data map to contain 1 namespace, but it contains " + dataMap.keySet().size() + " namespaces.", dataMap.keySet().size() == 1);
    assertTrue("Expected the data map to contain " + CONTAINER_INSTANCES.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(CONTAINER_INSTANCES.toString()));
    assertTrue("Expected there to be 2 CacheData, instead there is  " + dataMap.get(CONTAINER_INSTANCES.toString()).size(), dataMap.get(CONTAINER_INSTANCES.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(CONTAINER_INSTANCES.toString())) {
      Map<String, Object> attributes = cacheData.getAttributes();
      assertTrue("Expected the container instance ARN to be in the " + arns + " list, but was not. The given arn is " + attributes.get("containerInstanceArn"), arns.contains(attributes.get("containerInstanceArn")));
      assertTrue("Expected the EC2 instance ID to be in the " + ec2Ids + " list, but was not. The given arn is " + attributes.get("ec2InstanceId"), ec2Ids.contains(attributes.get("ec2InstanceId")));
    }
  }

  @Test
  public void shouldAddToCache() {
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

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(Keys.Namespace.CONTAINER_INSTANCES.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));
  }
}
