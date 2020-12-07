package ods_edw;

import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableRow;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


public class ODStoEDW_MoviesCDCSink {

    public static void main(String[] args) throws IOException {
        // The maximum number of shards when writing output.
        int numShards = 1;

        PubSubToGCSOptions options = PipelineOptionsFactory
                .fromArgs(args)
                .withValidation()
                .as(PubSubToGCSOptions.class);

        options.setStreaming(true);

        TableReference table1 = new TableReference();
        table1.setProjectId(options.getProject());
        table1.setDatasetId("mflix");
        table1.setTableId("movies");

        Pipeline pipeline = Pipeline.create(options);

        pipeline
                .apply("Read PubSub Messages", PubsubIO.readStrings().fromTopic(options.getInputTopic()))
                .apply("Read and transform Movies data", MapElements.via(
                        new SimpleFunction<String, TableRow>() {
                            @Override
                            public TableRow apply(String document) {
                                Gson gson = new GsonBuilder().create();
                                HashMap<String, Object> parsedMap = gson.fromJson(document, HashMap.class);
                                String movie_id = parsedMap.get("_id").toString().substring(6, 30);
                                String title = parsedMap.get("title").toString();
                                ArrayList<String> castArray = (ArrayList<String>) parsedMap.get("cast");
                                String lead_actor = castArray.get(0);
                                Double yearDouble = Double.parseDouble(parsedMap.get("year").toString());
                                Integer year = yearDouble.intValue();
                                String plot = parsedMap.get("plot").toString();

                                TableRow row =
                                        new TableRow()
                                                .set("movie_id", movie_id)
                                                .set("title", title)
                                                .set("lead_actor", lead_actor)
                                                .set("year", year)
                                                .set("plot", plot);

                                return row;

                            }
                        }
                )).apply(
                BigQueryIO.writeTableRows()
                        .to(table1)
                        .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                        .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND));

        pipeline.run().waitUntilFinish();
    }

    public interface PubSubToGCSOptions extends PipelineOptions, StreamingOptions, GcpOptions {
        @Description("The Cloud Pub/Sub topic to read from.")
        @Required
        String getInputTopic();

        void setInputTopic(String value);

        @Description("Output file's window size in number of minutes.")
        @Default.Integer(1)
        Integer getWindowSize();

        void setWindowSize(Integer value);

        @Description("Path of the output file including its filename prefix.")
        @Required
        String getOutput();

        void setOutput(String value);
    }
}


