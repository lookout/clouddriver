/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsClusterCachingAgent;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EcsClusterProviderTest {
  static final String REGION = "us-west-2";
  static final String ACCOUNT = "test-account";

  private Cache cacheView = mock(Cache.class);
  private EcsClusterProvider ecsClusterProvider = new EcsClusterProvider(cacheView);

  @Test
  public void shouldGetNoClusters() {
    //Given
    when(cacheView.filterIdentifiers(anyString(), anyString())).thenReturn(Collections.emptySet());

    //When
    List<String> ecsClusters = ecsClusterProvider.getEcsClusters(ACCOUNT, REGION);

    //Then
    assertTrue("Expected 0 cluster but got " + ecsClusters.size(), ecsClusters.size() == 0);
  }
  
  @Test
  public void shouldGetACluster() {
    //Given
    String clusterName = "test-cluster";
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName;
    String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName);

    Set<String> keys = new HashSet<>();
    keys.add(key);

    Map<String, Object> attributes = EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn);
    Collection<CacheData> cacheData = new HashSet<>();
    cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    when(cacheView.filterIdentifiers(anyString(), anyString())).thenReturn(keys);
    when(cacheView.getAll(ECS_CLUSTERS.toString(), keys)).thenReturn(cacheData);

    //When
    List<String> ecsClusters = ecsClusterProvider.getEcsClusters(ACCOUNT, REGION);

    //Then
    assertTrue("Expected 1 cluster but got " + ecsClusters.size(), ecsClusters.size() == 1);

    String retrievedClusterName = ecsClusters.get(0);
    assertTrue("Expected cluster name to be " + clusterName + " but got " + retrievedClusterName, clusterName.equals(retrievedClusterName));
  }

  @Test
  public void shouldGetMultipleClusters() {
    //Given
    int numberOfClusters = 5;
    Set<String> clusterNames = new HashSet<>();
    Collection<CacheData> cacheData = new HashSet<>();
    Set<String> keys = new HashSet<>();

    for (int x = 0; x < numberOfClusters; x++) {
      String clusterName = "test-cluster-" + x;
      String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName;
      String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName);

      keys.add(key);
      clusterNames.add(clusterName);

      Map<String, Object> attributes = EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn);
      cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    when(cacheView.filterIdentifiers(anyString(), anyString())).thenReturn(keys);
    when(cacheView.getAll(ECS_CLUSTERS.toString(), keys)).thenReturn(cacheData);

    //When
    List<String> ecsClusters = ecsClusterProvider.getEcsClusters(ACCOUNT, REGION);

    //Then
    assertTrue("Expected " + numberOfClusters + " cluster but got " + ecsClusters.size(), ecsClusters.size() == numberOfClusters);

    for (String retrievedClusterName : ecsClusters) {
      assertTrue("Expected cluster name to be in " + clusterNames + " but got " + retrievedClusterName, clusterNames.contains(retrievedClusterName));
    }
  }
}
