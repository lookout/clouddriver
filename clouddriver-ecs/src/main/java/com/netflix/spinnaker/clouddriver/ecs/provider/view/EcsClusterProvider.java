/*
 *
 *  * Copyright 2017 Lookout, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

@Component
public class EcsClusterProvider {

  private Cache cacheView;

  @Autowired
  public EcsClusterProvider(Cache cacheView) {
    this.cacheView = cacheView;
  }

  public List<String> getEcsClusters(String account, String region) {
    Collection<CacheData> ecsClustersCache = cacheView.getAll(ECS_CLUSTERS.toString());

    if (ecsClustersCache == null) {
      return Collections.emptyList();
    }

    List<String> listCluster = ecsClustersCache
      .stream()
      .filter(cache -> cache.getAttributes().get("account").equals(account))
      .map(cache -> (String) cache.getAttributes().get("clusterName"))
      .collect(Collectors.toList());

    return listCluster;
  }

}
