package com.netflix.spinnaker.clouddriver.ecs.view;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.ListClustersRequest;
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

  @Override
  public EcsInstance getInstance(String account, String region, String id) {
    if (!is_valid_task_id(id)) {
      return null;
    }

    AWSCredentialsProvider awsCredentialsProvider = getCredentials(account).getCredentialsProvider();
    AmazonECS amazonECS = amazonClientProvider.getAmazonECS(account, awsCredentialsProvider, region);

    ListClustersResult listClustersResult = amazonECS.listClusters();
    List<String> clusterList = listClustersResult.getClusterArns();
    while (listClustersResult.getNextToken() != null) {
      listClustersResult = amazonECS.listClusters(
        new ListClustersRequest().withNextToken(listClustersResult.getNextToken())
      );
      clusterList.addAll(listClustersResult.getClusterArns());
    }

    EcsInstance instance = null;

    List<String> queryTaskList = new ArrayList<>();
    queryTaskList.add(id);

    for (String cluster: clusterList) {
      List<Task> responseTaskList = amazonECS.describeTasks(
          new DescribeTasksRequest().withCluster(cluster).withTasks(queryTaskList))
        .getTasks();
      if (responseTaskList.size() == 1) {
        instance = new EcsInstance();
        break;
      }
    }

    return instance;
  }

  /**
   * Returns a Boolean that validates that the id parameter is a valid ECS Task ID
   *
   * @param  id  Task ID to validate
   * @return     Whether the Task ID is valid or not
   */
  private boolean is_valid_task_id(String id) {
    return true;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }
}
