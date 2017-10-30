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

import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.Service;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;


public class ServiceCachingAgentTest extends CommonCachingAgent {
  @Subject
  private ServiceCachingAgent agent = new ServiceCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldGetListOfContainerInstances() {
    //Given
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String serviceArn1 = "arn:aws:ecs:" + REGION + ":012345678910:service/service1";
    String serviceArn2 = "arn:aws:ecs:" + REGION + ":012345678910:service/service2";

    ListServicesResult listServicesResult = new ListServicesResult().withServiceArns(serviceArn1, serviceArn2);
    when(ecs.listServices(any(ListServicesRequest.class))).thenReturn(listServicesResult);

    List<Service> services = new LinkedList<>();
    services.add(new Service().withServiceArn(serviceArn1));
    services.add(new Service().withServiceArn(serviceArn2));

    DescribeServicesResult describeServicesResult = new DescribeServicesResult().withServices(services);
    when(ecs.describeServices(any(DescribeServicesRequest.class))).thenReturn(describeServicesResult);

    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(new ListClustersResult().withClusterArns(clusterArn));

    //When
    List<Service> returnedServices = agent.getItems(ecs, providerCache);

    //Then
    assertTrue("Expected the list to contain 2 ECS services, but got " + returnedServices.size(), returnedServices.size() == 2);
    for (Service service : returnedServices) {
      assertTrue("Expected the service to be in  " + services + " list but it was not. The container instance is: " + service, services.contains(service));
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    //Given
    String serviceName1 = "service-detail-stack-v1";
    String serviceName2 = "service-detail-stack-v2";
    List<String> serviceNames = new LinkedList<>();
    serviceNames.add(serviceName1);
    serviceNames.add(serviceName2);

    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";

    String serviceArn1 = "arn:aws:ecs:" + REGION + ":012345678910:service/" + serviceName1;
    String serviceArn2 = "arn:aws:ecs:" + REGION + ":012345678910:service/" + serviceName2;
    List<String> serviceArns = new LinkedList<>();
    serviceArns.add(serviceArn1);
    serviceArns.add(serviceArn2);

    List<Service> services = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (int x = 0; x < serviceArns.size(); x++) {
      keys.add(Keys.getServiceKey(ACCOUNT, REGION, serviceNames.get(x)));

      services.add(new Service().withClusterArn(clusterArn)
        .withServiceArn(serviceArns.get(x))
        .withServiceName(serviceNames.get(x))
      .withTaskDefinition("arn:aws:ecs:" + REGION + ":012345678910:task-definition/test-task-def:1")
      .withRoleArn("arn:aws:ecs:" + REGION + ":012345678910:service/test-role")
      .withDeploymentConfiguration(new DeploymentConfiguration().withMinimumHealthyPercent(50).withMaximumPercent(100))
      .withLoadBalancers(Collections.emptyList())
      .withDesiredCount(1)
      .withCreatedAt(new Date()));
    }

    //When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(services);

    //Then
    assertTrue("Expected the data map to contain 2 namespaces, but it contains " + dataMap.keySet().size() + " namespaces.", dataMap.keySet().size() == 2);
    assertTrue("Expected the data map to contain " + SERVICES.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(SERVICES.toString()));
    assertTrue("Expected the data map to contain " + ECS_CLUSTERS.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(ECS_CLUSTERS.toString()));
    assertTrue("Expected there to be 2 CacheData, instead there is  "+ dataMap.get(SERVICES.toString()).size(), dataMap.get(SERVICES.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(SERVICES.toString())) {
      assertTrue("Expected the key to be one of the following keys: " + keys.toString() + ". The key is: " + cacheData.getId() +".", keys.contains(cacheData.getId()));
      assertTrue("Expected the service ARN to be one of the following ARNs: " + serviceArns.toString() + ". The service ARN is: " + cacheData.getAttributes().get("serviceArn") +".", serviceArns.contains(cacheData.getAttributes().get("serviceArn")));
    }
  }

  @Test
  public void shouldAddToCache() {
    //Given
    String serviceName = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
    String key = Keys.getServiceKey(ACCOUNT, REGION, serviceName);
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String serviceArn = "arn:aws:ecs:" + REGION + ":012345678910:service/" + serviceName;

    Service service = new Service();
    service.setServiceName(serviceName);
    service.setServiceArn(serviceArn);
    service.setClusterArn(clusterArn);
    service.setTaskDefinition("arn:aws:ecs:" + REGION + ":012345678910:task-definition/test-task-def:1");
    service.setRoleArn("arn:aws:ecs:" + REGION + ":012345678910:service/test-role");
    service.setDeploymentConfiguration(new DeploymentConfiguration().withMinimumHealthyPercent(50).withMaximumPercent(100));
    service.setLoadBalancers(Collections.emptyList());
    service.setDesiredCount(1);
    service.setCreatedAt(new Date());

    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(new ListClustersResult().withClusterArns(clusterArn));
    when(ecs.listServices(any(ListServicesRequest.class))).thenReturn(new ListServicesResult().withServiceArns(serviceArn));
    when(ecs.describeServices(any(DescribeServicesRequest.class))).thenReturn(new DescribeServicesResult().withServices(service));

    //When
    CacheResult cacheResult = agent.loadData(providerCache);

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(Keys.Namespace.SERVICES.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));
  }
}
