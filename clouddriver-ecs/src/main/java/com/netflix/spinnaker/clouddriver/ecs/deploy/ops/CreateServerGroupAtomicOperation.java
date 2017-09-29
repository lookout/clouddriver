package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
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

    TaskDefinition taskDefinition = registerTaskDefinition();
    createService(taskDefinition);
    createAutoScalingGroup();

    return getDeploymentResult();
  }

  private TaskDefinition registerTaskDefinition() {
    AmazonECS ecs = getAmazonEcsClient();
    String serverGroupVersion = inferServerGroupVersion();

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
      .withFamily(getFamilyName());

    updateTaskStatus("Creating Amazon ECS Task Definition...");
    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(request);
    updateTaskStatus("Done creating Amazon ECS Task Definition...");

    return registerTaskDefinitionResult.getTaskDefinition();
  }

  private void createService(TaskDefinition taskDefinition) {
    AmazonECS ecs = getAmazonEcsClient();
    String serviceName = getServiceName();
    Collection<LoadBalancer> loadBalancers = new LinkedList<>();
    loadBalancers.add(getLoadBalancer());

    Integer desiredCapacity = getDesiredCapacity();
    String taskDefinitionArn = taskDefinition.getTaskDefinitionArn();

    DeploymentConfiguration deploymentConfiguration = new DeploymentConfiguration()
      .withMinimumHealthyPercent(50)
      .withMaximumPercent(100);

    CreateServiceRequest request = new CreateServiceRequest()
      .withServiceName(serviceName)
      .withDesiredCount(desiredCapacity != null ? desiredCapacity : 1)
      .withCluster(description.getEcsClusterName())
      .withRole(description.getIamRole())
      .withLoadBalancers(loadBalancers)
      .withTaskDefinition(taskDefinitionArn)
      .withDeploymentConfiguration(deploymentConfiguration);

    updateTaskStatus(String.format("Creating %s of %s with %s for %s.",
      desiredCapacity, serviceName, taskDefinitionArn, description.getCredentialAccount()));

    ecs.createService(request);

    updateTaskStatus(String.format("Done creating %s of %s with %s for %s.",
      desiredCapacity, serviceName, taskDefinitionArn, description.getCredentialAccount()));
  }

  private void createAutoScalingGroup() {
    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();
    RegisterScalableTargetRequest request = new RegisterScalableTargetRequest()
      .withServiceNamespace(ServiceNamespace.Ecs)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withResourceId(String.format("service/%s/%s", description.getEcsClusterName(), getServiceName()))
      .withMinCapacity(0)
      .withMaxCapacity(getDesiredCapacity());

    updateTaskStatus("Creating Amazon Application Auto Scaling Scalable Target Definition...");
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus("Done creating Amazon Application Auto Scaling Scalable Target Definition...");
  }

  private DeploymentResult getDeploymentResult() {
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(getRegion(), getServiceName());

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(Arrays.asList(getServerGroupName()));
    result.setServerGroupNameByRegion(namesByRegion);
    return result;
  }

  private LoadBalancer getLoadBalancer() {
    AmazonCredentials credentials = getCredentials();
    String region = getRegion();
    String versionString = inferServerGroupVersion();

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(versionString);
    loadBalancer.setContainerPort(description.getContainerPort());

    if (description.getTargetGroups().size() == 1) {
      AmazonElasticLoadBalancing loadBalancingV2 = amazonClientProvider.getAmazonElasticLoadBalancingV2(
        description.getCredentialAccount(),
        credentials.getCredentialsProvider(),
        region);
      String targetGroupName = description.getTargetGroups().get(0);  // TODO - make target group a single value field in Deck instead of an array here
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

  private AmazonECS getAmazonEcsClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = getRegion();
    String credentialAccount = description.getCredentialAccount();

    return amazonClientProvider.getAmazonEcs(credentialAccount, credentialsProvider, region);
  }

  private String getServerGroupName() {
    // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this
    return getRegion() + ":" + getServiceName();
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

  private String getServiceName() {
    String familyName = getFamilyName();
    String versionString = inferServerGroupVersion();
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

  private String inferServerGroupVersion() {
    int version = containerInformationService.getLatestServiceVersion(description.getEcsClusterName(), getFamilyName(), description.getCredentialAccount(), description.getRegion());
    return String.format("v%04d", (version+ 1));
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
