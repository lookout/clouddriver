package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsRequest;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;


public class TaskDefinitionCachingAgentTest extends CommonCachingAgent {
  @Subject
  private TaskDefinitionCachingAgent agent = new TaskDefinitionCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldAddToCache() {
    //Given
    String taskDefinitionArn = "arn:aws:ecs:" + REGION + ":012345678910:service/";
    String key = Keys.getTaskDefinitionKey(ACCOUNT, REGION, taskDefinitionArn);

    TaskDefinition taskDefinition = new TaskDefinition();
    taskDefinition.setTaskDefinitionArn(taskDefinitionArn);
    taskDefinition.setContainerDefinitions(Collections.emptyList());

    when(ecs.listTaskDefinitions(any(ListTaskDefinitionsRequest.class))).thenReturn(new ListTaskDefinitionsResult().withTaskDefinitionArns(taskDefinitionArn));
    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class))).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(taskDefinition));

    //When
    CacheResult cacheResult = agent.loadData(providerCache);

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(Keys.Namespace.TASK_DEFINITIONS.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));
  }
}
