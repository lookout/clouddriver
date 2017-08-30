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

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EcsServerClusterProvider implements ClusterProvider<EcsServerCluster> {

  private AccountCredentialsProvider accountCredentialsProvider;
  private AmazonClientProvider amazonClientProvider;
  private ContainerInformationService containerInformationService;

  @Autowired
  public EcsServerClusterProvider(AccountCredentialsProvider accountCredentialsProvider, AmazonClientProvider amazonClientProvider, ContainerInformationService containerInformationService) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    this.containerInformationService = containerInformationService;
    getClusters();
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusters() {
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials) {
        clusterMap = findClusters(clusterMap, (AmazonCredentials) credentials);
      }
    }
    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClusters(Map<String, Set<EcsServerCluster>> clusterMap,
                                                          AmazonCredentials credentials) {
    for (AmazonCredentials.AWSRegion awsRegion: credentials.getRegions()) {
      clusterMap = findClustersForRegion(clusterMap, credentials, awsRegion);
    }

    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClustersForRegion(Map<String, Set<EcsServerCluster>> clusterMap,
                                                                   AmazonCredentials credentials,
                                                                   AmazonCredentials.AWSRegion awsRegion) {

    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(credentials.getName(), credentials.getCredentialsProvider(), awsRegion.getName());
    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(credentials.getName(), credentials.getCredentialsProvider(), awsRegion.getName());
    AmazonElasticLoadBalancing amazonELB = amazonClientProvider.getAmazonElasticLoadBalancingV2(credentials.getName(), credentials.getCredentialsProvider(), awsRegion.getName());

    for (String clusterArn: amazonECS.listClusters().getClusterArns()) {
      String ecsClusterName = inferClusterNameFromClusterArn(clusterArn);

      ListServicesResult result = amazonECS.listServices(new ListServicesRequest().withCluster(ecsClusterName));
      for (String serviceArn: result.getServiceArns()) {

        ServiceMetadata metadata = extractMetadataFromServiceArn(serviceArn);
        Set<Instance> instances = new HashSet<>();

        DescribeLoadBalancersResult loadBalancersResult = amazonELB.describeLoadBalancers(new DescribeLoadBalancersRequest());
        Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> loadBalancers = extractLoadBalancersData(loadBalancersResult);

        ListTasksResult listTasksResult = amazonECS.listTasks(new ListTasksRequest().withServiceName(serviceArn).withCluster(ecsClusterName));
        if (listTasksResult.getTaskArns() != null && listTasksResult.getTaskArns().size() > 0) {
          DescribeTasksResult describeTasksResult = amazonECS.describeTasks(new DescribeTasksRequest().withCluster(ecsClusterName).withTasks(listTasksResult.getTaskArns()));

          for (Task task: describeTasksResult.getTasks()) {
            InstanceStatus ec2InstanceStatus = containerInformationService.getEC2InstanceStatus(amazonEC2, containerInformationService.getContainerInstance(amazonECS, task));
            instances.add(new EcsTask(extractTaskIdFromTaskArn(task.getTaskArn()), task, ec2InstanceStatus));
          }
        }

        EcsServerGroup ecsServerGroup = generateServerGroup(awsRegion, metadata, instances);
        EcsServerCluster spinnakerCluster = generateSpinnakerServerCluster(credentials, metadata, loadBalancers, ecsServerGroup);

        if (clusterMap.get(metadata.applicationName) != null) {
          clusterMap.get(metadata.applicationName).add(spinnakerCluster);
        } else {
          clusterMap.put(metadata.applicationName, Sets.newHashSet(spinnakerCluster));
        }
      }
    }

    return clusterMap;
  }

  private Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> extractLoadBalancersData(DescribeLoadBalancersResult loadBalancersResult) {
    Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> loadBalancers = Sets.newHashSet();
    for (LoadBalancer elb: loadBalancersResult.getLoadBalancers()) {
      AmazonLoadBalancer loadBalancer = new AmazonLoadBalancer();
      loadBalancer.setName(elb.getLoadBalancerName());
      loadBalancers.add(loadBalancer);
    }
    return loadBalancers;
  }

  private EcsServerCluster generateSpinnakerServerCluster(AmazonCredentials credentials, ServiceMetadata metadata, Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> loadBalancers, EcsServerGroup ecsServerGroup) {
    return new EcsServerCluster()
          .withAccountName(credentials.getName())
          .withName(metadata.cloudStack)
          .withLoadBalancers(loadBalancers)
          .withServerGroups(Sets.newHashSet(ecsServerGroup));
  }

  private EcsServerGroup generateServerGroup(AmazonCredentials.AWSRegion awsRegion, ServiceMetadata metadata, Set<Instance> instances) {
    return new EcsServerGroup()
          .withName(constructServerGroupName(metadata))
          .withCloudProvider("aws")   // TODO - Implement ECS in Deck so we can stop tricking the front-end app here
          .withType("aws")            // TODO - Implement ECS in Deck so we can stop tricking the front-end app here
          .withRegion(awsRegion.getName())
          .withInstances(instances);
  }

  private String constructServerGroupName(ServiceMetadata metadata) {
    return metadata.applicationName + "-" +  metadata.cloudStack + "-" + metadata.serverGroupVersion; // TODO - support CLOUD_DETAIL variable
  }

  private ServiceMetadata extractMetadataFromServiceArn(String arn) {
    if (!arn.contains("/")) {
      return null; // TODO - do a better verification,
    }

    String[] splitArn = arn.split("/");
    if (splitArn.length != 2) {
      return null; // TODO - do a better verification,
    }

    String[] splitResourceName = splitArn[1].split("-");

    if (splitResourceName.length != 3) {
      return null; // TODO - do a better verification, and handle cases with both cloudStack and CloudDetail
    }

    ServiceMetadata serviceMetadata = new ServiceMetadata();
    serviceMetadata.applicationName = splitResourceName[0];
    serviceMetadata.cloudStack = splitResourceName[1];
    serviceMetadata.serverGroupVersion = splitResourceName[2];

    return serviceMetadata;
  }

  class ServiceMetadata {
    String applicationName;
    String cloudStack;
    String serverGroupVersion;
  }

  private String inferClusterNameFromClusterArn(String clusterArn) {
    return clusterArn.split("/")[1];
  }

  private String extractTaskIdFromTaskArn(String taskArn) {
    return taskArn.split("/")[1];
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public Map<String, Set<EcsServerCluster>> getClusterSummaries(String application) {
    return getClusters();
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public Map<String, Set<EcsServerCluster>> getClusterDetails(String application) {
    return getClusters();
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public Set<EcsServerCluster> getClusters(String application, String account) {
    return getClusters().get(application);
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name) {
    return getClusters().get(application).iterator().next();
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name, boolean includeDetails) {
    return getClusters().get(application).iterator().next();
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public ServerGroup getServerGroup(String account, String region, String name) {
    return getClusters().get(name).iterator().next().getServerGroups().iterator().next();
  }

  @Override
  public String getCloudProviderId() {
    return EcsCloudProvider.ID;
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

}
