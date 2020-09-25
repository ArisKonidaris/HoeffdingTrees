import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.Classifier;
import moa.classifiers.trees.HoeffdingTree;
import moa.core.TimingUtils;
import moa.streams.ArffFileStream;

/**
 * A Hoeffding Tree training by using the Massive Online Analysis (MOA) library.
 */
public class MOAHT {

    String filepath;

    public MOAHT(String filepath) {
        this.filepath = filepath;
    }

    public void run() {
        Classifier learner = new HoeffdingTree();
        ArffFileStream stream = new ArffFileStream(filepath, -1);
        stream.prepareForUse();

        learner.setModelContext(stream.getHeader());
        learner.prepareForUse();

        int numberSamplesCorrect = 0;
        int numberSamples = 0;
        int tests = 0;
        long processTime = 0; // The processing time (in milliseconds) of the streaming algorithm.

        long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
        while (stream.hasMoreInstances() && numberSamples < 1000000) {
            Instance trainInst = stream.nextInstance().getData();
            if (numberSamples >= 999000) {
                tests++;
                if (learner.correctlyClassifies(trainInst)) numberSamplesCorrect++;
            } else {
                long t1 = TimingUtils.getNanoCPUTimeOfCurrentThread();
                learner.trainOnInstance(trainInst);
                processTime += TimingUtils.getNanoCPUTimeOfCurrentThread() - t1;
            }
            numberSamples++;

            if (numberSamples % 10000 == 0) System.out.println(numberSamples);
        }
        double time = TimingUtils.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread() - evaluateStartTime);
        System.out.println(learner);
        double accuracy = 100.0 * (double) numberSamplesCorrect / (double) tests;
        System.out.println(numberSamples + " instances processed with " + accuracy + "% accuracy in " + time +
                " seconds and processing time " + TimingUtils.nanoTimeToSeconds(processTime) + " seconds." );
    }

    public static void main(String[] args) {
        String filepath = "";
        try {
            filepath = args[0];
            assert(filepath.endsWith(".arff"));
        } catch (Exception e) {
            filepath = "/home/aris/IdeaProjects/DataStream/lin_class_mil.arff";
        }
        MOAHT exp = new MOAHT(filepath);
        exp.run();
    }

}
