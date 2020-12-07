# ================================================
# ================================================
# SETUP
# ================================================
# ================================================
// Enable API's
1. https://console.cloud.google.com/flows/enableapi?apiid=dataflow,compute_component,logging,storage_component,storage_api,bigquery,pubsub,datastore.googleapis.com,cloudresourcemanager.googleapis.com
2. Create storage bucket
3. Create BigQuery Dataset 'mflix' & Table 'movies (both empty)
4. Create GCP VM & clone git repo. Ensure Java and Maven are installed
5. CD to ~/atlas-google-pov/proofs/02-ODS-AND-EDW (all shell commands will run from here)


# ================================================
# INITIAL LOAD : ATLAS ==> DATAFLOW ==> BIGQUERY
# ODStoEDW_MoviesLoad.java
# ================================================
mvn compile exec:java \
    -Dexec.mainClass=ods_edw.ODStoEDW_MoviesLoad \
    -Dexec.args="--project={GCP-PROJECT} \
        --mongouri=mongodb+srv://{USER}:{PASSWORD}@{ATLAS-CLUSTER}/sample_mflix \
        --gcpTempLocation=gs://{GCS-BUCKET}/tmp/ \
        --tempLocation=gs://{GCS-BUCKET}/tmp/ \
        --runner=DataflowRunner \
        --jobName=dataflow-intro \
        --region=us-west1" \
    -Pdataflow-runner


# ================================================================
# CDC : ATLAS CHANGE STREAM ==> PUBSUB ==> DATAFLOW ==> BIGQUERY
# ODStoEDW_MoviesCDCSource.java
# ODStoEDW_MoviesCDCSink.java
# PUBSUB TOPIC: mongodbcdc
# ================================================================

# Terminal 1
mvn -q clean compile exec:java -Dexec.mainClass="ods_edw.ODStoEDW_MoviesCDCSource" \
-Dexec.args="{GCP-PROJECT} mongodbcdc mongodb+srv://{USER}:{PASSWORD}@{ATLAS-CLUSTER}/sample_mflix"

# Terminal 2
mvn -e compile exec:java \
  -Dexec.mainClass=ods_edw.ODStoEDW_MoviesCDCSink \
  -Dexec.cleanupDaemonThreads=false \
  -Dexec.args=" \
        --project={GCP-PROJECT} \
        --inputTopic=projects/{GCP-PROJECT}/topics/mongodbcdc \
        --output=output.log \
        --runner=DataflowRunner"


# ================================================================
# Query BQ for new movies (CDC)
# ================================================================
1. Using Atlas collection/data browser, copy a movie document and insert new. Remove the _id and change title
2. Show ChangeStream and PubSub sending new document to BigQuery
3. Query BigQuery using the exact title in step 1

SELECT *
FROM `{GCP-PROJECT}.mflix.movies`
WHERE Title = "[YOUR NEW MOVIE TITLE HERE]"
LIMIT 1


# ================================================================
# Obtain Insights - Movie Locations
# Save to [movieInsights] table in BQ
# ================================================================
CREATE TABLE IF NOT EXISTS mflix.movieInsights
OPTIONS(
  description = "Movie insights obtained from MongoDB Atlas and Google BigQuery"
)
AS
SELECT movie_id,
       m.title,
       numloc,
       plot
FROM
    (SELECT movie_id,
            title,
            plot
    FROM   `mflix.movies`) AS m
INNER JOIN
    (SELECT b.title  title,
            count(*) numLoc
    FROM    `bigquery-public-data.san_francisco_film_locations.film_locations` b
    GROUP BY b.title) AS movieLocations
ON m.title = movieLocations.title
ORDER BY 3 DESC;


# ================================================================
# CDC : BQ INSIGHT TABLE ==> PUBSUB ==> DATAFLOW ==> ATLAS
# EDWtoODS_InsightsCDCSink.java
# EDWtoODS_InsightsCDCSource.java
# PUBSUB TOPIC: bqinsights
# ================================================================
# ================================================================

# MANUAL Step
# Create a pubsub topic "bqinsights" and subscriber "bqinsightssub"

# Terminal 1
mvn -q clean compile exec:java -Dexec.mainClass="ods_edw.EDWtoODS_InsightsCDCSink" -Dexec.args="{GCP-PROJECT} \
bqinsightssub mongodb+srv://{USER}:{PASSWORD}@{ATLAS-CLUSTER}/sample_mflix"

# Terminal 2
mvn compile exec:java \
    -Dexec.mainClass=ods_edw.EDWtoODS_InsightsCDCSource \
    -Dexec.args="--project={GCP-PROJECT} \
        --mongouri=mongodb+srv://{USER}:{PASSWORD}@{ATLAS-CLUSTER}/sample_mflix \
        --topic=projects/{GCP-PROJECT}/topics/bqinsights \
        --gcpTempLocation=gs://{GCS-BUCKET}/tmp/ \
        --tempLocation=gs://{GCS-BUCKET}/tmp/ \
        --runner=DataflowRunner \
        --jobName=dataflow-intro \
        --region=us-west1" \
    -Pdataflow-runner



# ================================================================
# PROOF
# ================================================================
The numloc field should be set for movies where the title matched.
Run this query in MongoDB Atlas against sample_mflix.movies

{"numLoc":{"$exists": true}}

