# poc_aws
aws examples and pocs

The amazon key-pair from the csv file should be saved into the c:\users\user\\.aws\credentials with the following format:

[default]

aws_access_key_id= key_id from csv

aws_secret_access_key= access_key from cvs


Into the projects/pharma there is the project to create a pharmaceutical site:
- it will create automatically key-pair and save on D:\keypair-name.pem to be used for ssh
- all except load balancer are into a private virtual cloud
- the webserver site are distributed on S3
- two web servers into two availability zones which will take the site from S3
- load balancer in front of them
- two databases (not populated yet)
- each database instance has a NAT assigned to a elastic IP address
- Internet gateway for the web servers to be available from outside
- The automatic created structure is :
![Alt text](documentations/pharma.png?raw=true "Pharma")
- The EC2IfrastructureRequest is used to clone a webserver from one AZ to increase the load, it will be added also to the Load Balancer.

Into the projects/sns_with_sqs
- it will create at demand a topic
- it will create at demand a queue
- it will subscribe at demand the queue the topic
- it will publish at demand a message to the topic which will send automatically to the queue
- it will read at demand the messages from the queue, print and then delete them
- it will delete the created subscription with unsubscribe command at demand
- it will delete the created topic at demand
- it will delete the create queue at demand
- it will cleanUp the created resources at demand

Into the poc_tests are different small test for the amazon services:
- ec2 : creation, start, stop, adding ebs(not yet mounted on instance just attached).
- aim to create a role
- s3 to create a web site.
- sqs to create queue and exchange messages
- sns to create topic and publish messages
