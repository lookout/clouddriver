package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.amazonaws.services.ecs.model.LoadBalancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class ServiceCacheClientTest extends CommonCacheClient {
  @Subject
  private ServiceCacheClient client = new ServiceCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    //Given
    ObjectMapper mapper = new ObjectMapper();
    String serviceName = "test-service";
    String key = Keys.getServiceKey(ACCOUNT, REGION, serviceName);
    String clusterName = "test-cluster";

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ACCOUNT);
    attributes.put("region", REGION);
    attributes.put("applicationName", "test-app");
    attributes.put("serviceName", serviceName);
    attributes.put("serviceArn", "arn:aws:ecs:" + REGION + ":012345678910:service/" + serviceName);
    attributes.put("clusterName", clusterName);
    attributes.put("clusterArn", "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName);
    attributes.put("roleArn", "arn:aws:ecs:" + REGION + ":012345678910:service/test-role");
    attributes.put("taskDefinition", "arn:aws:ecs:" + REGION + ":012345678910:task-definition/test-task-def:1");
    attributes.put("desiredCount", 9001);
    attributes.put("maximumPercent", 100);
    attributes.put("minimumHealthyPercent", 50);
    attributes.put("createdAt", 1337L);

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName("container-name");
    loadBalancer.setContainerPort(8080);
    loadBalancer.setLoadBalancerName("balancer-of-load");
    loadBalancer.setTargetGroupArn("target-group-arn");

    attributes.put("loadBalancers", Collections.singletonList(mapper.convertValue(loadBalancer, Map.class)));

    when(cacheView.get(SERVICES.toString(), key)).thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    //When
    Service service = client.get(key);

    //Then
    assertTrue("Expected the cluster name to be " + clusterName + " but got " + service.getClusterName(), clusterName.equals(service.getClusterName()));
    assertTrue("Expected the cluster ARN to be " + attributes.get("clusterArn") + " but got " + service.getClusterArn(), attributes.get("clusterArn").equals(service.getClusterArn()));
    assertTrue("Expected the account of the service to be " + ACCOUNT + " but got " + service.getAccount(), ACCOUNT.equals(service.getAccount()));
    assertTrue("Expected the region of the service to be " + REGION + " but got " + service.getRegion(), REGION.equals(service.getRegion()));

    assertTrue("Expected the service application name to be " + attributes.get("applicationName") + " but got " + service.getApplicationName(), attributes.get("applicationName").equals(service.getApplicationName()));
    assertTrue("Expected the service name to be " + attributes.get("serviceName") + " but got " + service.getServiceName(), attributes.get("serviceName").equals(service.getServiceName()));
    assertTrue("Expected the service ARN to be " + attributes.get("serviceArn") + " but got " + service.getServiceArn(), attributes.get("serviceArn").equals(service.getServiceArn()));
    assertTrue("Expected the role ARN of the service to be " + attributes.get("roleArn") + " but got " + service.getRoleArn(), attributes.get("roleArn").equals(service.getRoleArn()));
    assertTrue("Expected the task definition of the service to be " + attributes.get("taskDefinition") + " but got " + service.getTaskDefinition(), attributes.get("taskDefinition").equals(service.getTaskDefinition()));
    assertTrue("Expected the desired count of the service to be " + attributes.get("desiredCount") + " but got " + service.getDesiredCount(), (Integer) attributes.get("desiredCount") == service.getDesiredCount());
    assertTrue("Expected the maximum percent of the service to be " + attributes.get("maximumPercent") + " but got " + service.getMaximumPercent(), (Integer) attributes.get("maximumPercent") == service.getMaximumPercent());
    assertTrue("Expected the minimum healthy percent of the service to be " + attributes.get("minimumHealthyPercent") + " but got " + service.getMinimumHealthyPercent(), (Integer) attributes.get("minimumHealthyPercent") == service.getMinimumHealthyPercent());
    assertTrue("Expected the created at of the service to be " + attributes.get("createdAt") + " but got " + service.getCreatedAt(), (Long) attributes.get("createdAt") == service.getCreatedAt());
    assertTrue("Expected the service to have 1 load balancer but got " + service.getLoadBalancers().size(), service.getLoadBalancers().size() == 1);
    assertTrue("Expected the service to have load balancer " + loadBalancer + " but got " + service.getLoadBalancers().get(0), service.getLoadBalancers().get(0).equals(loadBalancer));
  }
}
