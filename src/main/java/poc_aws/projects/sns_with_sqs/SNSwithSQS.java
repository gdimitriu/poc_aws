/*
 Copyright (c) 2019 Gabriel Dimitriu All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This file is part of poc_aws project.

 poc_aws is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 poc_aws is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with poc_aws.  If not, see <http://www.gnu.org/licenses/>.
 */

package poc_aws.projects.sns_with_sqs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.Tag;
import com.amazonaws.services.sns.util.Topics;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

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
        System.out.println("commands: createTopic, deleteTopic, publish:Message, createQueue, deleteQueue, subscribe, readMessage, cleanUp, unsubscribe, quit");
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
                case "readMessage":
                    client.readMessages(queueUrl);
                    break;
                case "subscribe":
                    subscriptionArn = client.subscribeSQStoSNS(queueUrl, topicArn);
                    break;
                case "unsubscribe":
                    client.unsubscribe(subscriptionArn);
                    subscriptionArn = null;
                    break;
                case "cleanUp":
                    client.cleanUp(queueUrl, topicArn, subscriptionArn);
                    queueUrl = null;
                    topicArn = null;
                    subscriptionArn = null;
                    break;
            }
            if (userChoice.startsWith("publish:")) {
                client.publishMessage(topicArn, userChoice.substring(8));
            }
        } while(!"quit".equals(userChoice));

        if (topicArn != null || queueUrl != null || subscriptionArn !=null) {
            System.out.println("Don't forget to clean up your resources !!!");
        }
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

    /**
     * read, print and then delete the message from SQS queue
     * @param queueUrl the queue url from where to read the messages
     */
    public void readMessages(String queueUrl) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest().withQueueUrl(queueUrl);
        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();
        messages.forEach(a -> System.out.println("Received message: " + a.getBody()));
        if (messages.size() > 0) {
            DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest().withQueueUrl(queueUrl);
            for (Message message : messages) {
                deleteMessageRequest.setReceiptHandle(message.getReceiptHandle());
            }
            sqsClient.deleteMessage(deleteMessageRequest);
        }
    }

    /**
     * clean up the resources queue, topic , subscription
     * @param queueUrl the url of the queue
     * @param topicArn the arn of the topic
     * @param subscribeArn the arn of the subscription
     */
    public void cleanUp(String queueUrl, String topicArn, String subscribeArn) {
        if (subscribeArn != null) {
            //unsubscribe the queue
            snsClient.unsubscribe(subscribeArn);
        }
        if (topicArn != null) {
            //delete the topic
            snsClient.deleteTopic(topicArn);
        }
        if (queueUrl != null) {
            //delete the queue
            sqsClient.deleteQueue(queueUrl);
        }
    }

    /**
     * unsubscribe the subscription
     * @param subscribeArn the arn of the subscription
     */
    public void unsubscribe(String subscribeArn) {
        snsClient.unsubscribe(subscribeArn);
    }
}
