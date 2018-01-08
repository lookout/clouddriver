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
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesResult;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ScalingPolicy;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
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
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.AssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CreateServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CREATE_ECS_SERVER_GROUP";

  private final CreateServerGroupDescription description;

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  public CreateServerGroupAtomicOperation(CreateServerGroupDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Create Amazon ECS Server Group Operation...");

    String region = getRegion();
    String credentialAccount = description.getCredentialAccount();
    AmazonCredentials credentials = getCredentials();
    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();
    AmazonCloudWatch cloudWatch = getAmazonCloudWatchClient();


    AmazonECS ecs = getAmazonEcsClient(region, credentialAccount);

    String serverGroupVersion = inferNextServerGroupVersion(ecs);

    updateTaskStatus("Creating Amazon ECS Task Definition...");
    TaskDefinition taskDefinition = registerTaskDefinition(ecs, serverGroupVersion);
    updateTaskStatus("Done creating Amazon ECS Task Definition...");

    String ecsServiceRole = inferAssumedRoleArn(credentials);
    Service service = createService(ecs, taskDefinition, ecsServiceRole, serverGroupVersion);

    String resourceId = createAutoScalingGroup(autoScalingClient, credentials, service);
    associateAsgWithMetrics(cloudWatch, autoScalingClient, service.getServiceName(), resourceId);

    return makeDeploymentResult(service);
  }

  private void associateAsgWithMetrics(AmazonCloudWatch cloudWatch,
                                       AWSApplicationAutoScaling autoScalingClient,
                                       String serviceName,
                                       String resourceId) {
    DescribeAlarmsRequest describeAlarmsRequest = new DescribeAlarmsRequest()
      .withAlarmNames(description.getAutoscalingPolicies().stream()
        .map(MetricAlarm::getAlarmName)
        .collect(Collectors.toList()));
    DescribeAlarmsResult describeAlarmsResult = cloudWatch.describeAlarms(describeAlarmsRequest);

    for (MetricAlarm metricAlarm : describeAlarmsResult.getMetricAlarms()) {
      Set<String> okScalingPolicyArns = putScalingPolicies(autoScalingClient, metricAlarm.getOKActions(),
        serviceName, resourceId, "ok", "scaling-policy-" + metricAlarm.getAlarmName());
      Set<String> alarmScalingPolicyArns = putScalingPolicies(autoScalingClient, metricAlarm.getAlarmActions(),
        serviceName, resourceId, "alarm", "scaling-policy-" + metricAlarm.getAlarmName());
      Set<String> insufficientActionPolicyArns = putScalingPolicies(autoScalingClient, metricAlarm.getInsufficientDataActions(),
        serviceName, resourceId, "insuffiicient", "scaling-policy-" + metricAlarm.getAlarmName());

      cloudWatch.putMetricAlarm(buildPutMetricAlarmRequest(metricAlarm, serviceName,
        insufficientActionPolicyArns, okScalingPolicyArns, alarmScalingPolicyArns));
    }
  }

  private Set<String> putScalingPolicies(AWSApplicationAutoScaling autoScalingClient,
                                         List<String> actionArns,
                                         String serviceName,
                                         String resourceId,
                                         String type,
                                         String suffix) {
    if (actionArns.isEmpty()) {
      return Collections.emptySet();
    }

    Set<ScalingPolicy> scalingPolicies = new HashSet<>();

    String nextToken = null;
    do {
      DescribeScalingPoliciesRequest request = new DescribeScalingPoliciesRequest().withPolicyNames(actionArns.stream()
        .filter(arn -> arn.contains(":policyName/"))
        .map(arn -> StringUtils.substringAfterLast(arn, ":policyName/"))
        .collect(Collectors.toSet()))
        .withServiceNamespace(ServiceNamespace.Ecs);
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      DescribeScalingPoliciesResult result = autoScalingClient.describeScalingPolicies(request);
      scalingPolicies.addAll(result.getScalingPolicies());

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    Set<String> policyArns = new HashSet<>();
    for (ScalingPolicy scalingPolicy : scalingPolicies) {
      String newPolicyName = serviceName + "-" + type + "-" + suffix;
      ScalingPolicy clone = scalingPolicy.clone();
      clone.setPolicyName(newPolicyName);
      clone.setResourceId(resourceId);

      updateTaskStatus("Putting Scaling Policy with the name " + newPolicyName);
      PutScalingPolicyResult result = autoScalingClient.putScalingPolicy(buildPutScalingPolicyRequest(clone));
      updateTaskStatus("Done putting Scaling Policy with the name " + newPolicyName);
      policyArns.add(result.getPolicyARN());
    }

    return policyArns;
  }

  private PutScalingPolicyRequest buildPutScalingPolicyRequest(ScalingPolicy policy) {
    return new PutScalingPolicyRequest()
      .withPolicyName(policy.getPolicyName())
      .withServiceNamespace(policy.getServiceNamespace())
      .withPolicyType(policy.getPolicyType())
      .withResourceId(policy.getResourceId())
      .withScalableDimension(policy.getScalableDimension())
      .withStepScalingPolicyConfiguration(policy.getStepScalingPolicyConfiguration())
      .withTargetTrackingScalingPolicyConfiguration(policy.getTargetTrackingScalingPolicyConfiguration());
  }

  private PutMetricAlarmRequest buildPutMetricAlarmRequest(MetricAlarm metricAlarm,
                                                           String serviceName,
                                                           Set<String> insufficientActionPolicyArns,
                                                           Set<String> okActionPolicyArns,
                                                           Set<String> alarmActionPolicyArns) {
    return new PutMetricAlarmRequest()
      .withAlarmName(metricAlarm.getAlarmName() + "-" + serviceName)
      .withEvaluationPeriods(metricAlarm.getEvaluationPeriods())
      .withThreshold(metricAlarm.getThreshold())
      .withActionsEnabled(metricAlarm.getActionsEnabled())
      .withAlarmDescription(metricAlarm.getAlarmDescription())
      .withComparisonOperator(metricAlarm.getComparisonOperator())
      .withDimensions(metricAlarm.getDimensions())
      .withMetricName(metricAlarm.getMetricName())
      .withUnit(metricAlarm.getUnit())
      .withPeriod(metricAlarm.getPeriod())
      .withNamespace(metricAlarm.getNamespace())
      .withStatistic(metricAlarm.getStatistic())
      .withEvaluateLowSampleCountPercentile(metricAlarm.getEvaluateLowSampleCountPercentile())
      .withTreatMissingData(metricAlarm.getTreatMissingData())
      .withExtendedStatistic(metricAlarm.getExtendedStatistic())
      .withInsufficientDataActions(insufficientActionPolicyArns)
      .withOKActions(okActionPolicyArns)
      .withAlarmActions(alarmActionPolicyArns);
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

    Integer desiredCapacity = getDesiredCapacity();
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

  private String createAutoScalingGroup(AWSApplicationAutoScaling autoScalingClient,
                                        AmazonCredentials credentials,
                                        Service service) {
    String assumedRoleArn = inferAssumedRoleArn(credentials);

    RegisterScalableTargetRequest request = new RegisterScalableTargetRequest()
      .withServiceNamespace(ServiceNamespace.Ecs)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withResourceId(String.format("service/%s/%s", description.getEcsClusterName(), service.getServiceName()))
      .withRoleARN(assumedRoleArn)
      .withMinCapacity(0)
      .withMaxCapacity(getDesiredCapacity());

    updateTaskStatus("Creating Amazon Application Auto Scaling Scalable Target Definition...");
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus("Done creating Amazon Application Auto Scaling Scalable Target Definition.");

    return request.getResourceId();
  }

  private String inferAssumedRoleArn(AmazonCredentials credentials) {
    String role;
    if (credentials instanceof AssumeRoleAmazonCredentials) {
      role = ((AssumeRoleAmazonCredentials) credentials).getAssumeRole();
    } else if (credentials instanceof NetflixAssumeRoleAmazonCredentials) {
      role = ((NetflixAssumeRoleAmazonCredentials) credentials).getAssumeRole();
    } else if (credentials instanceof NetflixAssumeRoleEcsCredentials) {
      role = ((NetflixAssumeRoleEcsCredentials) credentials).getAssumeRole();
    } else {
      throw new UnsupportedOperationException("Support for this kind of credentials is not supported, " +
        "please report this issue to the Spinnaker project on Github");
    }

    String roleArn = String.format("arn:aws:iam::%s:%s", credentials.getAccountId(), role);


    return roleArn;
  }

  private DeploymentResult makeDeploymentResult(Service service) {
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(getRegion(), service.getServiceName());

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(Arrays.asList(getServerGroupName(service)));
    result.setServerGroupNameByRegion(namesByRegion);
    return result;
  }

  private LoadBalancer retrieveLoadBalancer(String version) {
    AmazonCredentials credentials = getCredentials();
    String region = getRegion();

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(version);
    loadBalancer.setContainerPort(description.getContainerPort());

    if (description.getTargetGroup() != null) {
      AmazonElasticLoadBalancing loadBalancingV2 = amazonClientProvider.getAmazonElasticLoadBalancingV2(
        description.getCredentialAccount(),
        credentials.getCredentialsProvider(),
        region);
      String targetGroupName = description.getTargetGroup();
      DescribeTargetGroupsRequest request = new DescribeTargetGroupsRequest().withNames(targetGroupName);
      DescribeTargetGroupsResult describeTargetGroupsResult = loadBalancingV2.describeTargetGroups(request);
      loadBalancer.setTargetGroupArn(describeTargetGroupsResult.getTargetGroups().get(0).getTargetGroupArn());
    }
    return loadBalancer;
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = getRegion();
    String credentialAccount = description.getCredentialAccount();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, credentialsProvider, region);
  }

  private AmazonCloudWatch getAmazonCloudWatchClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = getRegion();
    String credentialAccount = description.getCredentialAccount();

    return amazonClientProvider.getAmazonCloudWatch(credentialAccount, credentialsProvider, region);
  }

  private AmazonECS getAmazonEcsClient(String region, String account) {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();

    return amazonClientProvider.getAmazonEcs(account, credentialsProvider, region);
  }

  private String getServerGroupName(Service service) {
    // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this
    return getRegion() + ":" + service.getServiceName();
  }

  private void updateTaskStatus(String status) {
    getTask().updateStatus(BASE_PHASE, status);
  }

  private AmazonCredentials getCredentials() {
    return (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
  }

  private Integer getDesiredCapacity() {
    return description.getCapacity().getDesired();
  }

  private String getNextServiceName(String versionString) {
    String familyName = getFamilyName();
    return familyName + "-" + versionString;
  }

  private String getRegion() {
    hasValidRegion();
    return description.getAvailabilityZones().keySet().iterator().next();
  }

  private void hasValidRegion() {
    if (description.getAvailabilityZones().size() != 1) {
      String message = "You must specify exactly 1 region to be used.  You specified %s region(s)";
      throw new Error(String.format(message, description.getAvailabilityZones().size()));
    }
  }

  private String inferNextServerGroupVersion(AmazonECS ecs) {
    int latestVersion = 0;

    String nextToken = null;
    do {
      ListServicesRequest request = new ListServicesRequest().withCluster(description.getEcsClusterName());
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      ListServicesResult result = ecs.listServices(request);
      for (String serviceArn : result.getServiceArns()) {
        if (serviceArn.contains(getFamilyName())) {
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
