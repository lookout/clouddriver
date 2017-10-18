package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;


public class EcsClusterCachingAgentTest extends CommonCachingAgent {
  @Subject
  private EcsClusterCachingAgent agent = new EcsClusterCachingAgent(ACCOUNT, REGION, clientProvider, credentialsProvider);

  @Test
  public void shouldAddToCache() {
    //Given
    String clusterName = "test-cluster";
    String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName);
    ListClustersResult listClustersResult = new ListClustersResult().withClusterArns("arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName);
    when(ecs.listClusters(any(ListClustersRequest.class))).thenReturn(listClustersResult);

    //When
    CacheResult cacheResult = agent.loadData(providerCache);

    //Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(Keys.Namespace.ECS_CLUSTERS.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue("Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey, retrievedKey.equals(key));
  }
}
