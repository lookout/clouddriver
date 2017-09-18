package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import lombok.Data;

@Data
public class TerminateAndDecrementServerGroupDescription extends AbstractECSDescription{
  String instance;
  String serverGroupName;
  String asgName;
}
