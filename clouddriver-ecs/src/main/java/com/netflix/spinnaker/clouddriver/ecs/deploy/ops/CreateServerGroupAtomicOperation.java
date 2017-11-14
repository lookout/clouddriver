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
import com.amazonaws.services.applicationautoscaling.model.AdjustmentType;
import com.amazonaws.services.applicationautoscaling.model.PolicyType;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.applicationautoscaling.model.StepAdjustment;
import com.amazonaws.services.applicationautoscaling.model.StepScalingPolicyConfiguration;
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
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CREATE_ECS_SERVER_GROUP";

  private final CreateServerGroupDescription description;

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  ContainerInformationService containerInformationService;

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

    updateTaskStatus("Creating Amazon ECS Task Definition...");
    TaskDefinition taskDefinition = registerTaskDefinition(ecs);
    updateTaskStatus("Done creating Amazon ECS Task Definition...");

    String ecsServiceRole = inferAssumedRoleArn(credentials);
    Service service = createService(ecs, taskDefinition, ecsServiceRole);

    String resourceId = createAutoScalingGroup(autoScalingClient, credentials, service);
    associateAsgWithMetrics(cloudWatch, autoScalingClient, service.getServiceName(), resourceId);

    return makeDeploymentResult(service);
  }

  private void associateAsgWithMetrics(AmazonCloudWatch cloudWatch,
                                       AWSApplicationAutoScaling autoScalingClient,
                                       String serviceName,
                                       String resourceId) {
    //TODO: Potentially cache all/whole MetricAlarms.
    DescribeAlarmsRequest describeAlarmsRequest = new DescribeAlarmsRequest()
      .withAlarmNames(description.getAutoscalingPolicies().stream()
        .map(MetricAlarm::getAlarmName)
        .collect(Collectors.toList()));
    DescribeAlarmsResult describeAlarmsResult = cloudWatch.describeAlarms(describeAlarmsRequest);

    for (MetricAlarm metricAlarm : describeAlarmsResult.getMetricAlarms()) {
      String policyName = serviceName + "-scaling-policy-" + metricAlarm.getAlarmName();
      String policyArn = putScalingPolicy(autoScalingClient, resourceId, policyName);
      cloudWatch.putMetricAlarm(buildPutMetricAlarmRequest(metricAlarm, policyArn));
    }
  }

  private String putScalingPolicy(AWSApplicationAutoScaling autoScalingClient,
                                  String resourceId,
                                  String policyName) {

    StepAdjustment stepAdjustment1 = new StepAdjustment()
      .withScalingAdjustment(1)
      .withMetricIntervalUpperBound(0.0d);

    StepScalingPolicyConfiguration stepScalingPolicyConfiguration = new StepScalingPolicyConfiguration()
      .withAdjustmentType(AdjustmentType.ChangeInCapacity)
      .withCooldown(60)
      .withStepAdjustments(stepAdjustment1);

    PutScalingPolicyRequest putScalingPolicyRequest = new PutScalingPolicyRequest()
      .withResourceId(resourceId)
      .withPolicyName(policyName)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withServiceNamespace(ServiceNamespace.Ecs)
      .withPolicyType(PolicyType.StepScaling)
      .withStepScalingPolicyConfiguration(stepScalingPolicyConfiguration);

    updateTaskStatus("Putting Scaling Policy with the name " + policyName);
    PutScalingPolicyResult result = autoScalingClient.putScalingPolicy(putScalingPolicyRequest);
    updateTaskStatus("Done putting Scaling Policy with the name " + policyName);
    return result.getPolicyARN();
  }

  private PutMetricAlarmRequest buildPutMetricAlarmRequest(MetricAlarm metricAlarm, String policyArn) {
    return new PutMetricAlarmRequest()
      //add +"-new" or w/e to make new MetricAlarms
      .withAlarmName(metricAlarm.getAlarmName())
      .withEvaluationPeriods(metricAlarm.getEvaluationPeriods())
      .withThreshold(metricAlarm.getThreshold())
      .withActionsEnabled(metricAlarm.getActionsEnabled())
      .withAlarmDescription(metricAlarm.getAlarmDescription())
      .withComparisonOperator(metricAlarm.getComparisonOperator())
      .withDimensions(metricAlarm.getDimensions())
      .withMetricName(metricAlarm.getMetricName())
      .withUnit(metricAlarm.getUnit())
      .withOKActions(metricAlarm.getOKActions())
      .withPeriod(metricAlarm.getPeriod())
      .withNamespace(metricAlarm.getNamespace())
      .withStatistic(metricAlarm.getStatistic())
      .withEvaluateLowSampleCountPercentile(metricAlarm.getEvaluateLowSampleCountPercentile())
      .withInsufficientDataActions(metricAlarm.getInsufficientDataActions())
      .withTreatMissingData(metricAlarm.getTreatMissingData())
      .withExtendedStatistic(metricAlarm.getExtendedStatistic())
      .withAlarmActions(policyArn);
  }

  private TaskDefinition registerTaskDefinition(AmazonECS ecs) {
    String serverGroupVersion = inferNextServerGroupVersion();

    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();
    containerEnvironment.add(new KeyValuePair().withName("SERVER_GROUP").withValue(serverGroupVersion));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_STACK").withValue(description.getStack()));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_DETAIL").withValue(description.getFreeFormDetails()));

    PortMapping portMapping = new PortMapping()
      .withHostPort(0)
      .withContainerPort(description.getContainerPort())
      .withProtocol(description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

    Collection<PortMapping> portMappings = new LinkedList<>();
    portMappings.add(portMapping);

    ContainerDefinition containerDefinition = new ContainerDefinition()
      .withName(serverGroupVersion)
      .withEnvironment(containerEnvironment)
      .withPortMappings(portMappings)
      .withCpu(description.getComputeUnits())
      .withMemoryReservation(description.getReservedMemory())
      .withImage(description.getDockerImageAddress());

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(containerDefinition);

    RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
      .withContainerDefinitions(containerDefinitions)
      .withFamily(getFamilyName())
      .withTaskRoleArn(description.getIamRole());

    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(request);

    return registerTaskDefinitionResult.getTaskDefinition();
  }

  private Service createService(AmazonECS ecs, TaskDefinition taskDefinition, String ecsServiceRole) {
    String serviceName = getNextServiceName();
    Collection<LoadBalancer> loadBalancers = new LinkedList<>();
    loadBalancers.add(retrieveLoadBalancer());

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

  private LoadBalancer retrieveLoadBalancer() {
    AmazonCredentials credentials = getCredentials();
    String region = getRegion();
    String versionString = inferNextServerGroupVersion();

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(versionString);
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

  private String getNextServiceName() {
    String familyName = getFamilyName();
    String versionString = inferNextServerGroupVersion();
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

  private String inferNextServerGroupVersion() {
    int version = containerInformationService.getLatestServiceVersion(description.getEcsClusterName(), getFamilyName(), description.getCredentialAccount(), description.getRegion());
    return String.format("v%04d", (version + 1));
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
