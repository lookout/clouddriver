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
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import org.junit.Assert;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class ServiceCacheTest extends CommonCachingAgent {
  private final ServiceCachingAgent agent = new ServiceCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);
  @Subject
  private final ServiceCacheClient client = new ServiceCacheClient(providerCache);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    //Given
    String applicationName = "test";
    String serviceName = applicationName + "-stack-detail-v1";
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
    when(providerCache.get(SERVICES.toString(), key)).thenReturn(cacheResult.getCacheResults().get(SERVICES.toString()).iterator().next());

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(SERVICES.toString());
    com.netflix.spinnaker.clouddriver.ecs.cache.model.Service ecsService = client.get(key);

    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));

    assertTrue("Expected the service application name to be " + applicationName + " but got " + ecsService.getApplicationName(),
      applicationName.equals(ecsService.getApplicationName()));
    assertTrue("Expected the service name to be " + serviceName + " but got " + ecsService.getServiceName(),
      serviceName.equals(ecsService.getServiceName()));
    assertTrue("Expected the service ARN to be " + serviceArn + " but got " + ecsService.getServiceArn(),
      serviceArn.equals(ecsService.getServiceArn()));
    assertTrue("Expected the service's cluster ARN to be " + clusterArn + " but got " + ecsService.getClusterArn(),
      clusterArn.equals(ecsService.getClusterArn()));
    assertTrue("Expected the service's cluster ARN to be " + clusterArn + " but got " + ecsService.getClusterArn(),
      clusterArn.equals(ecsService.getClusterArn()));
    Assert.assertTrue("Expected the role ARN of the service to be " + service.getRoleArn() + " but got " + ecsService.getRoleArn(),
      service.getRoleArn().equals(ecsService.getRoleArn()));
    Assert.assertTrue("Expected the task definition of the service to be " + service.getTaskDefinition() + " but got " + ecsService.getTaskDefinition(), service.getTaskDefinition().equals(ecsService.getTaskDefinition()));
    Assert.assertTrue("Expected the desired count of the service to be " + service.getDesiredCount() + " but got " + ecsService.getDesiredCount(),
      service.getDesiredCount() == ecsService.getDesiredCount());
    Assert.assertTrue("Expected the maximum percent of the service to be " + service.getDeploymentConfiguration().getMaximumPercent() + " but got " + ecsService.getMaximumPercent(),
      service.getDeploymentConfiguration().getMaximumPercent() == ecsService.getMaximumPercent());
    Assert.assertTrue("Expected the minimum healthy percent of the service to be " + service.getDeploymentConfiguration().getMinimumHealthyPercent() + " but got " + ecsService.getMinimumHealthyPercent(),
      service.getDeploymentConfiguration().getMinimumHealthyPercent() == ecsService.getMinimumHealthyPercent());
    Assert.assertTrue("Expected the created at of the service to be " + service.getCreatedAt().getTime() + " but got " + ecsService.getCreatedAt(), service.getCreatedAt().getTime() == ecsService.getCreatedAt());
    Assert.assertTrue("Expected the service to have 0 load balancer but got " + ecsService.getLoadBalancers().size(),
      ecsService.getLoadBalancers().size() == 0);
  }
}
