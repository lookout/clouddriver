/*
 * * Copyright 2017 Lookout, Inc.
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;


@Component
public class EcsInstanceProvider implements InstanceProvider<EcsTask> {

  private AccountCredentialsProvider accountCredentialsProvider;
  private AmazonClientProvider amazonClientProvider;
  private ContainerInformationService containerInformationService;
  private final Cache cacheView;

  @Autowired
  public EcsInstanceProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider,
                             AmazonClientProvider amazonClientProvider, ContainerInformationService containerInformationService) {
    this.cacheView = cacheView;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    this.containerInformationService = containerInformationService;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public EcsTask getInstance(String account, String region, String id) {
    if (!isValidId(id, region))
      return null;

    EcsTask ecsInstance = null;

    NetflixAmazonCredentials netflixAmazonCredentials =
      (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(account);
    AWSCredentialsProvider awsCredentialsProvider = netflixAmazonCredentials.getCredentialsProvider();
    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(account, awsCredentialsProvider, region);

    String key = Keys.getTaskKey(account, region, id);
    CacheData taskCache = cacheView.get(TASKS.toString(), key);
    if (taskCache == null) {
      return null;
    }

    //TODO: getEC2InstanceStatus is only being made to determine the availability zone of the instance.
    InstanceStatus instanceStatus = containerInformationService.getEC2InstanceStatus(amazonEC2, account, region, (String) taskCache.getAttributes().get("containerInstanceArn"));

    if (instanceStatus != null) {
      String serviceName = StringUtils.substringAfter((String) taskCache.getAttributes().get("group"), "service:");
      Long launchTime = (Long) taskCache.getAttributes().get("startedAt");

      List<Map<String, String>> healthStatus = containerInformationService.getHealthStatus(id, serviceName, account, region);
      String address = containerInformationService.getTaskPrivateAddress(account, region, amazonEC2, taskCache);

      ecsInstance = new EcsTask(id, launchTime, (String) taskCache.getAttributes().get("lastStatus"), (String) taskCache.getAttributes().get("desiredStatus"), instanceStatus.getAvailabilityZone(), healthStatus, address);
    }

    return ecsInstance;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  private boolean isValidId(String id, String region) {
    String id_regex = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}";
    String id_only = String.format("^%s$", id_regex);
    String arn = String.format("arn:aws:ecs:%s:\\d*:task/%s", region, id_regex);
    return id.matches(id_only) || id.matches(arn);
  }

}

