package ods_edw;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.HashMap;

public class EDWtoODS_InsightsCDCSink {

    public static void main(String... args) throws Exception {

        if (args.length != 3) {
            System.out.println("Usage:     " +
                    "mvn -q clean compile exec:java -Dexec.mainClass=\"dataflowdemonew.PubSubToMongo\" "
                    + "-Dexec.args=\"GCP_Project_ID Sub_id mongodbURI\"");
        }

        String projectId = args[0];
        String subscriptionId = args[1];
        MongoClientURI uri = new MongoClientURI(args[2]);

        MongoClient mongoClient = new MongoClient(uri);
        MongoDatabase database = mongoClient.getDatabase("sample_mflix");
        MongoCollection<Document> movies = database.getCollection("movies");

        subscribeWithCustomAttributesExample(projectId, subscriptionId, movies);
    }

    public static void subscribeWithCustomAttributesExample(String projectId, String subscriptionId, MongoCollection<Document> movies) {
        ProjectSubscriptionName subscriptionName =
                ProjectSubscriptionName.of(projectId, subscriptionId);

        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver =
                (PubsubMessage message, AckReplyConsumer consumer) -> {
                    // Handle incoming message, then ack the received message.
                    System.out.println("Id: " + message.getMessageId());
                    System.out.println("Data: " + message.getData().toStringUtf8());
                    // Print message attributes.

                    Gson gson = new GsonBuilder().create();
                    String update = message.getData().toStringUtf8();
                    HashMap<String, Object> parsedMap = gson.fromJson(update, HashMap.class);
                    Double numLoc = Double.parseDouble(parsedMap.get("numLoc").toString());
                    String title = parsedMap.get("title").toString();

                    movies.updateMany(
                            new Document("title", title),
                            new Document("$set", new Document("numLoc", numLoc))
                    );
                    consumer.ack();
                };

        Subscriber subscriber = null;
        try {
            subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
            // Start the subscriber.
            subscriber.startAsync().awaitRunning();
            System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());

            // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
            //subscriber.awaitRunning();
            subscriber.awaitTerminated();
        } catch (Exception e) {
            //Shut down the subscriber after 30s. Stop receiving messages.
            subscriber.stopAsync();
        }
    }


}
