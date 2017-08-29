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

import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;

import java.util.Map;
import java.util.Set;

public class EcsServerGroup implements ServerGroup {

  String name;
  String type;
  String cloudProvider;
  String region;
  Boolean isDisabled;
  Long createdTime;
  Set<String> zones;
  Set<Instance> instances;
  Set<String> loadBalancers;
  Set<String> securityGroups;
  Map<String, Object> launchConfig;
  InstanceCounts instanceCounts;
  Capacity capacity;
  ImagesSummary imagesSummary;
  ImageSummary imageSummary;

  public EcsServerGroup() {

  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public String getRegion() {
    return region;
  }

  public Boolean getDisabled() {
    return isDisabled;
  }

  @Override
  public Long getCreatedTime() {
    return createdTime;
  }

  @Override
  public Set<String> getZones() {
    return zones;
  }

  @Override
  public Set<Instance> getInstances() {
    return instances;
  }

  @Override
  public Set<String> getLoadBalancers() {
    return loadBalancers;
  }

  @Override
  public Set<String> getSecurityGroups() {
    return securityGroups;
  }

  @Override
  public Map<String, Object> getLaunchConfig() {
    return launchConfig;
  }

  @Override
  public InstanceCounts getInstanceCounts() {
    return instanceCounts;
  }

  @Override
  public Capacity getCapacity() {
    return capacity;
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return imagesSummary;
  }

  @Override
  public ImageSummary getImageSummary() {
    return imageSummary;
  }

  @Override
  public Boolean isDisabled() {
    return null;
  }

  @Override
  public Map<String, Object> getTags() {
    return null;
  }

  public EcsServerGroup withName(String name) {
    this.name = name;
    return this;
  }

  public EcsServerGroup withType(String type) {
    this.type = type;
    return this;
  }

  public EcsServerGroup withCloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
    return this;
  }

  public EcsServerGroup withRegion(String region) {
    this.region = region;
    return this;
  }

  public void withDisabled(Boolean disabled) {
    isDisabled = disabled;
  }

  public EcsServerGroup withCreatedTime(Long createdTime) {
    this.createdTime = createdTime;
    return this;
  }

  public EcsServerGroup withZones(Set<String> zones) {
    this.zones = zones;
    return this;
  }

  public EcsServerGroup withInstances(Set<Instance> instances) {
    this.instances = instances;
    return this;
  }

  public EcsServerGroup withLoadBalancers(Set<String> loadBalancers) {
    this.loadBalancers = loadBalancers;
    return this;
  }

  public EcsServerGroup withSecurityGroups(Set<String> securityGroups) {
    this.securityGroups = securityGroups;
    return this;
  }

  public EcsServerGroup withLaunchConfig(Map<String, Object> launchConfig) {
    this.launchConfig = launchConfig;
    return this;
  }

  public EcsServerGroup withInstanceCounts(InstanceCounts instanceCounts) {
    this.instanceCounts = instanceCounts;
    return this;
  }

  public EcsServerGroup withCapacity(Capacity capacity) {
    this.capacity = capacity;
    return this;
  }

  public EcsServerGroup withImagesSummary(ImagesSummary imagesSummary) {
    this.imagesSummary = imagesSummary;
    return this;
  }

  public EcsServerGroup withImageSummary(ImageSummary imageSummary) {
    this.imageSummary = imageSummary;
    return this;
  }

  public EcsServerGroup withIsDisabled(Boolean isDisabled) {
    this.isDisabled = isDisabled;
    return this;
  }

}
