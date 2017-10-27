/*
 * * Copyright 2017 Lookout, Inc.
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public abstract class AbstractEcsCachingAgent<T> implements CachingAgent {
  protected AmazonClientProvider amazonClientProvider;
  protected AWSCredentialsProvider awsCredentialsProvider;
  protected String region;
  protected String accountName;

  public AbstractEcsCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
  }

  /**
   * Fetches items to be stored from the AWS API
   *
   * @param ecs
   * @param providerCache
   * @return
   */
  protected abstract List<T> getItems(AmazonECS ecs, ProviderCache providerCache);

  /**
   * Transforms raw cached objects as cache-storeable objects
   *
   * @param items
   * @param providerCache
   * @return
   */
  protected abstract CacheResult buildCacheResult(List<T> items, ProviderCache providerCache);

  protected abstract Map<String, Collection<CacheData>> generateFreshData(Collection<T> cacheableItems);

  protected abstract Map<String, Collection<String>> computeEvictableData(Collection<CacheData> newData, Collection<String> oldKeys);

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);
    List<T> items = getItems(ecs, providerCache);
    return buildCacheResult(items, providerCache);
  }

  protected Set<String> getClusters(AmazonECS ecs, ProviderCache providerCache) {
    Set<String> clusters = providerCache.getAll(ECS_CLUSTERS.toString()).stream()
      .map(cacheData -> (String) cacheData.getAttributes().get("clusterArn"))
      .collect(Collectors.toSet());

    if (clusters == null || clusters.isEmpty()) {
      clusters = new HashSet<>();
      String nextToken = null;
      do {
        ListClustersRequest listClustersRequest = new ListClustersRequest();
        if (nextToken != null) {
          listClustersRequest.setNextToken(nextToken);
        }
        ListClustersResult listClustersResult = ecs.listClusters(listClustersRequest);
        clusters.addAll(listClustersResult.getClusterArns());

        nextToken = listClustersResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }

    return clusters;
  }
}
