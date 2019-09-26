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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;

public class AIMClientOperations {
    /** aim client  */
    private AmazonIdentityManagement aimClient;
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
    }

    /**
     * Default constructor.
     */
    public AIMClientOperations() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        aimClient = AmazonIdentityManagementClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("AWS_GLOBAL").build();
    }

    private void createRoleS3() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.withRoleName("MyS3role").withAssumeRolePolicyDocument(DOCUMENT);
        aimClient.createRole(request);
    }

    private void attachRolePolicy() {
        AttachRolePolicyRequest request = new AttachRolePolicyRequest().withRoleName("MyS3role").withPolicyArn("arn:aws:iam::aws:policy/AmazonS3FullAccess");
        aimClient.attachRolePolicy(request);
    }
}
