package com.netflix.spinnaker.clouddriver.ecs.view;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsInstance;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
public class EcsInstanceProvider implements InstanceProvider<EcsInstance> {

  private final String cloudProvider = EcsCloudProvider.ID;

  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  private AmazonClientProvider amazonClientProvider;


  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public EcsInstance getInstance(String account, String region, String id) {
    if (!isValidId(id, region))
      return null;

    EcsInstance ecsInstance = null;

    AWSCredentialsProvider awsCredentialsProvider = getCredentials(account).getCredentialsProvider();
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(account, awsCredentialsProvider, region);
    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(account, awsCredentialsProvider, region);

    Task ecsTask = getTask(amazonECS, id);
    InstanceStatus instanceStatus = getEC2InstanceStatus(amazonEC2, getContainerInstance(amazonECS, ecsTask));

    if (ecsTask != null && instanceStatus != null) {
      ecsInstance = new EcsInstance(id, ecsTask, instanceStatus);
    }

    return ecsInstance;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  private List<String> getAllClusters(AmazonECS amazonECS) {
    ListClustersResult listClustersResult = amazonECS.listClusters();
    List<String> clusterList = listClustersResult.getClusterArns();
    while (listClustersResult.getNextToken() != null) {
      listClustersResult = amazonECS.listClusters(
        new ListClustersRequest().withNextToken(listClustersResult.getNextToken())
      );
      clusterList.addAll(listClustersResult.getClusterArns());
    }
    return clusterList;
  }

  private NetflixAmazonCredentials getCredentials(String account) {
    NetflixAmazonCredentials accountCredentials = null;
    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials.getName().equals(account)) {
        if (credentials instanceof NetflixAmazonCredentials) {
          accountCredentials = (NetflixAmazonCredentials) credentials;
          break;
        }
      }
    }

    if (accountCredentials == null) {
      throw new NotFoundException(
        String.format("AWS account %s was not found.  Please specify a valid account name", account)
      );
    }

    return accountCredentials;
  }

  private boolean isValidId(String id, String region) {
    String id_regex = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}";
    String id_only = String.format("^%s$", id_regex);
    String arn = String.format("arn:aws:ecs:%s:\\d*:task/%s", region, id_regex);
    return id.matches(id_only) || id.matches(arn);
  }

  private Task getTask(AmazonECS amazonECS, String taskId) {
    Task task = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(taskId);

    for (String cluster: getAllClusters(amazonECS)) {
      List<Task> taskList = amazonECS.describeTasks(
        new DescribeTasksRequest().withCluster(cluster).withTasks(queryList))
        .getTasks();
      if (!taskList.isEmpty()) {
        task = taskList.get(0);
        break;
      }
    }
    return task;
  }


  private ContainerInstance getContainerInstance(AmazonECS amazonECS, Task task) {
    if (task == null) {
      return null;
    }

    ContainerInstance container = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(task.getContainerInstanceArn());
    DescribeContainerInstancesRequest request = new DescribeContainerInstancesRequest()
      .withCluster(task.getClusterArn())
      .withContainerInstances(queryList);
    List<ContainerInstance> containerList = amazonECS.describeContainerInstances(request).getContainerInstances();

    if (!containerList.isEmpty()) {
      container = containerList.get(0);
    }

    return container;
  }

  private InstanceStatus getEC2InstanceStatus(AmazonEC2 amazonEC2, ContainerInstance container) {
    if (container == null) {
      return null;
    }

    InstanceStatus instanceStatus = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(container.getEc2InstanceId());
    List<InstanceStatus> instanceStatusList = amazonEC2.describeInstanceStatus(
      new DescribeInstanceStatusRequest().withInstanceIds(queryList)
    ).getInstanceStatuses();

    if (!instanceStatusList.isEmpty()) {
      instanceStatus = instanceStatusList.get(0);
    }

    return instanceStatus;
  }
}

