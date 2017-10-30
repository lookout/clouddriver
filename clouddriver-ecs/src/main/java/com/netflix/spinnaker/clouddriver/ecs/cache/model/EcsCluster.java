package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import lombok.Data;

@Data
public class EcsCluster {
  String account;
  String region;
  String name;
  String arn;
}
