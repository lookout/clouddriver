package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import com.amazonaws.services.ecs.model.LoadBalancer;
import lombok.Data;

import java.util.List;

@Data
public class Service {
  String account;
  String region;
  String applicationName;
  String serviceName;
  String serviceArn;
  String clusterName;
  String clusterArn;
  String roleArn;
  String taskDefinition;
  int desiredCount;
  int maximumPercent;
  int minimumHealthyPercent;
  List<LoadBalancer> loadBalancers;
  long createdAt;
}
