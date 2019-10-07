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

import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;

import javax.jms.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SQSJmsClientOperations {

    /** connection factory for JMS */
    private SQSConnectionFactory connectionFactory;

    private SQSConnection sqsConnection;

    public static void main(String...args) {
        try {
            SQSJmsClientOperations client = new SQSJmsClientOperations();
            System.out.println("commands: createSt, createFifo, readMessage, readAsyncMessage, send:<message>, quit");
            String queueName = "MyQueue";
            Scanner sc = new Scanner(System.in);
            String userChoice;
            do {
                userChoice = sc.nextLine();
                switch (userChoice){
                    case "deleteQueue":
                        break;
                    case "readMessage":
                        System.out.println(client.readMessage(queueName));
                        break;
                    case "readAsyncMessage":
                        client.readAsynMessage(queueName);
                        break;
                    case "createSt":
                        queueName = "MyQueue";
                        client.createStandardQueue("MyQueue", 5, 86400);
                        break;
                    case "createFifo":
                        queueName = "MyQueue.fifo";
                        client.createFifoQueue("MyQueue.fifo", 5, 86400);
                        break;
                }
                if (userChoice.startsWith("send:")) {
                    client.sendMessage(queueName, userChoice.substring(5));
                }
            } while(!"quit".equals(userChoice));
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public SQSJmsClientOperations() throws JMSException {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
       connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(),
               AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(new AWSStaticCredentialsProvider(credentials)).build());
       sqsConnection = connectionFactory.createConnection();
    }

    /**
     * Create a FIFO queue.
     * @param queueName the name of the queue this should ended with .fifo
     * @param delay the delay of the messages
     * @param retentionPeriod the retention period
     * @throws JMSException
     */
    public void createFifoQueue(String queueName, int delay, int retentionPeriod) throws JMSException {
        AmazonSQSMessagingClientWrapper client = sqsConnection.getWrappedAmazonSQSClient();
        if (!client.queueExists(queueName)) {
            Map<String,String> attributes = new HashMap<>();
            attributes.put("FifoQueue", "true");
            attributes.put("ContentBasedDeduplication", "true");
            attributes.put("DelaySeconds", Integer.toString(delay));
            attributes.put("MessageRetentionPeriod", Integer.toString(retentionPeriod));
            CreateQueueRequest request = new CreateQueueRequest().withQueueName(queueName).withAttributes(attributes);
            client.createQueue(request);
        }
    }

    /**
     * Create standard queue.
     * @param queueName the name of the queue
     * @param delay the delay of the messages
     * @param retentionPeriod the retention period
     * @throws JMSException
     */
    public void createStandardQueue(String queueName, int delay, int retentionPeriod) throws JMSException {
        AmazonSQSMessagingClientWrapper client = sqsConnection.getWrappedAmazonSQSClient();
        if (!client.queueExists(queueName)) {
            Map<String,String> attributes = new HashMap<>();
            attributes.put("DelaySeconds", Integer.toString(delay));
            attributes.put("MessageRetentionPeriod", Integer.toString(retentionPeriod));
            client.createQueue(new CreateQueueRequest(queueName).withAttributes(attributes));
        }
    }

    /**
     * Send a message to the queue.
     * @param queueName the queue name
     * @param message the message to be send
     */
    public void sendMessage(String queueName, String message) {
        Session session = null;
        try {
            session = sqsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(queue);
            TextMessage jmsMessage = session.createTextMessage(message);
            if (queueName.endsWith(".fifo")) {
                jmsMessage.setStringProperty("JMSXGroupID", "default");
            }
            producer.send(jmsMessage);
            System.out.println("JMS Message " + jmsMessage.getJMSMessageID());
            System.out.println("JMS Message Sequence Number " + jmsMessage.getStringProperty("JMS_SQS_SequenceNumber"));
            session.close();
        } catch (JMSException e) {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex1) {
                    ex1.printStackTrace();
                }
            }
        }

    }

    /**
     * read synchonius a message from a queue
     * @param queueName the queue name
     * @return message payload
     */
    public String readMessage(String queueName) {
        Session session = null;
        try {
            session = sqsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(queue);
            sqsConnection.start();
            Message receivedMessage = consumer.receive(10000);
            if (receivedMessage != null) {
                System.out.println("Received message with correlation " + receivedMessage.getJMSCorrelationID() + " and message Id " + receivedMessage.getJMSMessageID());
                if (queueName.endsWith(".fifo")) {
                    System.out.println("Message sequence number: " + receivedMessage.getStringProperty("JMS_SQS_SequenceNumber"));
                }
                receivedMessage.acknowledge();
                session.close();
                return ((TextMessage) receivedMessage).getText();
            }
            session.close();
            sqsConnection.stop();
        }  catch (JMSException ex) {
            ex.printStackTrace();
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex1) {
                    ex1.printStackTrace();
                }
            }
        }
        return "Not received message from qeueu after 10000 ms !";
    }

    /**
     * read asynchonius a message from a queue
     * @param queueName the queue name
     * @return message payload
     */
    public void readAsynMessage(String queueName) {
        Session session = null;
        try {
            session = sqsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(new MyMessageListener());
            sqsConnection.start();
        } catch (JMSException ex) {
            ex.printStackTrace();
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex1) {
                    ex1.printStackTrace();
                }
            }
        }
    }
}
