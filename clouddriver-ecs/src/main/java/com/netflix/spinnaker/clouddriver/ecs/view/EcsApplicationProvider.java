/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.view;


import com.google.common.collect.Sets;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

@Component
public class EcsApplicationProvider implements ApplicationProvider {

  private final Cache cacheView;
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcsApplicationProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.cacheView = cacheView;
  }


  @Override
  public Application getApplication(String name) {

    for (Application application : getApplications(false)) {
      if (name.equals(application.getName())) {
        return application;
      }
    }

    return null;
  }

  /**
   * TODO - Implement this method as fully intended by the interface, once the POC is over, which includes using the expand boolean
   */
  @Override
  public Set<Application> getApplications(boolean expand) {
    Set<Application> applications = new HashSet<>();

    for (AccountCredentials credentials : accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials) {
        Set<Application> retrievedApplications = findApplicationsForAllRegions((AmazonCredentials) credentials);
        applications.addAll(retrievedApplications);
      }
    }

    return applications;
  }

  private Set<Application> findApplicationsForAllRegions(AmazonCredentials credentials) {
    Set<Application> applications = new HashSet<>();

    for (AmazonCredentials.AWSRegion awsRegion : credentials.getRegions()) {
      applications.addAll(findApplicationsForRegion(credentials.getName(), awsRegion.getName()));
    }

    return applications;
  }

  private Set<Application> findApplicationsForRegion(String account, String region) {
    HashMap<String, Application> applicationHashMap = populateApplicationMap(account, region);
    Set<Application> applications = transposeApplicationMapToSet(applicationHashMap);

    return applications;
  }

  private HashMap<String, Application> populateApplicationMap(String account, String region) {
    HashMap<String, Application> applicationHashMap = new HashMap<>();
    Collection<CacheData> allServices = cacheView.getAll(SERVICES.toString());
    Collection<CacheData> validServices = allServices
      .stream()
      .filter(cache ->{
        Map<String, String> keyAttributes = Keys.parse(cache.getId());
        return keyAttributes.get("account").equals(account) && keyAttributes.get("region").equals(region);
      })
      .collect(Collectors.toSet());

    for (CacheData serviceCache : validServices) {
      applicationHashMap = inferApplicationFromServices(applicationHashMap, serviceCache);
    }
    return applicationHashMap;
  }

  private Set<Application> transposeApplicationMapToSet(HashMap<String, Application> applicationHashMap) {
    Set<Application> applications = new HashSet<>();

    for (Map.Entry<String, Application> entry : applicationHashMap.entrySet()) {
      applications.add(entry.getValue());
    }

    return applications;
  }

  private HashMap<String, Application> inferApplicationFromServices(HashMap<String, Application> applicationHashMap, CacheData serviceCache) {

    HashMap<String, String> attributes = new HashMap<>();  // After POC we'll figure exactly what info we want to put in here
    String appName = (String) serviceCache.getAttributes().get("applicationName");
    String serviceName = (String) serviceCache.getAttributes().get("serviceName");
    attributes.put("iamRole", (String) serviceCache.getAttributes().get("roleArn"));
    attributes.put("taskDefinition", (String) serviceCache.getAttributes().get("taskDefinition"));
    attributes.put("desiredCount", String.valueOf(serviceCache.getAttributes().get("desiredCount")));

    HashMap<String, Set<String>> clusterNames = new HashMap<>();
    clusterNames.put(appName, Sets.newHashSet(serviceName));

    EcsApplication application = new EcsApplication(appName, attributes, clusterNames);

    if (!applicationHashMap.containsKey(appName)) {
      applicationHashMap.put(appName, application);
    } else {
      applicationHashMap.get(appName).getAttributes().putAll(application.getAttributes());
      applicationHashMap.get(appName).getClusterNames().get(appName).add(serviceName);
    }

    return applicationHashMap;
  }

}