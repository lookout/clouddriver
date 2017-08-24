package com.netflix.spinnaker.clouddriver.ecs.model;

import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class EcsInstance implements Instance, Serializable {
  private String name;
  private HealthState healthState;
  private Long launchTime;
  private String zone;
  private List<Map<String, String>> health;
  private String providerType;
  private String cloudProvider;

  @Override
  public String getZone() {
    return zone;
  }

  @Override
  public List<Map<String, String>> getHealth() {
    return health;
  }

  @Override
  public String getProviderType() {
    return providerType;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public Long getLaunchTime() {
    return launchTime;
  }

  @Override
  public HealthState getHealthState() {
    return healthState;
  }

  @Override
  public String getName() {
    return name;
  }

}
