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

package poc_aws.poc_tests.aim;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

public class AIMClientOperations {
    /** aim client  */
    private IamClient iamClient;
    private static String DOCUMENT = "{"+
            "    \"Version\": \"2012-10-17\","+
            "    \"Statement\": ["+
            "        {"+
            "            \"Effect\": \"Allow\","+
            "            \"Action\": \"sts:AssumeRole\","+
            "            \"Principal\": {"+
            "                  \"Service\":[ \"ec2.amazonaws.com\"]"+
            "            }" +
            "        }"+
            "    ]"+
            "}";

    public static void main(String...args) {
        AIMClientOperations client = new AIMClientOperations();
        client.createRoleS3();
        client.attachRolePolicy();
        client.createInstanceProfileAndRole();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {

        }
        client.describeRole();
    }

    /**
     * Default constructor.
     */
    public AIMClientOperations() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        iamClient = IamClient.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.AWS_GLOBAL).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    private void createRoleS3() {
        CreateRoleRequest request = CreateRoleRequest.builder().roleName("MyS3role").assumeRolePolicyDocument(DOCUMENT).build();
        iamClient.createRole(request);
    }

    private void attachRolePolicy() {
        AttachRolePolicyRequest request = AttachRolePolicyRequest.builder().roleName("MyS3role").policyArn("arn:aws:iam::aws:policy/AmazonS3FullAccess").build();
        iamClient.attachRolePolicy(request);
    }

    private void describeRole() {
        GetInstanceProfileRequest request = GetInstanceProfileRequest.builder().instanceProfileName("MyS3role").build();
        System.out.println(iamClient.getInstanceProfile(request).instanceProfile());
    }

    private void createInstanceProfileAndRole() {
        CreateInstanceProfileRequest requestCreateInstanceProfile = CreateInstanceProfileRequest.builder().instanceProfileName("MyS3role").build();
        CreateInstanceProfileResponse result = iamClient.createInstanceProfile(requestCreateInstanceProfile);
        AddRoleToInstanceProfileRequest requstAddRoleToInstanceProfile = AddRoleToInstanceProfileRequest.builder().instanceProfileName("MyS3role").roleName("MyS3role").build();
        iamClient.addRoleToInstanceProfile(requstAddRoleToInstanceProfile);

    }
}
