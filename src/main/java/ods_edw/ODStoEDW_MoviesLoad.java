package ods_edw;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.mongodb.MongoDbIO;
import org.apache.beam.sdk.io.mongodb.MongoDbIO.Read;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.PCollection;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class ODStoEDW_MoviesLoad {

    public static void main(String[] args) throws Exception {

        Options options = PipelineOptionsFactory.fromArgs(args).withValidation().as(Options.class);
        Pipeline p = Pipeline.create(options);

        System.out.println("mongoURI *********************" + options.getMongouri());
        System.out.println("project *********************" + options.getProject());

        List<TableFieldSchema> moviesFields = new ArrayList<>();
        moviesFields.add(new TableFieldSchema().setName("movie_id").setType("STRING"));
        moviesFields.add(new TableFieldSchema().setName("title").setType("STRING"));
        moviesFields.add(new TableFieldSchema().setName("lead_actor").setType("STRING"));
        moviesFields.add(new TableFieldSchema().setName("year").setType("INT64"));
        moviesFields.add(new TableFieldSchema().setName("plot").setType("STRING"));
        TableSchema moviesSchema = new TableSchema().setFields(moviesFields);


        Read read = MongoDbIO.read().
                withUri(options.getMongouri()).
				withBucketAuto(true).
                withDatabase("sample_mflix").
				withCollection("movies");


        PCollection<Document> lines = p.apply(read);

        TableReference table1 = new TableReference();
        table1.setProjectId(options.getProject());
        table1.setDatasetId("mflix");
        table1.setTableId("movies");

        lines.apply("moviesData", MapElements.via(
                new SimpleFunction<Document, TableRow>() {
                    @Override
                    public TableRow apply(Document document) {

                        String movie_id = document.getObjectId("_id").toString();
                        String title = document.getString("title");
                        String lead_actor = null;
                        ArrayList<String> castArray = (ArrayList<String>) document.get("cast");
                        if (castArray != null && !castArray.isEmpty()) {
                            lead_actor = castArray.get(0);
                        }

                        Integer year = document.getInteger("year");
                        String plot = document.getString("plot");

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
        ))
			.apply(
					BigQueryIO.writeTableRows()
							.to(table1)
							.withSchema(moviesSchema)
							.withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
							.withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_TRUNCATE));


        p.run().waitUntilFinish();
    }


    public interface Options extends PipelineOptions, GcpOptions {

        @Description("Table to write to, specified as " + "<project_id>:<dataset_id>.<table_id>")
        @Validation.Required
        String getMongouri();

        void setMongouri(String value);
    }

}
