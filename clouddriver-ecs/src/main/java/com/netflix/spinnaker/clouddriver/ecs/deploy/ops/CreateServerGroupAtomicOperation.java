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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.AssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateServerGroupAtomicOperation extends AbstractEcsAtomicOperation<CreateServerGroupDescription, DeploymentResult> {
  @Autowired
  EcsCloudMetricService ecsCloudMetricService;

  public CreateServerGroupAtomicOperation(CreateServerGroupDescription description) {
    super(description, "CREATE_ECS_SERVER_GROUP");
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Create Amazon ECS Server Group Operation...");

    AmazonCredentials credentials = getCredentials();

    AmazonECS ecs = getAmazonEcsClient();

    String serverGroupVersion = inferNextServerGroupVersion(ecs);

    updateTaskStatus("Creating Amazon ECS Task Definition...");
    TaskDefinition taskDefinition = registerTaskDefinition(ecs, serverGroupVersion);
    updateTaskStatus("Done creating Amazon ECS Task Definition...");

    String ecsServiceRole = inferAssumedRoleArn(credentials);
    Service service = createService(ecs, taskDefinition, ecsServiceRole, serverGroupVersion);

    String resourceId = createAutoScalingGroup(credentials, service);
    List<String> alarmNames = description.getAutoscalingPolicies().stream()
      .map(MetricAlarm::getAlarmName)
      .collect(Collectors.toList());
    ecsCloudMetricService.associateAsgWithMetrics(description.getCredentialAccount(), getRegion(), alarmNames, service.getServiceName(), resourceId);

    return makeDeploymentResult(service);
  }

  private TaskDefinition registerTaskDefinition(AmazonECS ecs, String version) {

    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();
    containerEnvironment.add(new KeyValuePair().withName("SERVER_GROUP").withValue(version));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_STACK").withValue(description.getStack()));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_DETAIL").withValue(description.getFreeFormDetails()));

    PortMapping portMapping = new PortMapping()
      .withHostPort(0)
      .withContainerPort(description.getContainerPort())
      .withProtocol(description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

    Collection<PortMapping> portMappings = new LinkedList<>();
    portMappings.add(portMapping);

    ContainerDefinition containerDefinition = new ContainerDefinition()
      .withName(version)
      .withEnvironment(containerEnvironment)
      .withPortMappings(portMappings)
      .withCpu(description.getComputeUnits())
      .withMemoryReservation(description.getReservedMemory())
      .withImage(description.getDockerImageAddress());

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(containerDefinition);

    RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
      .withContainerDefinitions(containerDefinitions)
      .withFamily(getFamilyName());

    if (!description.getIamRole().equals("None (No IAM role)")) {
      request.setTaskRoleArn(description.getIamRole());
    }

    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(request);

    return registerTaskDefinitionResult.getTaskDefinition();
  }

  private Service createService(AmazonECS ecs, TaskDefinition taskDefinition, String ecsServiceRole, String version) {
    String serviceName = getNextServiceName(version);
    Collection<LoadBalancer> loadBalancers = new LinkedList<>();
    loadBalancers.add(retrieveLoadBalancer(version));

    Integer desiredCapacity = description.getCapacity().getDesired();
    String taskDefinitionArn = taskDefinition.getTaskDefinitionArn();

    DeploymentConfiguration deploymentConfiguration = new DeploymentConfiguration()
      .withMinimumHealthyPercent(50)
      .withMaximumPercent(100);

    CreateServiceRequest request = new CreateServiceRequest()
      .withServiceName(serviceName)
      .withDesiredCount(desiredCapacity != null ? desiredCapacity : 1)
      .withCluster(description.getEcsClusterName())
      .withRole(ecsServiceRole)
      .withLoadBalancers(loadBalancers)
      .withTaskDefinition(taskDefinitionArn)
      .withPlacementStrategy(description.getPlacementStrategySequence())
      .withDeploymentConfiguration(deploymentConfiguration);

    updateTaskStatus(String.format("Creating %s of %s with %s for %s.",
      desiredCapacity, serviceName, taskDefinitionArn, description.getCredentialAccount()));

    Service service = ecs.createService(request).getService();

    updateTaskStatus(String.format("Done creating %s of %s with %s for %s.",
      desiredCapacity, serviceName, taskDefinitionArn, description.getCredentialAccount()));

    return service;
  }

  private String createAutoScalingGroup(AmazonCredentials credentials,
                                        Service service) {

    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();
    String assumedRoleArn = inferAssumedRoleArn(credentials);

    RegisterScalableTargetRequest request = new RegisterScalableTargetRequest()
      .withServiceNamespace(ServiceNamespace.Ecs)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withResourceId(String.format("service/%s/%s", description.getEcsClusterName(), service.getServiceName()))
      .withRoleARN(assumedRoleArn)
      .withMinCapacity(0)
      .withMaxCapacity(description.getCapacity().getDesired());

    updateTaskStatus("Creating Amazon Application Auto Scaling Scalable Target Definition...");
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus("Done creating Amazon Application Auto Scaling Scalable Target Definition.");

    return request.getResourceId();
  }

  //TODO: do a trust relationship check
  private String inferAssumedRoleArn(AmazonCredentials credentials) {
    String role;
    if (credentials instanceof AssumeRoleAmazonCredentials) {
      role = ((AssumeRoleAmazonCredentials) credentials).getAssumeRole();
    } else if (credentials instanceof NetflixAssumeRoleAmazonCredentials) {
      role = ((NetflixAssumeRoleAmazonCredentials) credentials).getAssumeRole();
    } else if (credentials instanceof NetflixAssumeRoleEcsCredentials) {
      role = ((NetflixAssumeRoleEcsCredentials) credentials).getAssumeRole();
    } else {
      throw new UnsupportedOperationException("The given kind is not supported, " +
        "please report this issue to the Spinnaker project on Github");
    }

    String roleArn = String.format("arn:aws:iam::%s:%s", credentials.getAccountId(), role);


    return roleArn;
  }

  private DeploymentResult makeDeploymentResult(Service service) {
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(getRegion(), service.getServiceName());

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(Collections.singletonList(getServerGroupName(service)));
    result.setServerGroupNameByRegion(namesByRegion);
    return result;
  }

  private LoadBalancer retrieveLoadBalancer(String version) {
    AmazonCredentials credentials = getCredentials();

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(version);
    loadBalancer.setContainerPort(description.getContainerPort());

    if (description.getTargetGroup() != null) {
      AmazonElasticLoadBalancing loadBalancingV2 = amazonClientProvider.getAmazonElasticLoadBalancingV2(
        description.getCredentialAccount(),
        credentials.getCredentialsProvider(),
        getRegion());
      String targetGroupName = description.getTargetGroup();
      DescribeTargetGroupsRequest request = new DescribeTargetGroupsRequest().withNames(targetGroupName);
      DescribeTargetGroupsResult describeTargetGroupsResult = loadBalancingV2.describeTargetGroups(request);
      loadBalancer.setTargetGroupArn(describeTargetGroupsResult.getTargetGroups().get(0).getTargetGroupArn());
    }
    return loadBalancer;
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String credentialAccount = description.getCredentialAccount();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, credentialsProvider, getRegion());
  }

  private String getServerGroupName(Service service) {
    // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this
    return getRegion() + ":" + service.getServiceName();
  }

  private String getNextServiceName(String versionString) {
    return getFamilyName() + "-" + versionString;
  }

  private String getRegion() {
    return description.getAvailabilityZones().keySet().iterator().next();
  }

  private String inferNextServerGroupVersion(AmazonECS ecs) {
    int latestVersion = 0;
    String familyName = getFamilyName();

    String nextToken = null;
    do {
      ListServicesRequest request = new ListServicesRequest().withCluster(description.getEcsClusterName());
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      ListServicesResult result = ecs.listServices(request);
      for (String serviceArn : result.getServiceArns()) {
        if (serviceArn.contains(familyName)) {
          int currentVersion;
          try {
            currentVersion = Integer.parseInt(StringUtils.substringAfterLast(serviceArn, "-").replaceAll("v", ""));
          } catch (NumberFormatException e) {
            currentVersion = 0;
          }
          latestVersion = Math.max(currentVersion, latestVersion);
        }
      }

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return String.format("v%04d", (latestVersion + 1));
  }

  private String getFamilyName() {
    String familyName = description.getApplication();

    if (description.getStack() != null) {
      familyName += "-" + description.getStack();
    }
    if (description.getFreeFormDetails() != null) {
      familyName += "-" + description.getFreeFormDetails();
    }

    return familyName;
  }
}
