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

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class SQSClientOperations {


    private SqsClient sqsClient;
    public static void main(String...args) {
        SQSClientOperations client = new SQSClientOperations();
        String queueUrl = null;
        System.out.println("commands: createSt, createFifo, deleteQueue, readAll, send:<message>, deleteReadMessages, quit");
        Scanner sc = new Scanner(System.in);
        String userChoice;
        List<Message> messages = null;
        do {
            userChoice = sc.nextLine();
            switch (userChoice){
                case "deleteQueue":
                    if (queueUrl != null) {
                        client.deleteQeueue(queueUrl);
                    }
                    queueUrl = null;
                    break;
                case "readAll":
                    messages = client.readAndPrintAll(queueUrl);
                    break;
                case "deleteReadMessages":
                    client.deleteMessages(queueUrl, messages);
                    break;
                case "createSt":
                    if (queueUrl != null) {
                        client.deleteQeueue(queueUrl);
                    }
                    queueUrl = client.createStandardQueue("MyQueue", 5, 86400);
                    break;
                case "createFifo":
                    if (queueUrl != null) {
                        client.deleteQeueue(queueUrl);
                    }
                    queueUrl = client.createFifoQueue("MyQueue", 5, 86400);
                    break;
            }
            if (userChoice.startsWith("send:")) {
                client.sendMessage(queueUrl, userChoice.substring(5));
            }
        } while(!"quit".equals(userChoice));
    }

    public SQSClientOperations() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        sqsClient = SqsClient.builder().credentialsProvider(StaticCredentialsProvider.create(credentials)).region(Region.US_EAST_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * Create a standard queue.
     * @param name the queue name
     * @param delay the delay
     * @param retentionPeriod  the retention period
     * @return queue url
     */
    public String createStandardQueue(String name, int delay, int retentionPeriod) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("DelaySeconds", Integer.toString(delay));
        attributes.put("MessageRetentionPeriod", Integer.toString(retentionPeriod));
        CreateQueueRequest request = CreateQueueRequest.builder().queueName(name)
                .attributesWithStrings(attributes).build();
        sqsClient.createQueue(request);
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }

    /**
     * Create a fifo queue.
     * @param name the queue name
     * @param delay the delay
     * @param retentionPeriod  the retention period
     * @return queue url
     */
    public String createFifoQueue(String name, int delay, int retentionPeriod) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("DelaySeconds", Integer.toString(delay));
        attributes.put("MessageRetentionPeriod", Integer.toString(retentionPeriod));
        attributes.put("ContentBasedDeduplication", "true");
        attributes.put("FifoQueue", "true");
        CreateQueueRequest request = CreateQueueRequest.builder().queueName(name + ".fifo").attributesWithStrings(attributes).build();
        sqsClient.createQueue(request);
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name + ".fifo").build()).queueUrl();
    }

    /**
     * delete a sqs queue
     * @param queueUrl name of the queue to be deleted
     */
    public void deleteQeueue(String queueUrl) {
        DeleteQueueRequest request = DeleteQueueRequest.builder().queueUrl(queueUrl).build();
        sqsClient.deleteQueue(request);
    }

    /**
     * send a message to the queue with delay
     * @param queueUrl the url of the queue
     * @param message the message to be send
     */
    public void sendMessage(String queueUrl, String message) {
        GetQueueAttributesRequest description = GetQueueAttributesRequest.builder().attributeNamesWithStrings("FifoQueue").queueUrl(queueUrl).build();
        Map<String, String>  attributes = sqsClient.getQueueAttributes(description).attributesAsStrings();
        SendMessageRequest.Builder request = SendMessageRequest.builder().messageBody(message).queueUrl(queueUrl);
        if (attributes.containsKey("FifoQueue")) {
            if (attributes.get("FifoQueue").equals("true")) {
                request.messageGroupId("awk.test");
            }
        }

        sqsClient.sendMessage(request.build());
    }

    /**
     * read and print 10 messages if they exists
     * @param queueUrl the url of the queue
     * @return list of messages
     */
    public List<Message> readAndPrintAll(String queueUrl) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10).build();
        List<Message> messages = sqsClient.receiveMessage(request).messages();
        messages.stream().map(a -> a.body()).forEach(System.out::println);
        for (Message message : messages) {
            ChangeMessageVisibilityRequest.Builder requestVisibility = ChangeMessageVisibilityRequest.builder().queueUrl(queueUrl);
            requestVisibility.receiptHandle(message.receiptHandle()).visibilityTimeout(60);
            sqsClient.changeMessageVisibility(requestVisibility.build());
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
            DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build();
            sqsClient.deleteMessage(request);
        }
    }
}
