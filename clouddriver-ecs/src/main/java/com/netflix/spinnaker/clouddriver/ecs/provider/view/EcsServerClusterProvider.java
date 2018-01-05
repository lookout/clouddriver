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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.model.TaskDefinition;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EcsServerClusterProvider implements ClusterProvider<EcsServerCluster> {

  private final TaskCacheClient taskCacheClient;
  private final ServiceCacheClient serviceCacheClient;
  private final TaskDefinitionCacheClient taskDefinitionCacheClient;
  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;
  private final EcsCloudWatchAlarmCacheClient ecsCloudWatchAlarmCacheClient;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AmazonClientProvider amazonClientProvider;
  private final ContainerInformationService containerInformationService;

  @Autowired
  public EcsServerClusterProvider(AccountCredentialsProvider accountCredentialsProvider,
                                  AmazonClientProvider amazonClientProvider,
                                  ContainerInformationService containerInformationService,
                                  TaskCacheClient taskCacheClient,
                                  ServiceCacheClient serviceCacheClient,
                                  EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient,
                                  TaskDefinitionCacheClient taskDefinitionCacheClient,
                                  EcsCloudWatchAlarmCacheClient ecsCloudWatchAlarmCacheClient) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    this.containerInformationService = containerInformationService;
    this.taskCacheClient = taskCacheClient;
    this.serviceCacheClient = serviceCacheClient;
    this.taskDefinitionCacheClient = taskDefinitionCacheClient;
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
    this.ecsCloudWatchAlarmCacheClient = ecsCloudWatchAlarmCacheClient;
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusters() {
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AmazonCredentials credentials : getEcsCredentials()) {
      clusterMap = findClusters(clusterMap, credentials);
    }
    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClusters(Map<String, Set<EcsServerCluster>> clusterMap,
                                                          AmazonCredentials credentials) {
    return findClusters(clusterMap, credentials, null);
  }

  private Map<String, Set<EcsServerCluster>> findClusters(Map<String, Set<EcsServerCluster>> clusterMap,
                                                          AmazonCredentials credentials,
                                                          String application) {
    for (AmazonCredentials.AWSRegion awsRegion : credentials.getRegions()) {
      clusterMap = findClustersForRegion(clusterMap, credentials, awsRegion, application);
    }

    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClustersForRegion(Map<String, Set<EcsServerCluster>> clusterMap,
                                                                   AmazonCredentials credentials,
                                                                   AmazonCredentials.AWSRegion awsRegion,
                                                                   String application) {

    Collection<Service> services = serviceCacheClient.getAll(credentials.getName(), awsRegion.getName());
    Collection<Task> allTasks = taskCacheClient.getAll();

    for (Service service : services) {
      String serviceArn = service.getServiceArn();
      String serviceName = service.getServiceName();
      ServiceMetadata metadata = extractMetadataFromServiceArn(serviceArn);

      if ((null != application) && (!application.equals(metadata.getApplicationName()))) {
        continue;
      }

      Set<LoadBalancer> loadBalancers = new HashSet<>(ecsLoadbalancerCacheClient.findAll());

      Set<Instance> instances = allTasks.stream()
        .filter(task -> task.getGroup().equals("service:" + serviceName))
        .map(task -> convertToEcsTask(credentials.getName(), awsRegion.getName(), serviceName, task))
        .collect(Collectors.toSet());

      String taskDefinitionKey = Keys.getTaskDefinitionKey(credentials.getName(), awsRegion.getName(), service.getTaskDefinition());
      com.amazonaws.services.ecs.model.TaskDefinition taskDefinition = taskDefinitionCacheClient.get(taskDefinitionKey);
      if (taskDefinition == null) {
        continue;
      }

      EcsServerGroup ecsServerGroup = buildEcsServerGroup(credentials, awsRegion.getName(),
        serviceName, service.getDesiredCount(), metadata, instances, service.getCreatedAt(),
        service.getClusterName(), taskDefinition);

      if (clusterMap.containsKey(metadata.applicationName)) {
        String escClusterName = removeVersion(ecsServerGroup.getName());
        boolean found = false;

        for (EcsServerCluster cluster : clusterMap.get(metadata.applicationName)) {
          if (cluster.getName().equals(escClusterName)) {
            cluster.getServerGroups().add(ecsServerGroup);
            found = true;
            break;
          }
        }

        if (!found) {
          EcsServerCluster spinnakerCluster = buildSpinnakerServerCluster(credentials, loadBalancers, ecsServerGroup);
          clusterMap.get(metadata.applicationName).add(spinnakerCluster);
        }
      } else {
        EcsServerCluster spinnakerCluster = buildSpinnakerServerCluster(credentials, loadBalancers, ecsServerGroup);
        clusterMap.put(metadata.applicationName, Sets.newHashSet(spinnakerCluster));
      }
    }

    return clusterMap;
  }

  private EcsTask convertToEcsTask(String account, String region, String serviceName, Task task) {
    String taskId = task.getTaskId();
    Long launchTime = task.getStartedAt();

    String address = containerInformationService.getTaskPrivateAddress(account, region, task);
    List<Map<String, String>> healthStatus = containerInformationService.getHealthStatus(taskId, serviceName, account, region);

    com.amazonaws.services.ec2.model.Instance ec2Instance = containerInformationService.getEc2Instance(account, region, task);
    String availabilityZone = ec2Instance.getPlacement().getAvailabilityZone();

    return new EcsTask(taskId, launchTime, task.getLastStatus(), task.getDesiredStatus(), availabilityZone, healthStatus, address);
  }

  private TaskDefinition buildTaskDefinition(com.amazonaws.services.ecs.model.TaskDefinition taskDefinition) {
    String roleArn = taskDefinition.getTaskRoleArn();
    String iamRole = roleArn != null ? StringUtils.substringAfterLast(roleArn, "/") : "None";
    ContainerDefinition containerDefinition = taskDefinition.getContainerDefinitions().get(0);

    return new TaskDefinition()
      .setContainerImage(containerDefinition.getImage())
      .setContainerPort(containerDefinition.getPortMappings().get(0).getContainerPort())
      .setCpuUnits(containerDefinition.getCpu())
      .setMemoryReservation(containerDefinition.getMemoryReservation())
      .setIamRole(iamRole)
      .setTaskName(StringUtils.substringAfterLast(taskDefinition.getTaskDefinitionArn(), "/"))
      .setEnvironmentVariables(containerDefinition.getEnvironment());
  }

  private ServerGroup.Capacity buildServerGroupCapacity(int desiredCount, ScalableTarget target) {
    ServerGroup.Capacity capacity = new ServerGroup.Capacity();
    capacity.setDesired(desiredCount);
    if (target != null) {
      capacity.setMin(target.getMinCapacity());
      capacity.setMax(target.getMaxCapacity());
    } else {
      //TODO: Min/Max should be based on (desired count * min/max precent).
      capacity.setMin(desiredCount);
      capacity.setMax(desiredCount);
    }
    return capacity;
  }

  private EcsServerCluster buildSpinnakerServerCluster(AmazonCredentials credentials,
                                                       Set<LoadBalancer> loadBalancers,
                                                       EcsServerGroup ecsServerGroup) {
    return new EcsServerCluster()
      .setAccountName(credentials.getName())
      .setName(removeVersion(ecsServerGroup.getName()))
      .setLoadBalancers(loadBalancers)
      .setServerGroups(Sets.newHashSet(ecsServerGroup));
  }

  private EcsServerGroup buildEcsServerGroup(AmazonCredentials credentials,
                                             String region,
                                             String serviceName,
                                             int desiredCount,
                                             ServiceMetadata metadata,
                                             Set<Instance> instances,
                                             //ServerGroup.Capacity capacity,
                                             long creationTime,
                                             String ecsCluster,
                                             com.amazonaws.services.ecs.model.TaskDefinition taskDefinition
                                             //                                           ScalableTarget scalableTarget
  ) {
    ServerGroup.InstanceCounts instanceCounts = buildInstanceCount(instances);
    TaskDefinition ecsTaskDefinition = buildTaskDefinition(taskDefinition);
    ScalableTarget scalableTarget = retrieveScalableTarget(credentials, region, serviceName, ecsCluster);
    ServerGroup.Capacity capacity = buildServerGroupCapacity(desiredCount, scalableTarget);

    String account = credentials.getName();
    //vpcId and securityGroups were moved from outside of the for-loop into it. This is to potentially solve the to-do concern.
    String vpcId = "None"; // TODO - the assumption that there is only 1 VPC associated with the cluster might not be true 100% of the time.  It should be, but I am sure some people out there are weird
    Set<String> securityGroups = new HashSet<>();

    if (!instances.isEmpty()) {
      String taskId = instances.iterator().next().getName();
      String taskKey = Keys.getTaskKey(account, region, taskId);
      Task task = taskCacheClient.get(taskKey);
      com.amazonaws.services.ec2.model.Instance ec2Instance = containerInformationService.getEc2Instance(account, region, task);

      vpcId = ec2Instance.getVpcId();
      securityGroups = ec2Instance.getSecurityGroups().stream()
        .map(GroupIdentifier::getGroupId)
        .collect(Collectors.toSet());
    }


    Set<String> metricAlarmNames = ecsCloudWatchAlarmCacheClient.getMetricAlarms(serviceName, account, region).stream()
      .map(EcsMetricAlarm::getAlarmName)
      .collect(Collectors.toSet());

    EcsServerGroup serverGroup = new EcsServerGroup()
      .setDisabled(capacity.getDesired() == 0)
      .setName(constructServerGroupName(metadata))
      .setCloudProvider(EcsCloudProvider.ID)
      .setType(EcsCloudProvider.ID)
      .setRegion(region)
      .setInstances(instances)
      .setCapacity(capacity)
      .setInstanceCounts(instanceCounts)
      .setCreatedTime(creationTime)
      .setEcsCluster(ecsCluster)
      .setTaskDefinition(ecsTaskDefinition)
      .setVpcId(vpcId)
      .setSecurityGroups(securityGroups)
      .setMetricAlarms(metricAlarmNames);

    if (scalableTarget != null) {
      EcsServerGroup.AutoScalingGroup asg = new EcsServerGroup.AutoScalingGroup()
        .setDesiredCapacity(scalableTarget.getMaxCapacity())
        .setMaxSize(scalableTarget.getMaxCapacity())
        .setMinSize(scalableTarget.getMinCapacity());

      // TODO: Update Deck to handle an asg. Current Deck implementation uses a EC2 AutoScaling Group
      //serverGroup.setAsg(asg);
    }

    return serverGroup;
  }

  private ScalableTarget retrieveScalableTarget(AmazonCredentials credentials, String region, String serviceName, String clusterName) {
    //TODO: Consider caching the describeScalableTargets.
    AWSApplicationAutoScaling appASClient = getAmazonApplicationAutoScalingClient(credentials, region);
    List<String> resourceIds = new ArrayList<>();
    resourceIds.add(String.format("service/%s/%s", clusterName, serviceName));
    DescribeScalableTargetsRequest request = new DescribeScalableTargetsRequest()
      .withResourceIds(resourceIds)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withServiceNamespace(ServiceNamespace.Ecs);
    DescribeScalableTargetsResult result = appASClient.describeScalableTargets(request);

    if (result.getScalableTargets().isEmpty()) {
      return null;
    }

    if (result.getScalableTargets().size() == 1) {
      return result.getScalableTargets().get(0);
    }

    throw new Error("Multiple Scalable Targets found");
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient(AmazonCredentials credentials,
                                                                          String region) {
    String account = credentials.getName();
    AWSCredentialsProvider provider = credentials.getCredentialsProvider();
    return amazonClientProvider.getAmazonApplicationAutoScaling(account, provider, region);
  }

  private ServerGroup.InstanceCounts buildInstanceCount(Set<Instance> instances) {
    ServerGroup.InstanceCounts instanceCounts = new ServerGroup.InstanceCounts();
    for (Instance instance : instances) {
      switch (instance.getHealthState()) {
        case Up:
          instanceCounts.setUp(instanceCounts.getUp() + 1);
          break;
        case Down:
          instanceCounts.setDown(instanceCounts.getDown() + 1);
          break;
        case Failed:
          instanceCounts.setDown(instanceCounts.getDown() + 1);
          break;
        case Starting:
          instanceCounts.setOutOfService(instanceCounts.getOutOfService() + 1);
          break;
        case Unknown:
          instanceCounts.setUnknown(instanceCounts.getUnknown() + 1);
          break;
        case OutOfService:
          instanceCounts.setOutOfService(instanceCounts.getOutOfService() + 1);
          break;
        case Succeeded:
          instanceCounts.setUp(instanceCounts.getUp());
          break;
        default:
          throw new Error(String.format(
            "Unexpected health state: %s.  Don't know how to proceed - update %s",
            instance.getHealthState(),
            this.getClass().getSimpleName()));
      }
      instanceCounts.setTotal(instanceCounts.getTotal() + 1);
    }
    return instanceCounts;
  }

  private String constructServerGroupName(ServiceMetadata metadata) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(metadata.applicationName).append("-");

    if (metadata.cloudStack != null) {
      stringBuilder.append(metadata.cloudStack).append("-");
    }

    if (metadata.cloudDetail != null) {
      stringBuilder.append(metadata.cloudDetail).append("-");
    }

    if (metadata.serverGroupVersion != null) {
      stringBuilder.append(metadata.serverGroupVersion);
    }
    return stringBuilder.toString();
  }

  private String removeVersion(String serverGroupName) {
    return StringUtils.substringBeforeLast(serverGroupName, "-");
  }

  private ServiceMetadata extractMetadataFromServiceArn(String arn) {
    if (!arn.contains("/")) {
      return null; // TODO - do a better verification - Regex: arn:(.*):(.*):(\d*):(.*)\/((.*)-(v\d*))
    }

    String[] splitArn = arn.split("/");
    if (splitArn.length != 2) {
      return null; // TODO - do a better verification,
    }

    String[] splitResourceName = splitArn[1].split("-");

    if (splitResourceName.length < 2) {
      return null; // TODO - do a better verification, and handle cases with both cloudStack and CloudDetail
    }

    ServiceMetadata serviceMetadata = new ServiceMetadata();
    serviceMetadata.setApplicationName(splitResourceName[0]);

    String versionString = splitResourceName[splitResourceName.length - 1];
    try {
      Integer.parseInt(versionString.replaceAll("v", ""));
      serviceMetadata.setServerGroupVersion(versionString);
    } catch (NumberFormatException e) {
      // TODO - handle errorinous versions.
    }

    // An assumption is made here: Stack always appears before detail in the server group name.
    if (splitResourceName.length >= 3) {
      serviceMetadata.setCloudStack(splitResourceName[1]);
    }
    if (splitResourceName.length >= 4) {
      serviceMetadata.setCloudDetail(splitResourceName[2]);
    }

    return serviceMetadata;
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public Map<String, Set<EcsServerCluster>> getClusterSummaries(String application) {
    return getClusters();
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public Map<String, Set<EcsServerCluster>> getClusterDetails(String application) {
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AmazonCredentials credentials : getEcsCredentials()) {
      clusterMap = findClusters(clusterMap, credentials, application);
    }

    return clusterMap;
  }

  private List<AmazonCredentials> getEcsCredentials() {
    List<AmazonCredentials> ecsCredentialsList = new ArrayList<>();
    for (AccountCredentials credentials : accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials && credentials.getCloudProvider().equals(EcsCloudProvider.ID)) {
        ecsCredentialsList.add((AmazonCredentials) credentials);
      }
    }
    return ecsCredentialsList;
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public Set<EcsServerCluster> getClusters(String application, String account) {
    return getClusters().get(application);
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name) {
    return getClusters().get(application).iterator().next();
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name, boolean includeDetails) {

    Set<EcsServerCluster> ecsServerClusters = getClusters().get(application);
    if (ecsServerClusters != null && ecsServerClusters.size() > 0) {
      for (EcsServerCluster cluster : ecsServerClusters) {
        if (cluster.getName().equals(name)) {
          return cluster;
        }
      }
    }
    return null;
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public ServerGroup getServerGroup(String account, String region, String serverGroupName) {
    if (serverGroupName == null) {
      throw new Error("Invalid Server Group");
    }
    // TODO - use a caching system, and also check for account which is currently not the case here

    // TODO - remove the application filter.
    String application = serverGroupName.split("-")[0];
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AmazonCredentials credentials : getEcsCredentials()) {
      clusterMap = findClusters(clusterMap, credentials, application);
    }

    for (Map.Entry<String, Set<EcsServerCluster>> entry : clusterMap.entrySet()) {
      if (entry.getKey().equals(serverGroupName.split("-")[0])) {
        for (EcsServerCluster ecsServerCluster : entry.getValue()) {
          for (ServerGroup serverGroup : ecsServerCluster.getServerGroups()) {
            if (region.equals(serverGroup.getRegion())
              && serverGroupName.equals(serverGroup.getName())) {
              return serverGroup;
            }
          }
        }
      }
    }

    throw new Error(String.format("Server group %s not found", serverGroupName));
  }

  @Override
  public String getCloudProviderId() {
    return EcsCloudProvider.ID;
  }

  /**
   * Temporary implementation to satisfy the interface's implementation.
   * This will be modified and updated properly once we finish the POC
   */
  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

  @Data
  @NoArgsConstructor
  class ServiceMetadata {
    String applicationName;
    String cloudStack;
    String cloudDetail;
    String serverGroupVersion;
  }
}
