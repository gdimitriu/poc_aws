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

package poc_aws.poc_tests.sns;


import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.Tag;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class SNSClientOperations {


    private SnsClient snsClient;
    public static void main(String...args) {
        SNSClientOperations client = new SNSClientOperations();
        String topicArn = null;
        System.out.println("commands: createTopic, deleteTopic, send:Message quit");
        Scanner sc = new Scanner(System.in);
        String userChoice;
        do {
            userChoice = sc.nextLine();
            switch (userChoice){
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
            }
            if (userChoice.startsWith("send:")) {
                client.sendMessage(topicArn, userChoice.substring(5));
            }
        } while(!"quit".equals(userChoice));
    }

    public SNSClientOperations() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        snsClient = SnsClient.builder().region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(credentials))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * Create a topic.
     * @param topicName the name of the topic
     * @return the arn of the topic
     */
    public String createTopic(String topicName) {
        CreateTopicRequest request = CreateTopicRequest.builder().name(topicName)
                .tags(Tag.builder().key("Name").value(topicName).build()).build();
        return snsClient.createTopic(request).topicArn();
    }

    /**
     * Delete the topic
     * @param topicArn  the arn of the topic
     */
    public void deleteTopic(String topicArn) {
        DeleteTopicRequest request = DeleteTopicRequest.builder().topicArn(topicArn).build();
        snsClient.deleteTopic(request);
    }

    public void sendMessage(String topicArn, String message) {
        PublishRequest request = PublishRequest.builder().message(message).topicArn(topicArn).build();
        snsClient.publish(request);
    }
}
