# GCP-02

# CLOUD ODS + EDW

__Operationalized Data Lake/Warehouse - Ability to provide a highly available and highly scalable Operational Data Store (ODS) while keeping an Enterprise Data Warehouse (EDW) up to date in near real time, in order to obtain broader insight by cross-referencing against other sources of data in the enterprise.__

__SA Maintainer__: [Sig Narváez](mailto:sig.narvaez@mongodb.com), 

__SA Contributor__: [Paresh Saraf](mailto:paresh.saraf@mongodb.com), 

__SA Contributor__: [Munish Kapoor](mailto:munish.kapoor@mongodb.com)

__SA Contributor__: [Chris Grabosky](mailto:chris.grabosky@mongodb.com)

__Time to setup__: 2+ hrs

__Time to execute__: 20 mins

__Estimated GCP cost__: ~$50

__Estimated Atlas cost__: <$5

---

## Description

This proof shows how MongoDB Atlas on Google Cloud can act as an Operational Data Layer and effectively co-exist with Google BigQuery acting as Enterprise Data Warehouse. 

The proof uses a sample movie dataset __sample_mflix__ loaded into MongoDB Atlas. [Google Cloud Dataflow](https://cloud.google.com/dataflow) is then used to transform the MongoDB collection into relational tables using the [MongoDBIO](https://beam.apache.org/releases/javadoc/2.0.0/org/apache/beam/sdk/io/mongodb/MongoDbIO.html) library from Apache Beam. Finally, [BigQueryIO](https://beam.apache.org/releases/javadoc/2.2.0/org/apache/beam/sdk/io/gcp/bigquery/BigQueryIO.html) library is used to write the transformed data into Google BigQuery. We will then join this data with a public dataset provided by Google BigQuery to show the power of consolidation across disparate data sources.

__*** NOTE ***:__ The readme for this proof (this file) needs an overhaul given pull request [ODS-EDW complete refactor #47](https://github.com/10gen/atlas-google-pov/pull/47). 
For now, please follow [demo_steps.sh](demo_steps.sh) and [demo_steps_ml.sh](demo_steps_ml.sh).

![](img/overview.png)

---

## Prerequisites

* A [MongoDB Atlas](https://cloud.mongodb.com) account
* A [Google Cloud Platform](https://console.cloud.google.com) account

---

## Setup
### 1. Configure Atlas Environment
- Log-on to your [Atlas account](https://cloud.mongodb.com)
- If you do not have an account, click on Sign Up. Sign Up with Google or enter details.
- Click on the __Database Access__ link on the left column. In the new page, choose to __Add New User__ using the green button on the left. In the modal dialog, give the user the name `appUser` and password `appUser123`. We will use some built in roles so click __Add Default Privileges__ and in the __Default Privileges__ section add the roles __readWriteAnyDatabase__ and __clusterMonitor__ then press the green __Add User__ button to create the user.

![](img/ss_atlas_user.png)

- Return to the clusters page by clicking __Clusters__ on the left navigation column.
- Press the giant __Build a Cluster__ button and then choose to create a single region cluster.

![](img/ss_atlas_clustertype.png)

* Deploy a 3 node replica-set in the Google Cloud region of your choice (here us-west2) with default settings, no backup, MongoDB version 4.0, and give it a name of your choosing (here we will use __MDB-Dataflow__). The size must be M10 or larger. Here we used an M30. Click the __Create Cluster__ button and wait for the cluster to complete deployment.

![](img/ss_atlas_deploy.png)

_If this is a free account, you will be required to add your credit card to create a cluster other than M0. To add your credit card details - select Billing on the left Navigation Menu and Add a Payment Method._

![](img/ss_atlas_payment.png)

- Whitelist the IPs. For the demo we will allow access from 0.0.0.0/0. This is not recommended for a production setup, where the recommendation will be to use VPC Peering and private IPs.

![](img/ss_atlas_network.png)

- Copy the Connection String and replace the password with the __password__ of the __appUser__ created earlier. This will be used in the [Execution](#Execution) later.

![](img/ss_atlas_connection_string.png)

### 2. Load Data Into the MongoDB Atlas Cluster
MongoDB Atlas provides a [sample dataset](https://docs.atlas.mongodb.com/sample-data/available-sample-datasets/) that you can use to quickly get started and begin experimenting with various tools like CRUD operations in Atlas and visualizations in Charts. To load the dataset: 

1. Navigate to your Clusters view.
  -   In the left navigation pane in Atlas, click Clusters.

2. Open the Load Sample Dataset dialog.
  -   Locate the cluster where you want to load sample data.
  -   Click the Ellipses (…) button for your cluster.
  -   Click Load Sample Dataset.

3. In the dialog, click Load Sample Dataset
  -   The dialog closes and Atlas begins loading your sample dataset into your cluster.

4. View your sample data.
  -   To view your sample data by click your cluster’s Collections button. You should see the following databases in your cluster:

  | S. No. | Datbase Name |
  |---|---|
  |1.|sample_airbnb|
  |2.|sample_geospatial|
  |3.|sample_mflix|
  |4.|sample_supplies|
  |5.|sample_training|
  |6.|sample_weatherdata|

<img src="img/ss_atlas_data_load.png" width="500">

We will be using the `sample_mflix` data for this exercise.


### 3. Configure GCP environment
- Log-on to your personal [GCP](https://console.cloud.google.com) account.

- In the Cloud Console, on the project selector page, create a new Cloud project named __MongoDBAtlasODS-EDW__.

![](img/GCP_Project_ID.png)

- Make sure that [billing](https://cloud.google.com/billing/docs/how-to/modify-project) is enabled for your Google Cloud project
- Enable the following Google Cloud services via the API by opening this url in your browser. You will need to select the __MongoDBAtlasODS-EDW__ project.

   ```https://console.cloud.google.com/flows/enableapi?apiid=dataflow,compute_component,logging,storage_component,storage_api,bigquery,pubsub,datastore.googleapis.com,cloudresourcemanager.googleapis.com```
   
<img src="img/ApiEnable.jpg" width="500">

<img src="img/ApiEnable2.jpg" width="500">
  
- Create a VM Instance using __GCP Compute Engine__

<img src="img/gcp_vm_instances.png" width="500"> 

- Use the following values for creating the VM instance. Ensure that you deploy in the same Google Cloud region as your MongoDB Atlas cluster (us-west2 in this example).
  * _Name:_ `client-vm`
  * _Region:_ us-west2
  * _Zone:_ a
  * _Machine configuration:_ leave as General purpose and choose __n1-standard-1__
  * _Boot disk:_ Change to __Ubuntu 18.04 LTS__ (you can choose something else but all instructions will be based on this)
  * _Identity and API access:_ __Allow full access to all Cloud APIs__ 
  * Leave the other values as default.
  * Then click __Create__

![](../03-SINGLEVIEW/img/ss_gcp_newvm.png)

- Connect to the newly created VM using ssh

![](img/gcp_vm_connect.png)

* Prepare the prerequisites for the machine by either running the commands in the following table one-by-one, or execute the [`prepMachine.sh` script located in this directory](prepMachine.sh)

| Purpose | Command |
| ------- | ------- |
| Confirm version is Ubuntu 18.04 LTS | `lsb_release -a` |
| Update apt cache | `sudo apt update` |
| Install git | `sudo apt install -y git` |
| Install Java | `sudo apt install -y default-jre` |
| Check Java version | `java --version` |
| Make sure only one Java is installed | `sudo update-alternatives --config java` |
| Set `JAVA_HOME` environment variable | `export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64` |
| Make sure correct `java` is in path | `export PATH=$PATH:$JAVA_HOME/bin` |
| Check Java version to make sure all is good | `java --version` |
| Install [Apache Maven](http://maven.apache.org/) | `sudo apt install -y maven` |
| Make sure Maven is in path | `mvn -v` |

__IF THE OUTPUT OF THE `LSB_RELEASE` COMMAND IS NOT THAT OF UBUNTU 18.04, YOU HAVE DEPLOYED THE WRONG VM FOR THESE INSTRUCTIONS. PLEASE DESTROY THE VM AND START OVER WITH THE CORRECT OPERATING SYSTEM__

__ON THE `export JAVA_HOME` COMMAND, THE PATH LISTED SHOULD BE THE PATH RETURNED BY THE `sudo update-alternatives --config java` COMMAND, OMMITTING THE `/bin/java`. AN EXAMPLE HAS BEEN PROVIDED__

- Clone the git repo using the git clone <url> command. NOTE: Cloning private GitHub repos from the command line require [personal access tokens](https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token-for-the-command-line). eg - 
  
    `git clone https://YOUR_USER:YOUR_PERSONAL_ACCESS_TOKEN@github.com/10gen/atlas-google-pov.git`

 
- Create a __Cloud Storage bucket__:

* In the Cloud Console, go to the _Cloud Storage_ Browser [page](https://console.cloud.google.com/storage/browser) then __Create Bucket__
* Complete the form as follows:
  * _Name:_ anything you choose but do not include sensitive information in the bucket name, because the bucket namespace is global and publicly visible
  * _Location type:_ change to `Region` and in the drop down choose the same regions you deployed your compute and Atlas in (here `us-west2`)
  * _Storage class:_ Standard
  * _Access control:_ fine-grained
* Then click the blue __Create__ button
   
   ![](img/gcp_bucket.png)


- Configure __BigQuery__ environment

 - Search for bigquery in search bar at the top and select first result.
   ![](img/gcp_bigquery.png)

 - Create a data set by name __mflix__. Accept remaining defaults.
   ![](img/gcp_bigquery_dataset.png)

 - Create an empty table __movies__ (inside the __mflix__ dataset) as shown below. Accept remaining defaults.
   ![](img/gcp_bq_table.png)
 
---

## Execution

Make sure you are inside proof folder. Execute below script :

```cd ~/atlas-google-pov/proofs/02-ODS-AND-EDW/```

```
mvn compile exec:java \
    -Dexec.mainClass=dataflowdemonew.MongoDataFlowBigQuery \
    -Dexec.args="--project=<your GCP project id> \
    --mongouri=<mongodb atlas cluster connection string> \
    --gcpTempLocation=<temp location for dataflow (within the cloud storage bucket created before)> \
    --tempLocation=<temp location for bigquery (within the cloud storage bucket created before)> \
    --runner=DataflowRunner \
    --jobName=<job name> \
     --region=<region name>" \
    -Pdataflow-runner
```
Example:

```
mvn compile exec:java \
    -Dexec.mainClass=dataflowdemonew.MongoDataFlowBigQuery \
    -Dexec.args="--project=mongodbatlasods-edw-268604 \
         --mongouri=mongodb+srv://appUser:appUser123@mdb-dataflow-htq0y.gcp.mongodb.net/sample_mflix \
         --gcpTempLocation=gs://mkbucket001/tmp/ \
         --tempLocation=gs://mkbucket001/tmp/ \
         --runner=DataflowRunner \
         --jobName=dataflow-intro \
         --region=us-west1" \
         -Pdataflow-runner
```

__HINT:__ If you obtain a runtime error, check which [DataFlow Regional Endpoint](https://cloud.google.com/dataflow/docs/concepts/regional-endpoints) is closest and available to your VM and MongoDB Atlas Cluster.


After a few minutes, navigate to GCP Dataflow (Select it from left tab in GCP console). You can see list of jobs in progress and completed:

![](img/gcp_dataflow_jobs.png)

Click on the relevant job. You should be able to see job details as below:

![](img/gcp_job_details.png)

Navigate to BigQuery from GCP console. You should be able to see the rows populated in the empty tables created before.

![](img/gcp_bq_table_loaded.png)

---

## Measurement

An Operational Data Lake/Date Warehouse will bring in data from various different System of Records (SORs). Users execute  queries across these data sources. Let's join two distinct datasets, the `mflix.movies` data, which has the details on movie listings brought over from MongoDB Atlas and [Google BigQuery's public dataset](https://cloud.google.com/bigquery/public-data)  on film locations in San Francisco, where movies have been shot. 

Lets query this data to see list of movies ordered by the number of shooting locations in San Francisco.

Execute the following query. You may see that the movie _Blue Jasmine_ has been shot in over 50 locations in San Francisco, followed by _Time After Time_ and _San Andreas_.

```
WITH   movieLocations 
AS     (select b.title title, 
               count(*) numLoc
        from `bigquery-public-data.san_francisco_film_locations.film_locations` b
        group by b.title),
movies 
AS     (SELECT movie_id,
               title,
               plot
        FROM   mflix.movies)
SELECT movie_id,
       m.title,
       numLoc,
       plot       
FROM   movies m
INNER JOIN
movieLocations 
ON m.title = movieLocations.title
order by 3 desc;

```
You should see an output like this:

![](img/combined_movies.png)


---

## Cleanup

1. Terminate the MongoDB Atlas Cluster 

<img src="img/terminate_cluster.png" width="500"> 


2. Delete BigQuery Dataset

![](img/terminate_bigquery.png)

3. Delete Compute VM

![](img/terminate_vm.png)

4. Delete Storage

![](img/terminate_storage.png)

5. Shutdown the project, if it isn't used for other purposes.

<img src="img/terminate_project.png" width="500"> 
