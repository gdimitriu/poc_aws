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

package poc_aws.poc_tests.sqs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;

import java.util.List;
import java.util.Scanner;

public class SQSClientOperations {


    private AmazonSQS sqsClient;
    public static void main(String...args) {
        SQSClientOperations client = new SQSClientOperations();
        String queueUrl = client.createStandardQueue("MyQueue", 5, 86400);
        System.out.println("commands:deleteQueue, readAll, send:<message>, deleteReadMessages quit");
        Scanner sc = new Scanner(System.in);
        String userChoice;
        List<Message> messages = null;
        do {
            userChoice = sc.nextLine();
            switch (userChoice){
                case "deleteQueue":
                    client.deleteQeueue(queueUrl);
                    break;
                case "readAll":
                    messages = client.readAndPrintAll(queueUrl);
                    break;
                case "deleteReadMessages":
                    client.deleteMessages(queueUrl, messages);
                    break;
            }
            if (userChoice.startsWith("send:")) {
                client.sendMessage(queueUrl, userChoice.substring(5));
            }
        } while(!"quit".equals(userChoice));
    }

    public SQSClientOperations() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        sqsClient = AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

    /**
     * Create a standard queue.
     * @param name the queue name
     * @param delay the delay
     * @param retentionPeriod  the retention period
     * @return queue url
     */
    public String createStandardQueue(String name, int delay, int retentionPeriod) {
        CreateQueueRequest request = new CreateQueueRequest(name)
                .addAttributesEntry("DelaySeconds", Integer.toString(delay))
                .addAttributesEntry("MessageRetentionPeriod", Integer.toString(retentionPeriod));
        sqsClient.createQueue(request);
        return sqsClient.getQueueUrl(name).getQueueUrl();
    }

    /**
     * delete a sqs queue
     * @param queueUrl name of the queue to be deleted
     */
    public void deleteQeueue(String queueUrl) {
        DeleteQueueRequest request = new DeleteQueueRequest().withQueueUrl(queueUrl);
        sqsClient.deleteQueue(request);
    }

    /**
     * send a message to the queue with delay
     * @param queueUrl the url of the queue
     * @param message the message to be send
     */
    public void sendMessage(String queueUrl, String message) {
        SendMessageRequest request = new SendMessageRequest().withMessageBody(message).withQueueUrl(queueUrl);
        sqsClient.sendMessage(request);
    }

    /**
     * read and print 10 messages if they exists
     * @param queueUrl the url of the queue
     * @return list of messages
     */
    public List<Message> readAndPrintAll(String queueUrl) {
        ReceiveMessageRequest request = new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(10);
        List<Message> messages = sqsClient.receiveMessage(request).getMessages();
        messages.stream().map(a -> a.getBody()).forEach(System.out::println);
        for (Message message : messages) {
            ChangeMessageVisibilityRequest requestVisibility = new ChangeMessageVisibilityRequest().withQueueUrl(queueUrl);
            requestVisibility.withReceiptHandle(message.getReceiptHandle()).withVisibilityTimeout(60);
            sqsClient.changeMessageVisibility(requestVisibility);
        }
        return messages;
    }

    /**
     * delete messages from a queue
     * @param queueUrl the url of the queue
     * @param messages list of messages to be deleted
     */
    public void deleteMessages(String queueUrl, List<Message> messages) {
        if (messages == null) {
            return;
        }
        for (Message message : messages) {
            DeleteMessageRequest request = new DeleteMessageRequest().withQueueUrl(queueUrl).withReceiptHandle(message.getReceiptHandle());
            sqsClient.deleteMessage(request);
        }
    }
}
