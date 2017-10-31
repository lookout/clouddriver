package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ContainerInstanceCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class ContainerInstanceCacheClientTest extends CommonCacheClient {
  @Subject
  private ContainerInstanceCacheClient client = new ContainerInstanceCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    //Given
    String containerInstanceArn = "arn:aws:ecs:" + REGION + ":012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98";
    String key = Keys.getContainerInstanceKey(ACCOUNT, REGION, containerInstanceArn);

    ContainerInstance containerInstance = new ContainerInstance();
    containerInstance.setEc2InstanceId("i-deadbeef");
    containerInstance.setContainerInstanceArn(containerInstanceArn);

    Map<String, Object> attributes = ContainerInstanceCachingAgent.convertContainerInstanceToAttributes(ACCOUNT, REGION, containerInstance);
    when(cacheView.get(CONTAINER_INSTANCES.toString(), key)).thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    //When
    com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance ecsContainerInstance = client.get(key);

    //Then
    assertTrue("Expected the EC2 instance ID to be " + containerInstance.getEc2InstanceId() + " but got " + ecsContainerInstance.getEc2InstanceId(),
      containerInstance.getEc2InstanceId().equals(ecsContainerInstance.getEc2InstanceId()));

    assertTrue("Expected the container instance ARN to be " + containerInstance.getContainerInstanceArn() + " but got " + ecsContainerInstance.getArn(),
      containerInstance.getContainerInstanceArn().equals(ecsContainerInstance.getArn()));
  }
}
