package ods_edw;

import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.PCollection;
import org.bson.Document;

public class EDWtoODS_InsightsCDCSource {

    public static void main(String[] args) throws Exception {

        // Read options from command line.
        Options options = PipelineOptionsFactory.fromArgs(args).withValidation().as(Options.class);
        Pipeline p = Pipeline.create(options);

        System.out.println("mongoURI *********************" + options.getMongouri());
        System.out.println("project *********************" + options.getProject());
        System.out.println("Topic *********************" + options.getTopic());

        TableReference tableSpec =
                new TableReference()
                        .setDatasetId("mflix")
                        .setTableId("movieInsights");

        PCollection<TableRow> movies =
                p.apply(BigQueryIO.readTableRows().from(tableSpec));

        movies.apply("moviesData", MapElements.via(
                new SimpleFunction<TableRow, String>() {
                    @Override
                    public String apply(TableRow row) {

                        String movie_id = row.get("movie_id").toString();
                        String title = row.get("title") == null ? "" : row.get("title").toString();
                        Integer numLoc = row.get("numLoc") == null ? 0 : Integer.parseInt(row.get("numLoc").toString());
                        String plot = row.get("plot") == null ? "" : row.get("plot").toString();

                        Document doc = new Document();
                        doc.append("movie_id", movie_id)
                            .append("title", title)
                            .append("numLoc", numLoc)
                            .append("plot", plot);
                        return doc.toJson();

                    }
                }
        )).apply(
                PubsubIO.writeStrings().to(options.getTopic())
        );

        p.run().waitUntilFinish();
    }

    public interface Options extends PipelineOptions, GcpOptions {

        @Description("Table to write to, specified as " + "<project_id>:<dataset_id>.<table_id>")
        @Validation.Required
        String getMongouri();

        void setMongouri(String value);

        @Validation.Required
        String getTopic();

        void setTopic(String topic);

    }

}
