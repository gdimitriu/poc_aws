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
package poc_aws.s3;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;

import java.io.File;
import java.net.URL;

public class S3Infrastructure {

    /** the client for the Amazon storage */
    private AmazonS3 s3client;

    public S3Infrastructure(Regions region) {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        s3client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region).build();
    }

    public S3Infrastructure(AmazonS3 client) {
        this.s3client = client;
    }

    /**
     * Create a bucket with the specific name into the region.
     * @param bucketName the name of the bucket
     * @return the amazon bucket or null if the bucket already exists
     */
    public Bucket createBucket(String bucketName) {
        if (s3client.doesBucketExistV2(bucketName)) {
            System.out.println("Bucket with name " + bucketName + " already exist!");
            return null;
        }
        return s3client.createBucket(bucketName);
    }

    /**
     * get the file from resource
     * @param resourceFile  name of the resource
     * @return the file
     */
    private File getFileFromResource(String resourceFile) {
        URL url = this.getClass().getClassLoader().getResource(resourceFile);
        if (url != null) {
            return new File(url.getFile());
        }
        return null;
    }

    /**
     * upload new file to Bucket.
     * If the bucket does not exist create it.
     * If the file exist print message.
     * @param bucketName the name of the bucket
     * @param fileToUpload the file to upload
     * @param fileOnS3Name the name of the file on s3
     * @return true if file is uploaded, false if exists
     */
    public boolean uploadNewFileToBucket(String bucketName, String fileToUpload, String fileOnS3Name) {
        File file = getFileFromResource(fileToUpload);
        if (file == null) {
            return false;
        }
        if (!s3client.doesBucketExistV2(bucketName)) {
            createBucket(bucketName);
        }
        if (!s3client.doesObjectExist(bucketName, file.getName())) {
            s3client.putObject(bucketName, fileOnS3Name, file);
            return true;
        } else {
            System.out.println("Object " + fileToUpload + " already exists!");
            return false;
        }
    }
}
