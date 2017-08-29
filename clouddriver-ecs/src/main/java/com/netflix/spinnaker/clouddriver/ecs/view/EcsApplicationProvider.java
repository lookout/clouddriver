package com.netflix.spinnaker.clouddriver.ecs.view;


import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class EcsApplicationProvider implements ApplicationProvider {


  public static final String SERVICE_DELIMITER = "-";
  AccountCredentialsProvider accountCredentialsProvider;

  AmazonClientProvider amazonClientProvider;

  @Autowired
  public EcsApplicationProvider(AccountCredentialsProvider accountCredentialsProvider, AmazonClientProvider amazonClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    getApplications(false);
  }

  @Override
  public Set<Application> getApplications(boolean expand) {
    Set<Application> applications = new HashSet<>();

    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials) {
        Set<Application> retrievedApplications = findApplications((AmazonCredentials) credentials);
        applications.addAll(retrievedApplications);
      }
    }

    return applications;
  }

  private Set<Application> findApplications(AmazonCredentials credentials) {
    Set<Application> applications = new HashSet<>();


    for (AmazonCredentials.AWSRegion awsRegion: credentials.getRegions()) {
      AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(
        credentials.getName(),
        credentials.getCredentialsProvider(),
        awsRegion.getName()
      );

      applications.addAll(findApplicationsForRegion(amazonECS));
    }


    return applications;
  }

  private Set<Application> findApplicationsForRegion(AmazonECS amazonECS) {
    Set<Application> applications = new HashSet<>();

    HashMap<String, Application> applicationHashMap = new HashMap<>();

    for (String clusterArn: amazonECS.listClusters().getClusterArns()) {
      String clusterName = inferClusterNameFromArn(clusterArn);

      ListServicesResult result = amazonECS.listServices(new ListServicesRequest().withCluster(clusterName));
      for (String serviceArn: result.getServiceArns()) {
        inferApplicationsFromService(amazonECS, applicationHashMap, clusterName, serviceArn);
      }
    }

    for (Map.Entry<String, Application> entry :applicationHashMap.entrySet()) {
      applications.add(entry.getValue());
    }

    return applications;
  }

  private String inferClusterNameFromArn(String clusterArn) {
    return clusterArn.split("/")[1];
  }

  private HashMap<String, Application> inferApplicationsFromService(AmazonECS amazonECS, HashMap<String, Application> applicationHashMap, String clusterName, String serviceArn) {
    DescribeServicesResult describeResult = amazonECS.describeServices(new DescribeServicesRequest().withServices(serviceArn).withCluster(clusterName));

    for (Service service: describeResult.getServices()) {
      if (service.getServiceName().contains(SERVICE_DELIMITER)) {
        String appName = inferAppNameFromServiceName(service.getServiceName());


        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("iamRole", service.getRoleArn());
        attributes.put("taskDefinition", service.getTaskDefinition());
        attributes.put("desiredCount", String.valueOf(service.getDesiredCount()));

        HashMap<String, Set<String>> clusterNames = new HashMap<>();
        clusterNames.put(appName, Sets.newHashSet(service.getServiceName()));


        EcsApplication application = new EcsApplication(
          appName,
          attributes,
          clusterNames);

        if (!applicationHashMap.containsKey(appName)) {
          applicationHashMap.put(appName, application);
        } else {
          applicationHashMap.get(appName).getAttributes().putAll(application.getAttributes());
          applicationHashMap.get(appName).getClusterNames().get(appName).add(service.getServiceName());
        }
      }
    }

    return applicationHashMap;
  }

  private String inferAppNameFromServiceName(String serviceName) {
    return serviceName.split(SERVICE_DELIMITER)[0];
  }

  @Override
  public Application getApplication(String name) {

    for (Application application: getApplications(false)) {
      if (name.equals(application.getName())) {
        return application;
      }
    }

    return null;
  }
}
