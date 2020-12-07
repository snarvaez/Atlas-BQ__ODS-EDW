package ods_edw;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class ODStoEDW_MoviesCDCSource {

    public static void main(String... args) throws Exception {
        String PROJECT_ID = args[0];
        String topicId = args[1];
        String mongoUri = args[2];

        MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoUri));
        MongoDatabase database = mongoClient.getDatabase("sample_mflix");
        MongoCollection<Document> collection = database.getCollection("movies");

        ProjectTopicName topicName = ProjectTopicName.of(PROJECT_ID, topicId);
        List<ApiFuture<String>> futures = new ArrayList<>();
        final Publisher publisher = Publisher.newBuilder(topicName).build();

        Block<ChangeStreamDocument<Document>> printBlock = new Block<ChangeStreamDocument<Document>>() {
            @Override
            public void apply(final ChangeStreamDocument<Document> changeStreamDocument) {
                System.out.println(changeStreamDocument.getFullDocument().toJson());


                try {
                    System.out.println(changeStreamDocument.getFullDocument());
                    ByteString data = ByteString.copyFromUtf8(changeStreamDocument.getFullDocument().toJson());
                    PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                            .setData(data)
                            .build();
                    // Schedule a message to be published. Messages are automatically batched.
                    ApiFuture<String> future = publisher.publish(pubsubMessage);
                    futures.add(future);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {

                }
            }
        };

        /**
         * Change stream listener
         */
        collection.watch().forEach(printBlock);

    }
}
