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
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;


public class TaskCacheTest extends CommonCachingAgent {
  @Subject
  private TaskCachingAgent agent = new TaskCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);
  @Subject
  private TaskCacheClient client = new TaskCacheClient(providerCache);

  @Test
  public void shouldRetrieveFromWrittenCache() {
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
    when(providerCache.get(TASKS.toString(), key)).thenReturn(cacheResult.getCacheResults().get(TASKS.toString()).iterator().next());

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(Keys.Namespace.TASKS.toString());
    com.netflix.spinnaker.clouddriver.ecs.cache.model.Task ecsTask = client.get(key);

    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));

    assertTrue("Expected the cluster ARN to be " + clusterArn + " but got " + ecsTask.getClusterArn(),
      clusterArn.equals(ecsTask.getClusterArn()));

    assertTrue("Expected the task ARN to be " + taskArn + " but got " + ecsTask.getTaskArn(),
      taskArn.equals(ecsTask.getTaskArn()));

    assertTrue("Expected the container instance ARN name to be " + task.getContainerInstanceArn() + " but got " + ecsTask.getContainerInstanceArn(),
      task.getContainerInstanceArn().equals(ecsTask.getContainerInstanceArn()));

    assertTrue("Expected the group to be " + task.getGroup() + " but got " + ecsTask.getGroup(),
      task.getGroup().equals(ecsTask.getGroup()));

    assertTrue("Expected the last status to be " + task.getLastStatus() + " but got " + ecsTask.getLastStatus(),
      task.getLastStatus().equals(ecsTask.getLastStatus()));

    assertTrue("Expected the desired status to be " + task.getDesiredStatus() + " but got " + ecsTask.getDesiredStatus(),
      task.getDesiredStatus().equals(ecsTask.getDesiredStatus()));

    assertTrue("Expected the started at to be " + task.getStartedAt().getTime() + " but got " + ecsTask.getStartedAt(),
      task.getStartedAt().getTime() == ecsTask.getStartedAt());

    assertTrue("Expected the task to have 0 containers but got " + task.getContainers().size(),
      task.getContainers().size() == 0);
  }
}
