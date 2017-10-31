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

import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;


public class TaskCachingAgentTest extends CommonCachingAgent {
  @Subject
  private TaskCachingAgent agent = new TaskCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldGetListOfTasks() {
    //Given
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String taskArn1 = "arn:aws:ecs:" + REGION + ":012345678910:task/1dc5c17a-422b-4dc4-b493-371970c6c4d6";
    String taskArn2 = "arn:aws:ecs:" + REGION + ":012345678910:task/deadbeef-422b-4dc4-b493-371970c6c4d6";

    ListTasksResult listTasksResult = new ListTasksResult().withTaskArns(taskArn1, taskArn2);
    when(ecs.listTasks(any(ListTasksRequest.class))).thenReturn(listTasksResult);

    List<Task> tasks = new LinkedList<>();
    tasks.add(new Task().withTaskArn(taskArn1));
    tasks.add(new Task().withTaskArn(taskArn2));

    DescribeTasksResult describeResult = new DescribeTasksResult().withTasks(tasks);
    when(ecs.describeTasks(any(DescribeTasksRequest.class))).thenReturn(describeResult);

    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(new ListClustersResult().withClusterArns(clusterArn));

    //When
    List<Task> returnedTasks = agent.getItems(ecs, providerCache);

    //Then
    assertTrue("Expected the list to contain " + tasks.size() + " ECS tasks, but got " + returnedTasks.size(), returnedTasks.size() == tasks.size());
    for (Task task : returnedTasks) {
      assertTrue("Expected the task to be in  " + tasks + " list but it was not. The task is: " + task, tasks.contains(task));
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    //Given
    String taskId1 = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
    String taskId2 = "deadbeef-422b-4dc4-b493-371970c6c4d6";
    List<String> taskIDs = new LinkedList<>();
    taskIDs.add(taskId1);
    taskIDs.add(taskId2);

    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";

    String taskArn1 = "arn:aws:ecs:" + REGION + ":012345678910:task/" + taskId1;
    String taskArn2 = "arn:aws:ecs:" + REGION + ":012345678910:task/" + taskId2;
    List<String> taskArns = new LinkedList<>();
    taskArns.add(taskArn1);
    taskArns.add(taskArn2);

    List<Task> tasks = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (int x = 0; x < taskArns.size(); x++) {
      keys.add(Keys.getTaskKey(ACCOUNT, REGION, taskIDs.get(x)));

      tasks.add(new Task().withClusterArn(clusterArn)
        .withTaskArn(taskArns.get(x))
        .withContainerInstanceArn("arn:aws:ecs:" + REGION + ":012345678910:container/e09064f7-7361-4c87-8ab9-8d073bbdbcb9")
        .withGroup("test-service")
        .withContainers(Collections.emptyList())
        .withLastStatus("RUNNING")
        .withDesiredStatus("RUNNING")
        .withStartedAt(new Date()));
    }

    //When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(tasks);

    //Then
    assertTrue("Expected the data map to contain 2 namespaces, but it contains " + dataMap.keySet().size() + " namespaces.", dataMap.keySet().size() == 2);
    assertTrue("Expected the data map to contain " + TASKS.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(TASKS.toString()));
    assertTrue("Expected the data map to contain " + ECS_CLUSTERS.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(ECS_CLUSTERS.toString()));
    assertTrue("Expected there to be 2 CacheData, instead there is  " + dataMap.get(TASKS.toString()).size(), dataMap.get(TASKS.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(TASKS.toString())) {
      assertTrue("Expected the key to be one of the following keys: " + keys.toString() + ". The key is: " + cacheData.getId() + ".", keys.contains(cacheData.getId()));
      assertTrue("Expected the task ARN to be one of the following ARNs: " + taskArns.toString() + ". The task ARN is: " + cacheData.getAttributes().get("taskArn") + ".", taskArns.contains(cacheData.getAttributes().get("taskArn")));
    }
  }

  @Test
  public void shouldAddToCache() {
    //Given
    String taskId = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
    String key = Keys.getTaskKey(ACCOUNT, REGION, taskId);
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String taskArn = "arn:aws:ecs:" + REGION + ":012345678910:task/" + taskId;

    Task task = new Task();
    task.setTaskArn(taskArn);
    task.setClusterArn(clusterArn);
    task.setContainerInstanceArn("arn:aws:ecs:" + REGION + ":012345678910:container/e09064f7-7361-4c87-8ab9-8d073bbdbcb9");
    task.setGroup("test-service");
    task.setContainers(Collections.emptyList());
    task.setLastStatus("RUNNING");
    task.setDesiredStatus("RUNNING");
    task.setStartedAt(new Date());

    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(new ListClustersResult().withClusterArns(clusterArn));
    when(ecs.listTasks(any(ListTasksRequest.class))).thenReturn(new ListTasksResult().withTaskArns(taskArn));
    when(ecs.describeTasks(any(DescribeTasksRequest.class))).thenReturn(new DescribeTasksResult().withTasks(task));

    //When
    CacheResult cacheResult = agent.loadData(providerCache);

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(Keys.Namespace.TASKS.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));
  }
}