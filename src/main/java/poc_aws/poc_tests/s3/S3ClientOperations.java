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

package poc_aws.poc_tests.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.S3Actions;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("access-can-be-private")
public class S3ClientOperations {

    private AmazonS3 s3client;
    /**
     * bucket should be unique across entire S3
     */
    private static final String BUCKET_PREFIX = "gabrieldimitriu";
    private static final String BUCKET_LOAD = BUCKET_PREFIX + "load";
    public static void main(String...args) {
        System.out.println("Create buckets in s3");
        S3ClientOperations client = new S3ClientOperations();
        client.createBucket(BUCKET_PREFIX+ "test");
        client.createBucket(BUCKET_PREFIX + "test1");
        List<Bucket> buckets = client.getAllBuckets();
        buckets.stream().map(Bucket::getName).forEach(System.out::println);

        System.out.println("Delete the not empty bucket at second run of the test");
        client.forceDeleteNotEmptyBucket(BUCKET_LOAD);
        System.out.println("Now delete all buckets");
        client.removeBuckets(buckets).forEach((key, value) -> System.out.println("bucket=" + key.getName() + " error = " + value.getLocalizedMessage()));

        System.out.println("Create bucket and upload files");
        client.uploadFileToBucket(BUCKET_LOAD, "poc_tests/testDocument.txt", "testDocument1.txt");
        client.uploadFileToBucket(BUCKET_LOAD, "poc_tests/testDocument.txt", "testDocument2.txt");
        client.uploadFileToBucket(BUCKET_LOAD, "poc_tests/testDocument.txt", "testDocument3.txt");

        System.out.println("List object from the bucket");
        List<S3ObjectSummary> objectSummaries = client.getUploadedObjectsFromBucket(BUCKET_LOAD);
        objectSummaries.forEach(ob -> System.out.println(ob.getKey()));

        System.out.println("Download and print objects");
        List<InputStream> inputStreams = client.getAllObjectFromBucket(BUCKET_LOAD);
        inputStreams.stream().map(is -> new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"))).forEach(System.out::println);
        System.out.println("Make the populated bucket pubic");
        client.setPublicReadPolicy(BUCKET_LOAD);
    }

    public S3ClientOperations() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        s3client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(Regions.US_EAST_1).build();
    }

    public Bucket createBucket(String bucketName) {
        if (s3client.doesBucketExistV2(bucketName)) {
            System.out.println("Bucket with name " + bucketName + " already exist!");
            return null;
        }
        return s3client.createBucket(bucketName);
    }

    public List<Bucket> getAllBuckets() {
        return s3client.listBuckets();
    }

    /**
     * This will remove the buckets, the removed bocket is removed also from the list
     * @param toRemove list of buckets to be removed
     * @return map of buckets with exceptions
     */
    public Map<Bucket, Exception> removeBuckets(List<Bucket> toRemove) {
        Map<Bucket, Exception> removeError = new HashMap<>();
        Iterator iter = toRemove.iterator();
        while(iter.hasNext()) {
            Bucket bucket = (Bucket) iter.next();
            try {
                forceDeleteNotEmptyBucket(bucket.getName());
                iter.remove();
            } catch (AmazonServiceException e) {
                removeError.put(bucket, e);
            }
        }
        return removeError;
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
     * upload file to Bucket.
     * If the bucket does not exist create it.
     * If the file exist print message.
     * @param bucketName the name of the bucket
     * @param fileToUpload the file to upload
     * @param fileOnS3Name the name of the file on s3
     * @return true if file is uploaded, false if exists
     */
    public boolean uploadFileToBucket(String bucketName, String fileToUpload, String fileOnS3Name) {
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

    /**
     * get the summary for the objects from bucket.
     * @param bucketName the name of the bucket
     * @return list of object summary
     */
    public List<S3ObjectSummary> getUploadedObjectsFromBucket(String bucketName) {
        List<S3ObjectSummary> objects = new ArrayList<>();
        s3client.listObjects(bucketName).getObjectSummaries().forEach(objects::add);
        return objects;
    }

    public List<InputStream> getAllObjectFromBucket(String bucketName) {
        List<InputStream> objects = new ArrayList<>();
        s3client.listObjects(bucketName).getObjectSummaries().forEach(ob -> objects.add(s3client.getObject(bucketName,ob.getKey()).getObjectContent()));
        return objects;
    }

    /**
     * force delete the bucket. s3 does not have the notion of folder is just a prefix in front of the file.
     * so to delete all elements from a bucket you have to delete all objects.
     * @param bucketName the name of the bucket
     */
    private void forceDeleteNotEmptyBucket(String bucketName) {
        if (s3client.doesBucketExistV2(bucketName)) {
            //this could be done using request but is more complicated
            /*
            List<String> objects = new ArrayList<>();
            s3client.listObjects(bucketName).getObjectSummaries().stream().forEach(ob -> objects.add(ob.getKey()));
            if (objects.size() > 0) {
                DeleteObjectsRequest delObjsReq = new DeleteObjectsRequest(bucketName).withKeys(objects.toArray(new String[1]));
                s3client.deleteObjects(delObjsReq);
            } */
            s3client.listObjects(bucketName).getObjectSummaries().forEach(ob -> s3client.deleteObject(bucketName,ob.getKey()));
            s3client.deleteBucket(bucketName);
        }
    }

    public void setPublicReadPolicy(String bucketName) {
        String bucketPolicy = new Policy().withStatements(
                new Statement(Statement.Effect.Allow)
                        .withPrincipals(Principal.AllUsers)
                        .withActions(S3Actions.GetObject)
                        .withResources(new Resource(
                                "arn:aws:s3:::" + bucketName + "/*"))).toJson();
        s3client.setBucketPolicy(bucketName, bucketPolicy);
    }
}
