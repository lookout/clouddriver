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

import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsRequest;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;


public class TaskDefinitionCachingAgentTest extends CommonCachingAgent {
  @Subject
  private TaskDefinitionCachingAgent agent = new TaskDefinitionCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldGetListOfContainerInstances() {
    //Given
    String taskDefinitionArn1 = "arn:aws:ecs:" + REGION + ":012345678910:task-definition/hello_world:10";

    ListTaskDefinitionsResult listTaskDefinitionsResult = new ListTaskDefinitionsResult().withTaskDefinitionArns(taskDefinitionArn1);
    when(ecs.listTaskDefinitions(any(ListTaskDefinitionsRequest.class))).thenReturn(listTaskDefinitionsResult);

    DescribeTaskDefinitionResult describeTaskDefinitionResult = new DescribeTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withTaskDefinitionArn(taskDefinitionArn1));
    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class))).thenReturn(describeTaskDefinitionResult);

    //When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    //Then
    assertTrue("Expected the list to contain 1 ECS task definition, but got " + returnedTaskDefs.size(), returnedTaskDefs.size() == 1);
    for (TaskDefinition taskDef : returnedTaskDefs) {
      assertTrue("Expected the task definition to be in  " + taskDef + " list but it was not. The task definition is: " + task, returnedTaskDefs.contains(taskDef));
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    //Given
    String taskDefinitionArn1 = "arn:aws:ecs:" + REGION + ":012345678910:task-definition/hello_world:10";
    String taskDefinitionArn2 = "arn:aws:ecs:" + REGION + ":012345678910:task-definition/hello_world:11";


    List<String> taskDefinitionArns = new LinkedList<>();
    taskDefinitionArns.add(taskDefinitionArn1);
    taskDefinitionArns.add(taskDefinitionArn2);

    List<TaskDefinition> tasks = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (String taskDefArn : taskDefinitionArns) {
      keys.add(Keys.getTaskDefinitionKey(ACCOUNT, REGION, taskDefArn));

      tasks.add(new TaskDefinition().withTaskDefinitionArn(taskDefArn)
        .withContainerDefinitions(Collections.emptyList()));
    }

    //When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(tasks);

    //Then
    assertTrue("Expected the data map to contain 2 namespaces, but it contains " + dataMap.keySet().size() + " namespaces.", dataMap.keySet().size() == 2);
    assertTrue("Expected the data map to contain " + TASK_DEFINITIONS.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(TASK_DEFINITIONS.toString()));
    assertTrue("Expected the data map to contain " + ECS_CLUSTERS.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(ECS_CLUSTERS.toString()));
    assertTrue("Expected there to be 2 CacheData, instead there is  " + dataMap.get(TASK_DEFINITIONS.toString()).size(), dataMap.get(TASK_DEFINITIONS.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(TASK_DEFINITIONS.toString())) {
      assertTrue("Expected the key to be one of the following keys: " + keys.toString() + ". The key is: " + cacheData.getId() + ".", keys.contains(cacheData.getId()));
      assertTrue("Expected the task definition ARN to be one of the following ARNs: " + taskDefinitionArns.toString() + ". The task definition  ARN is: " + cacheData.getAttributes().get("taskDefinitionArn") + ".", taskDefinitionArns.contains(cacheData.getAttributes().get("taskDefinitionArn")));
    }
  }

  @Test
  public void shouldAddToCache() {
    //Given
    String taskDefinitionArn = "arn:aws:ecs:" + REGION + ":012345678910:task-definition/hello_world:10";
    String key = Keys.getTaskDefinitionKey(ACCOUNT, REGION, taskDefinitionArn);

    TaskDefinition taskDefinition = new TaskDefinition();
    taskDefinition.setTaskDefinitionArn(taskDefinitionArn);
    taskDefinition.setContainerDefinitions(Collections.emptyList());

    when(ecs.listTaskDefinitions(any(ListTaskDefinitionsRequest.class))).thenReturn(new ListTaskDefinitionsResult().withTaskDefinitionArns(taskDefinitionArn));
    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class))).thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(taskDefinition));

    //When
    CacheResult cacheResult = agent.loadData(providerCache);

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(TASK_DEFINITIONS.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));
  }
}
