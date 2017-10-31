package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import lombok.Data;

@Data
public class ContainerInstance {
  String arn;
  String ec2InstanceId;
}
