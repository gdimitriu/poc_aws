package poc_aws.projects.sns_with_sqs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.Tag;
import com.amazonaws.services.sns.util.Topics;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;

import java.util.List;
import java.util.Scanner;

public class SNSwithSQS {

    /** client for the SQS system */
    private AmazonSQS sqsClient;
    /** client for the SNS system */
    private AmazonSNS snsClient;

    public static void main(String...args) {
        SNSwithSQS client = new SNSwithSQS();
        String topicArn = null;
        String queueUrl = null;
        String subscriptionArn = null;
        System.out.println("commands: createTopic, deleteTopic, publish:Message, createQueue, deleteQueue, subscribe, quit");
        Scanner sc = new Scanner(System.in);
        String userChoice;
        do {
            userChoice = sc.nextLine();
            switch (userChoice){
                case "createQueue":
                    if (queueUrl != null) {
                        client.deleteQueue(queueUrl);
                    }
                    queueUrl = client.createQueue("MyQueue", 5, 86400);
                    break;
                case "deleteTopic":
                    if (topicArn != null) {
                        client.deleteTopic(topicArn);
                    }
                    topicArn = null;
                    break;
                case "createTopic":
                    if (topicArn != null) {
                        client.deleteTopic(topicArn);
                    }
                    topicArn = client.createTopic("MyTopic");
                    break;
                case "subscribe":
                    subscriptionArn = client.subscribeSQStoSNS(queueUrl, topicArn);
            }
            if (userChoice.startsWith("publish:")) {
                client.publishMessage(topicArn, userChoice.substring(8));
            }
        } while(!"quit".equals(userChoice));
    }

    public SNSwithSQS() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        sqsClient = AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        snsClient = AmazonSNSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

    /**
     * delete the SQS queue
     * @param queueUrl the url of the queue
     */
    public void deleteQueue(String queueUrl) {
        sqsClient.deleteQueue(queueUrl);
    }

    /**
     * delete the SNS topic
     * @param topicArn  the arn of the topic
     */
    public void deleteTopic(String topicArn) {
        snsClient.deleteTopic(topicArn);
    }

    /**
     * create a standard queue to receive SNS topics
     * @param queueName queue name
     * @param delay the delay
     * @param retentionPeriod the retention period
     * @return the url of the queue
     */
    public String createQueue(String queueName, int delay, int retentionPeriod) {
        CreateQueueRequest request = new CreateQueueRequest().withQueueName(queueName)
                .addAttributesEntry("DelaySeconds", Integer.toString(delay))
                .addAttributesEntry("MessageRetentionPeriod", Integer.toString(retentionPeriod));
        return sqsClient.createQueue(request).getQueueUrl();
    }

    /**
     * create a SNS topic with a name
     * @param topicName the name of the topic
     * @return the arn of the created topic
     */
    public String createTopic(String topicName) {
        CreateTopicRequest request = new CreateTopicRequest().withName(topicName).withTags(new Tag().withKey("Name").withValue(topicName));
        return snsClient.createTopic(request).getTopicArn();
    }

    /**
     * publish a message to the topic which will be taken by the SQS which is suscribed
     * @param topicArn the topic arn
     * @param message the message to be send
     */
    public void publishMessage(String topicArn, String message) {
        PublishRequest request = new PublishRequest().withTopicArn(topicArn).withMessage(message).withSubject("message from SNS");
        snsClient.publish(request);
    }

    /**
     * subscribe SQS queue to the SNS topic.
     * @param queueUrl the url of the SQS queue
     * @param topicArn the SNS topic arn
     * @return the arn of the subscription
     */
    public String subscribeSQStoSNS(String queueUrl, String topicArn) {
        return Topics.subscribeQueue(snsClient, sqsClient, topicArn, queueUrl);
    }
}
