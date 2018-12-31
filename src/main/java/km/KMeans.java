package km;

import java.io.*;
import java.util.ArrayList;

import java.net.URI;
import java.util.HashSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.hadoop.mapreduce.Counters;

public class KMeans extends Configured implements Tool {
	private static final Logger logger = LogManager.getLogger(KMeans.class);

	static enum eCounter {
		SSE
	}
	
	public static class AssignToCenterMapper extends Mapper<Object, Text, IntWritable, IntWritable> {
        private final static IntWritable closestCenter = new IntWritable();
        private final static IntWritable o = new IntWritable();
        private ArrayList<Integer> centroids = new ArrayList<>(); // Array containing the k cluster centers


        @Override
        public void setup(Context context) throws IOException, InterruptedException{
            // Read centroids from distributed cache
            Configuration conf = context.getConfiguration();
            String path = conf.get("pathInput");
            URI[] cacheFiles = context.getCacheFiles();
            if (cacheFiles != null && cacheFiles.length > 0) {
                try {
                    FileSystem fs = FileSystem.get(new Path(path).toUri(), conf);
                    for (URI fileUri: cacheFiles) {
                        logger.info("*********************************"+fileUri.toString());
                        Path filePath = new Path(fileUri.toString());
                        BufferedReader rdr = new BufferedReader(new InputStreamReader(fs.open(filePath)));
                        String value;
                        // For each record in the file
                        while ((value = rdr.readLine()) != null) {
                            String[] line= value.split(",");
                            centroids.add(Integer.parseInt(line[1]));
                        }
                        rdr.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            // Assign each object in the partition to the closest center
            Configuration conf = context.getConfiguration();
            int k = Integer.parseInt(conf.get("k"));

			String[] line = value.toString().split(",");
			String uid = line[0];
			int cnt = Integer.parseInt(line[1]);
			int clstCtr = centroids.get(0);
			int minDist = Math.abs(clstCtr - cnt);

			for (int i = 1; i < k; i++) {
                int currDist = Math.abs(centroids.get(i) - cnt);
			    if (currDist < minDist) {
                    clstCtr = centroids.get(i);
                    minDist = currDist;
                }
            }
            closestCenter.set(clstCtr);
			o.set(cnt);
            context.write(closestCenter, o);

		}
		
	}
	
	public static class ComputeNewCenterReducer extends Reducer<IntWritable, IntWritable, Text, IntWritable> {
        private final Text outputKey = new Text();
	    private final IntWritable newCentroid = new IntWritable();

		@Override
		public void reduce(final IntWritable center, final Iterable<IntWritable> oList, final Context context) throws IOException, InterruptedException {
			// Compute the new centroids
		    int sum = 0;
			int n = 0;
            Counter SSE = context.getCounter(KMeans.eCounter.SSE);

			for (final IntWritable o : oList) {
				sum += Math.abs(o.get());
				SSE.increment((int)Math.pow(o.get() - center.get(), 2));
				n++;
			}
            outputKey.set("Ctrd");
            newCentroid.set(sum / n);
			context.write(outputKey, newCentroid);


		}
	}
    
	@Override
	public int run(final String[] args) throws Exception {
		final Configuration conf = this.getConf();
        conf.set("k", args[2]);
        conf.set("mapreduce.output.textoutputformat.separator", ",");

		// Delete output directory, only to ease local development; will not work on AWS. ===========
//		final FileSystem fileSystem = FileSystem.get(conf);
//		if (fileSystem.exists(new Path(args[1]))) {
//			fileSystem.delete(new Path(args[1]), true);
//		}
		// ================

        // local: Initialize k centroids automatically
//        File fileRead = new File(new Path(args[0]+"/FollowerCount.csv").toUri().toString());
//        File fileWrite = new File(new Path("init.csv").toUri().toString());
//        BufferedReader rdr = new BufferedReader(new FileReader(fileRead));
//        BufferedWriter rwr = new BufferedWriter(new FileWriter(fileWrite));
//        HashSet<Integer> ctrd = new HashSet<>();
//        String value;
//        int k = Integer.parseInt(args[2]);
//        while ((value = rdr.readLine()) != null && ctrd.size() != k) {
//            String[] line= value.split(",");
//            ctrd.add(Integer.parseInt(line[1]));
//        }
//        rdr.close();
//        for (Integer c : ctrd) {
//            rwr.write("Ctrd,"+c.toString()+"\n");
//        }
//        rwr.close();
//
        long SSE = 0;
		long lastSSE = Long.MAX_VALUE;
		int numIterations = 1;
        String pathInit = "S3://bucket-mr-kmeans/init";
		FileSystem fs = FileSystem.get(new Path(args[0]).toUri(), conf);
        FileSystem fss = FileSystem.get(new Path(pathInit).toUri(), conf);
		while (lastSSE - SSE > 0 && numIterations <= 10) {
            logger.info("Iteration: " + numIterations);

            String input, output;
            Job job = Job.getInstance(conf, "K-Means");
            Configuration jobConf = job.getConfiguration();
            if (numIterations == 1) {
                input = args[0];
                // local
//                job.addCacheFile(new Path("init_bad.csv").toUri());
                // ================

                // aws
                jobConf.set("pathInput", pathInit);
                RemoteIterator<LocatedFileStatus> fileStatusListIterator = fss.listFiles(new Path(pathInit), true);
	            while(fileStatusListIterator.hasNext()){
	                LocatedFileStatus fileStatus = fileStatusListIterator.next();
	                job.addCacheFile(fileStatus.getPath().toUri());
	            }
                // ================

            } else {
                lastSSE = SSE;
                input = args[1] + "-" + (numIterations - 1);

                // local
//                File inputFolder = new File(input);
//                File[] inputFiles = inputFolder.listFiles();
//                String filePath;
//                for (File file : inputFiles) {
//                    filePath = input+"/"+file.getName();
//                    if (filePath.contains("/part")) job.addCacheFile(new Path(filePath).toUri());
//                }
                // ================

                // aws
                jobConf.set("pathInput", input);
                RemoteIterator<LocatedFileStatus> fileStatusListIterator = fss.listFiles(new Path(input), true);
                while(fileStatusListIterator.hasNext()){
                    LocatedFileStatus fileStatus = fileStatusListIterator.next();
                    if (fileStatus.getPath().toString().contains("/part")) job.addCacheFile(fileStatus.getPath().toUri());
                }
                // ================
            }
            output = args[1] + "-" + numIterations;

            job.setJarByClass(KMeans.class);
            job.setMapperClass(AssignToCenterMapper.class);
            job.setReducerClass(ComputeNewCenterReducer.class);
            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(IntWritable.class);
            FileInputFormat.addInputPath(job, new Path(args[0]));
            FileOutputFormat.setOutputPath(job, new Path(output));

            job.waitForCompletion(true);

            Counters jobCounters = job.getCounters();
            SSE = jobCounters.findCounter(eCounter.SSE).getValue();

            if (numIterations > 1) {
            //    fs.delete(new Path(input), true);
                logger.info("SSE: " + SSE);
            }

            numIterations += 1;

        }

		return 0;
	}
	
	
	
	public static void main(final String[] args) {
		if (args.length != 3) {
			throw new Error("Three arguments required:\n<input-dir> <output-dir> <k>");
		}

		try {
			ToolRunner.run(new KMeans(), args);
		} catch (final Exception e) {
			logger.error("", e);
		}
	}

}
