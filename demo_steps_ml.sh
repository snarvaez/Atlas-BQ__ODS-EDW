# ================================================
# ================================================
# SETUP
# ================================================
# ================================================

# ================================================
# Create View in Atlas
# ================================================
db.movies_ml_training.drop();
db.createView("movies_ml_training", "movies", [
  {"$unwind": "$cast"},
  {"$unwind": "$directors"},
  {"$unwind": "$genres"},
  {"$unwind": "$languages"},
  {"$unwind": "$countries"},
  {"$project": {
      "_id": 0,
      "title": 1,
      "cast": 1,
      "director": "$directors",
      "genre": "$genres",
      "language": "$languages",
      "country": "$countries",
      "rated": 1,
      "year": 1,
      "runtime": 1,
      "imdb_rating": "$imdb.rating",
      "imdb_votes": "$imdb.votes",
      "awards_wins": "$awards.wins",
      "awards_nominations": "$awards.nominations",
      "tomatoes_fresh": "$tomatoes.fresh",
      "tomatoes_rotten": "$tomatoes.rotten",
      "tomatoes_viewer_rating": "$tomatoes.viewer.rating",
      "tomatoes_viewer_numReviews": "$tomatoes.viewer.numReviews",
      "tomatoes_viewer_meter": "$tomatoes.viewer.meter",
      "tomatoes_critic_rating": "$tomatoes.critic.rating",
      "tomatoes_critic_numReviews": "$tomatoes.critic.numReviews",
      "tomatoes_critic_meter": "$tomatoes.critic.meter" }}
]);

# ================================================
# Export
# ================================================
./mongoexport --uri mongodb+srv://{USER}:{PASSWORD}@{ATLAS-CLUSTER}/sample_mflix --collection movies_ml_training --type csv --out {YOUR-CSV-FILE} \
--fields "title,cast,director,genre,language,country,rated,year,runtime,imdb_rating,imdb_votes,awards_wins,awards_nominations,tomatoes_fresh,tomatoes_rotten,tomatoes_viewer_rating,tomatoes_viewer_numReviews,tomatoes_viewer_meter,tomatoes_critic_rating,tomatoes_critic_numReviews,tomatoes_critic_meter"

# ================================================
# Import to BigQuery as new table named [movies_full]
# ================================================

# ================================================
# Create View in BigQuery [vw_ratings_by_movie_locations]
# ================================================
CREATE OR REPLACE VIEW mflix.vw_ratings_by_movie_locations
AS
SELECT m.title,
       location,
       mf.director,
       mf.cast,
       mf.imdb_votes,
       mf.rated,
       mf.year,
       mf.imdb_rating
FROM
    (SELECT movie_id,
            title,
            plot
    FROM   mflix.movies) AS m
INNER JOIN
    (SELECT b.title  title,
            locations location
    FROM    `bigquery-public-data.san_francisco_film_locations.film_locations` b
    ) AS movieLocations
ON  m.title = movieLocations.title
INNER JOIN
    `mflix.movies_full` mf
ON  m.title = mf.title

# ================================================
# In Google Tables, go to Datasets, then import the view from BQ as a new data set
# ================================================

# ================================================
# From there, train a new model and select columns as follows
# ================================================
Regression Model
Name: ratingsby_cast_director_location
Feature columns:
- location
- director
- cast

Target column:
- imdb_rating

Training takes around 1-2 hours

# ================================================
# Deploy model
# ================================================
Deployment can take 15minutes to over 1 hour

Test model in UI using Test & Use tab

Test model via API - hint: easier to do this from a GCP VM

# The Matrix
sudo curl -X POST \
-H "Authorization: Bearer "$(gcloud auth application-default print-access-token) \
-H "Content-Type: application/json; charset=utf-8" \
-d '{"payload": {"row": {"values": ["Keanu Reeves","Lana Wachowski","Financial District"]}}}' \
https://automl.googleapis.com/v1beta1/projects/{GCP-PROJECT}/locations/{YOUR-REGION}/models/{YOUR-MODEL-ID}:predict

# Mrs. Doubtfire
sudo curl -X POST \
-H "Authorization: Bearer "$(gcloud auth application-default print-access-token) \
-H "Content-Type: application/json; charset=utf-8" \
-d '{"payload": {"row": {"values": ["Sally Field","Chris Columbus","100 Embarcadero Street"]}}}' \
https://automl.googleapis.com/v1beta1/projects/{GCP-PROJECT}/locations/{YOUR-REGION}/models/{YOUR-MODEL-ID}:predict

sudo curl -X POST \
-H "Authorization: Bearer "$(gcloud auth application-default print-access-token) \
-H "Content-Type: application/json; charset=utf-8" \
-d '{"payload": {"row": {"values": ["Robin Williams","Chris Columbus","100 Embarcadero Street"]}}}' \
https://automl.googleapis.com/v1beta1/projects/{GCP-PROJECT}/locations/{YOUR-REGION}/models/{YOUR-MODEL-ID}:predict


