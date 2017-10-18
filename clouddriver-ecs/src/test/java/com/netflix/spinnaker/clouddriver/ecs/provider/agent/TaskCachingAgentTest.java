package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.junit.BeforeClass;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TaskCachingAgentTest {
  private static final String REGION = "us-west-2";
  private static final String ACCOUNT = "test-account";

  private static AmazonECS ecs = mock(AmazonECS.class);
  private static AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  private ProviderCache providerCache = mock(ProviderCache.class);
  private AWSCredentialsProvider credentialsProvider = mock(AWSCredentialsProvider.class);
  private Registry registry = mock(Registry.class);

  @Subject
  private TaskCachingAgent agent = new TaskCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider, registry);

  @BeforeClass
  public static void setUp() {
    when(clientProvider.getAmazonEcs(anyString(), any(AWSCredentialsProvider.class), anyString())).thenReturn(ecs);
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
