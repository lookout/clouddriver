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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;

@Component
public class EcsLoadbalancerCacheClient {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  public EcsLoadbalancerCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  public List<EcsLoadBalancerCache> findAll() {
    String searchKey = Keys.getLoadBalancerKey("*", "*", "*", "*", "*") + "*";
    Collection<String> loadbalancerKeys = cacheView.filterIdentifiers(LOAD_BALANCERS.getNs(), searchKey);

    Set<Map<String, Object>> loadbalancerAttributes = fetchLoadBalancerAttributes(loadbalancerKeys);

    List<EcsLoadBalancerCache> loadbalancers = convertToLoadbalancer(loadbalancerAttributes);

    return loadbalancers;
  }

  public Set<EcsLoadBalancerCache> findWithTargetGroups(Set<String> targetGroups){
    return findAll().stream()
      .filter(ecsLoadBalancerCache -> targetGroups.containsAll(ecsLoadBalancerCache.getTargetGroups()))
      .collect(Collectors.toSet());
  }

  private EcsLoadBalancerCache convertToTargetGroup(Map<String, Object> targetGroupAttributes) {
    EcsLoadBalancerCache ecsLoadBalancerCache = objectMapper.convertValue(targetGroupAttributes, EcsLoadBalancerCache.class);
    return ecsLoadBalancerCache;
  }

  private List<EcsLoadBalancerCache> convertToLoadbalancer(Collection<Map<String, Object>> targetGroupAttributes) {
    List<EcsLoadBalancerCache> ecsTargetGroups = new ArrayList<>();

    for (Map<String, Object> attributes : targetGroupAttributes) {
      ecsTargetGroups.add(convertToTargetGroup(attributes));
    }

    return ecsTargetGroups;
  }


  private Set<Map<String, Object>> fetchLoadBalancerAttributes(Collection<String> targetGroupKeys) {
    Set<CacheData> loadBalancers = fetchTargetGroups(targetGroupKeys);

    Set<CacheData> targetGroupsWithAtLeastOneLoadBalancer = loadBalancers
      .stream()
      .filter(loadbalancer -> loadbalancer.getRelationships().get("targetGroups") != null
        && loadbalancer.getRelationships().get("targetGroups").size() > 0)
      .collect(Collectors.toSet());

    Set<Map<String, Object>> loadbalancerAttributes = targetGroupsWithAtLeastOneLoadBalancer.stream()
      .map(lb -> {
        Map<String, String> parts = Keys.parse(lb.getId());
        lb.getAttributes().put("region", parts.get("region"));
        lb.getAttributes().put("account", parts.get("account"));
        lb.getAttributes().put("loadBalancerType", parts.get("loadBalancerType"));
        lb.getAttributes().put("targetGroups", lb.getRelationships().get("targetGroups").stream()
          .map(id -> Keys.parse(id).get("targetGroup"))
          .collect(Collectors.toSet())
        );
        return lb.getAttributes();
      })
      .collect(Collectors.toSet());

    //TODO - Transform the return of the above method into a list of items, with the correct nested items inside of it
    // TODO - Extract a lot of these commands to a LoadBalancerCacheReader
    // TODO - Implement all methods of the LoadBalancerProvider

    return loadbalancerAttributes;
  }


  private Set<Map<String, Object>> retrieveLoadbalancers(Set<String> loadbalancersAssociatedWithTargetGroups) {
    Collection<CacheData> loadbalancers = cacheView.getAll(LOAD_BALANCERS.getNs(), loadbalancersAssociatedWithTargetGroups);
    Set<Map<String, Object>> loadBalancerAttributes = loadbalancers
      .stream()
      .map(lb -> lb.getAttributes())
      .collect(Collectors.toSet());

    return loadBalancerAttributes;
  }


  private Set<String> inferAssociatedLoadBalancers(Set<CacheData> targetGroups) {
    Set<String> loadbalancersAssociatedWithTargetGroups = new HashSet<>();

    for (CacheData targetGroup : targetGroups) {
      Collection<String> relatedLoadbalancer = targetGroup.getRelationships().get("loadbalancer");
      if (relatedLoadbalancer != null && relatedLoadbalancer.size() > 0) {
        for (String loadbalancerArn : relatedLoadbalancer) {
          loadbalancersAssociatedWithTargetGroups.add(loadbalancerArn);
        }
      }
    }
    return loadbalancersAssociatedWithTargetGroups;
  }

  private Set<CacheData> fetchTargetGroups(Collection<String> targetGroupKeys) {
    return cacheView
      .getAll(LOAD_BALANCERS.getNs(), targetGroupKeys, RelationshipCacheFilter.include(TARGET_GROUPS.getNs()))
      .stream()
      .collect(Collectors.toSet());
  }
}
