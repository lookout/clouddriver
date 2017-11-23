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

package com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer;

import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class EcsLoadBalancerSummaryByAccount implements LoadBalancerProvider.ByAccount{

  private String name;
  private Map<String, EcsLoadBalancerSummaryByRegion> byRegions;

  public List getByRegions() {
    return byRegions.values().stream().collect(Collectors.toList());
  }

  public EcsLoadBalancerSummaryByRegion getOrCreateRegions(String region){
    if(!byRegions.containsKey(region)){
      byRegions.put(region, new EcsLoadBalancerSummaryByRegion());
    }
    return byRegions.get(region);
  }
}
