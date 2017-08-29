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

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster;
import com.netflix.spinnaker.clouddriver.model.*;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

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

    Set<Cluster> clusters = new HashSet<>();
    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials) {
         Set<Cluster> retrievedClusters = findClusters((AmazonCredentials) credentials);
          clusters.addAll(retrievedClusters);
      }
    }
    return null;
  }

  private Set<Cluster> findClusters(AmazonCredentials credentials) {
    Set<Cluster> clusters = new HashSet<>();


    for (AmazonCredentials.AWSRegion awsRegion: credentials.getRegions()) {
      AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(
        credentials.getName(),
        credentials.getCredentialsProvider(),
        awsRegion.getName()
      );

      findClustersForRegion(amazonECS);
//      clusters.addAll(findClustersForRegion(amazonECS));
    }

    return clusters;
  }

  private Map<String, Set<EcsServerCluster>> findClustersForRegion(AmazonECS amazonECS) {
    Set<EcsServerCluster> clusters = new HashSet<>();

    HashMap<String, Set<EcsServerCluster>> clusterHashMap = new HashMap<>();

    for (String clusterArn: amazonECS.listClusters().getClusterArns()) {
      String clusterName = inferClusterNameFromArn(clusterArn);

      ListServicesResult result = amazonECS.listServices(new ListServicesRequest().withCluster(clusterName));
      for (String serviceArn: result.getServiceArns()) {
        // Define all the server groups
        // place server groups in proper cluster
        // place clusters in proper application
      }
    }

    for (Map.Entry<String, Set<EcsServerCluster>> entry :clusterHashMap.entrySet()) {
      //clusters.add(entry.getValue());
    }

    return null;
  }

  private String inferClusterNameFromArn(String clusterArn) {
    return clusterArn.split("/")[1];
  }

  private HashMap<String, Set<EcsServerCluster>> inferClustersFromService(AmazonECS amazonECS, HashMap<String, Set<EcsServerCluster>> clusterHashMap, String clusterName, String serviceArn) {
    return null;
  }


    @Override
  public Map<String, Set<EcsServerCluster>> getClusterSummaries(String application) {
    return null;
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusterDetails(String application) {
    return null;
  }

  @Override
  public Set<EcsServerCluster> getClusters(String application, String account) {
    return null;
  }

  @Override
  public EcsServerCluster getCluster(String application, String account, String name) {
    return null;
  }

  @Override
  public EcsServerCluster getCluster(String application, String account, String name, boolean includeDetails) {
    return null;
  }

  @Override
  public AmazonServerGroup getServerGroup(String account, String region, String name) {
    return null;
  }

  @Override
  public String getCloudProviderId() {
    return null;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

}
