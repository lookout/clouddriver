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

import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.ecs.view.EcsInstanceProvider.getContainerInstance;
import static com.netflix.spinnaker.clouddriver.ecs.view.EcsInstanceProvider.getEC2InstanceStatus;

/*
 Spinnaker    | AWS
 server group = services
 cluster = services for an environment like prod
*/
@Component
public class EcsServerClusterProvider implements ClusterProvider<EcsServerCluster> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  AmazonClientProvider amazonClientProvider;
  private DescribeAutoScalingGroupsResult autoScalingGroupsResult;

  @Autowired
  public EcsServerClusterProvider(AccountCredentialsProvider accountCredentialsProvider, AmazonClientProvider amazonClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
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


    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(
      credentials.getName(),
      credentials.getCredentialsProvider(),
      awsRegion.getName()
    );

    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(
      credentials.getName(),
      credentials.getCredentialsProvider(),
      awsRegion.getName()
    );

    for (String clusterArn: amazonECS.listClusters().getClusterArns()) {
      String ecsClusterName = inferClusterNameFromArn(clusterArn);

      ListServicesResult result = amazonECS.listServices(new ListServicesRequest().withCluster(ecsClusterName));
      for (String serviceArn: result.getServiceArns()) {

        // Define all the server groups

        ServiceMetadata metadata = extractMetadataFromArn(serviceArn);

        Set<Instance> instances = new HashSet<>();
        AmazonLoadBalancer loadBalancer = new AmazonLoadBalancer();
        loadBalancer.setName("LOAD-BALANCER-NAME");



        ListTasksResult listTasksResult = amazonECS.listTasks(new ListTasksRequest().withServiceName(serviceArn).withCluster(ecsClusterName));
        if (listTasksResult.getTaskArns() != null && listTasksResult.getTaskArns().size() > 0) {

          DescribeTasksResult describeTasksResult = amazonECS.describeTasks(new DescribeTasksRequest().withCluster(ecsClusterName).withTasks(listTasksResult.getTaskArns()));
          for (Task task: describeTasksResult.getTasks()) {
            instances.add(new EcsTask(task.getTaskArn().split("/")[1], task, getEC2InstanceStatus(amazonEC2, getContainerInstance(amazonECS, task))));
          }
        }

        EcsServerGroup ecsServerGroup = new EcsServerGroup()
          .withName(metadata.applicationName + "-" +  metadata.cloudStack + "-" + metadata.serverGroupVersion) // This should be refactored using the `extract method` refactoring
          .withCloudProvider("aws")
          .withType("aws")
          .withRegion("us-west-2")
          .withInstances(instances);

        EcsServerCluster spinnakerCluster = new EcsServerCluster()
          .withAccountName(credentials.getName())
          .withName(metadata.cloudStack)
          .withLoadBalancers(Sets.newHashSet(loadBalancer))
          .withServerGroups(Sets.newHashSet(ecsServerGroup));


        if (clusterMap.get(metadata.applicationName) != null) {
          clusterMap.get(metadata.applicationName).add(spinnakerCluster);
        } else {
          clusterMap.put(metadata.applicationName, Sets.newHashSet(spinnakerCluster));
        }
      }
    }

    return clusterMap;
  }

  private ServiceMetadata extractMetadataFromArn(String arn) {
    if (!arn.contains("/")) {
      return null;
    }

    String[] splitArn = arn.split("/");
    if (splitArn.length != 2) {
      return null;
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

  private String inferClusterNameFromArn(String clusterArn) {
    return clusterArn.split("/")[1];
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusterSummaries(String application) {
    return getClusters();
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusterDetails(String application) {
    return getClusters();
  }

  @Override
  public Set<EcsServerCluster> getClusters(String application, String account) {
    return getClusters().get(application);
  }

  @Override
  public EcsServerCluster getCluster(String application, String account, String name) {
    return getClusters().get(application).iterator().next();
  }

  @Override
  public EcsServerCluster getCluster(String application, String account, String name, boolean includeDetails) {
    return getClusters().get(application).iterator().next();
  }

  @Override
  public ServerGroup getServerGroup(String account, String region, String name) {
    return getClusters().get("springfun").iterator().next().getServerGroups().iterator().next();
  }

  @Override
  public String getCloudProviderId() {
    return EcsCloudProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

}
