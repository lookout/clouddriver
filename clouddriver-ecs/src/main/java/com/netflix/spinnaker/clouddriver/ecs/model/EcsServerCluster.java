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

package com.netflix.spinnaker.clouddriver.ecs.model;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonTargetGroup;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;

import java.util.*;

public class EcsServerCluster implements Cluster {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public Set<ServerGroup> getServerGroups() {
    return null;
  }

  @Override
  public Set<LoadBalancer> getLoadBalancers() {
    return null;
  }

  String name;
  String type = "ecs";
  String accountName;

  Set<AmazonTargetGroup> targetGroups = Collections.synchronizedSet(new HashSet<AmazonTargetGroup>());
  Set<ServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<ServerGroup>());
  Set<LoadBalancer> loadBalancers = Collections.synchronizedSet(new HashSet<LoadBalancer>());
}
